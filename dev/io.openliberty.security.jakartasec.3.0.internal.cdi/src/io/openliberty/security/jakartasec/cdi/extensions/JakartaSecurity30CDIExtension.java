/*******************************************************************************
 * Copyright (c) 2022, 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package io.openliberty.security.jakartasec.cdi.extensions;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.security.javaeesec.JavaEESecConstants;
import com.ibm.ws.security.javaeesec.cdi.extensions.HttpAuthenticationMechanismsTracker;
import com.ibm.ws.security.javaeesec.cdi.extensions.PrimarySecurityCDIExtension;

import io.openliberty.security.jakartasec.JakartaSec30Constants;
import io.openliberty.security.jakartasec.OpenIdAuthenticationMechanismDefinitionHolder;
import io.openliberty.security.jakartasec.cdi.beans.OidcHttpAuthenticationMechanism;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessBeanAttributes;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.OpenIdAuthenticationMechanismDefinition;

/**
 * CDI Extension to process the {@link OpenIdAuthenticationMechanismDefinition} annotation
 * and register beans required for Jakarta Security 3.0.
 */
@Component(service = {},
           immediate = true,
           configurationPolicy = ConfigurationPolicy.IGNORE,
           property = "service.vendor=IBM")
public class JakartaSecurity30CDIExtension implements Extension {

    private static final TraceComponent tc = Tr.register(JakartaSecurity30CDIExtension.class);

    private static PrimarySecurityCDIExtension primarySecurityCDIExtension;

    private final Set<Bean> beansToAdd = new HashSet<Bean>();
    private final String applicationName;

    // for multi-ham, if there are qualifiers, helper class to store *HAM details
    //   as they are created and added into the CDI in afterBeanDiscovery()
    // the http auth tracker cannot be used for this purpose as a) it only
    //   stores HAM details of a single type of class, and b) it would be
    //   overloading its core purpose
    private final List<HAMDefinition> hamDefinitions = new ArrayList<>();

    private static class HAMDefinition {
        Class<?> implClass;
        List<Class<?>> qualifiers;
        Properties props;

        HAMDefinition(Class<?> implClass, List<Class<?>> qualifiers, Properties props) {
            this.implClass = implClass;
            this.qualifiers = qualifiers;
            this.props = props;
        }
    }

    public JakartaSecurity30CDIExtension() {
        applicationName = HttpAuthenticationMechanismsTracker.getApplicationName();
        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "JakartaSecurity30CDIExtension", "Using application name [" + applicationName + "].");
        }
    }

    @SuppressWarnings("static-access")
    @Reference
    protected void setPrimarySecurityCDIExtension(PrimarySecurityCDIExtension primarySecurityCDIExtension) {
        this.primarySecurityCDIExtension = primarySecurityCDIExtension;
        primarySecurityCDIExtension.registerMechanismClass(OidcHttpAuthenticationMechanism.class);
    }

    @Deactivate
    protected void deactivate() {
        primarySecurityCDIExtension.deregisterMechanismClass(OidcHttpAuthenticationMechanism.class);
    }

    /**
     * Process a List annotation containing multiple Open ID definitions.
     *
     * @param listAnnotation The List annotation instance
     * @param annotatedClass The annotated class
     * @param annotatedType  The annotated type
     * @param beanManager    CDI BeanManager
     */

    private <T> void processOpenIdList(Annotation listAnnotation, Class<?> annotatedClass, AnnotatedType<T> annotatedType, BeanManager beanManager) {
        try {
            Method valueMethod = listAnnotation.annotationType().getMethod("value");
            Annotation[] oidcAnnotations = (Annotation[]) valueMethod.invoke(listAnnotation);

            for (Annotation oidcAnnotation : oidcAnnotations) {
                addOidcHttpAuthenticationMechanismBean(oidcAnnotation, annotatedClass, annotatedType);
            }
        } catch (Exception e) {
            if (tc.isDebugEnabled()) {
                Tr.error(tc, "Error processing OpenIdAuthenticationMechanismDefinition.List", e);
            }
        }
    }

    /**
     * This method processes annotations in the application which open id HAMs and
     * open identity stores - all new for Jakarta Security.
     *
     * NOTE: ApplicationScoped annotation was added because this method needs to see OpenIdHAM lists now
     * (checked explicitly in the code) and by putting ApplicationScoped in there, we get all
     * annotations. The OpenIdHAM.List can't be added explicitly as it is part of JS 4.0+ only.
     *
     * @param <T>
     * @param event
     * @param beanManager
     */
    public <T> void processAnnotatedOidc(@WithAnnotations({ OpenIdAuthenticationMechanismDefinition.class,
                                                            ApplicationScoped.class }) @Observes ProcessAnnotatedType<T> event,
                                         BeanManager beanManager) {
        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "instance: " + Integer.toHexString(this.hashCode()) + " BeanManager: " + Integer.toHexString(beanManager.hashCode()));
        }

        AnnotatedType<T> annotatedType = event.getAnnotatedType();
        Class<?> annotatedClass = annotatedType.getJavaClass();
        Set<Annotation> annotations = annotatedType.getAnnotations();

        // check for List annotation (multiple OpenID HAMs)
        boolean processedList = false;
        for (Annotation annotation : annotations) {
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Annotations found: " + annotation);
                Tr.debug(tc, "Annotation class: ", annotation.getClass());
            }

            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType.getName().contains("OpenIdAuthenticationMechanismDefinition$List")) {
                if (tc.isDebugEnabled()) {
                    Tr.debug(tc, "Processing OpenIdAuthenticationMechanismDefinition.List");
                }
                processOpenIdList(annotation, annotatedClass, annotatedType, beanManager);
                processedList = true;
                break;
            }
        }

        // not a list, process the single annotation
        if (!processedList) {
            Annotation oidcAnnotation = annotatedType.getAnnotation(OpenIdAuthenticationMechanismDefinition.class);
            if (oidcAnnotation != null) {
                addOidcHttpAuthenticationMechanismBean(oidcAnnotation, annotatedClass, annotatedType);
                addOidcIdentityStore(beanManager);
                addOpenIdContext(beanManager);
            }
        }
    }

    private <T> void addOidcHttpAuthenticationMechanismBean(Annotation annotation, Class<?> annotatedClass, AnnotatedType<T> annotatedType) {
        Properties props = new Properties();
        props.put(JakartaSec30Constants.OIDC_ANNOTATION, new OpenIdAuthenticationMechanismDefinitionHolder((OpenIdAuthenticationMechanismDefinition) annotation));
        Set<Annotation> annotations = annotatedType.getAnnotations();

        List<Class<?>> qualifiers = getQualifierClassesFromAnnotation(annotation, annotation.annotationType());

        // only add Oidc hamType to hamDefinitions if it contains
        //   custom specified qualifiers, not default ones or none
        if (hasCustomQualifiers(qualifiers)) {
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Processing HAM type [OpenIdAuthenticationMechanism]"
                             + "] with qualifiers [" + qualifiers.toString()
                             + "] and props [" + props.toString() + "].");
            }
            // will use this in afterBeanDiscovery() to add the multiple HAMS,
            //   qualifiers mean they must be different instances
            hamDefinitions.add(new HAMDefinition(OidcHttpAuthenticationMechanism.class, qualifiers, props));
        } else {
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Processing HAM type [OpenIdAuthenticationMechanism]"
                             + "] with qualifiers [" + ((qualifiers == null) ? "none" : qualifiers.toString() + "]"));
            }
            primarySecurityCDIExtension.addAuthMech(applicationName, annotatedClass, OidcHttpAuthenticationMechanism.class, annotations, props);
        }
    }

    private void addOidcIdentityStore(BeanManager beanManager) {
        //TODO: look for better way to check for duplicates
        for (Bean b : beansToAdd) {
            if (OidcIdentityStoreBean.class.equals(b.getClass())) {
                if (tc.isDebugEnabled())
                    Tr.debug(tc, "OidcIdentityStoreBean already registered.");
                return;
            }
        }
        if (tc.isDebugEnabled())
            Tr.debug(tc, "adding OidcIdentityStoreBean.");
        beansToAdd.add(new OidcIdentityStoreBean(beanManager));
    }

    private void addOpenIdContext(BeanManager beanManager) {
        //TODO: look for better way to check for duplicates
        for (Bean b : beansToAdd) {
            if (OpenIdContextBean.class.equals(b.getClass())) {
                if (tc.isDebugEnabled())
                    Tr.debug(tc, "OpenIdContextBean already registered.");
                return;
            }
        }
        if (tc.isDebugEnabled())
            Tr.debug(tc, "adding OpenIdContextBean.");
        beansToAdd.add(new OpenIdContextBean(beanManager));
    }

    /**
     * Invoked when OpenIdHAM discovered in application.
     *
     * @param processBeanAttributes
     * @param beanManager           CDI BeanManager
     */

    public void processOidcHttpAuthMechNeeded(@Observes ProcessBeanAttributes<OidcHttpAuthenticationMechanism> processBeanAttributes,
                                              BeanManager beanManager) {
        // don't veto if in tracker OR in hamDefinitions
        boolean inTracker = primarySecurityCDIExtension.existAuthMech(applicationName, OidcHttpAuthenticationMechanism.class);
        boolean inHamDefinitions = !hamDefinitions.isEmpty();

        if (!inTracker && !inHamDefinitions) {
            processBeanAttributes.veto();
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "OidcHttpAuthenticationMechanism is disabled since it is not configured within the application.");
            }
        }
    }

    public <T> void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery, BeanManager beanManager) {

        boolean hasCDIDiscoveredHAMs = !primarySecurityCDIExtension.isEmptyModuleMap(applicationName);

        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "afterBeanDiscovery : instance : " + Integer.toHexString(this.hashCode()));
            Tr.debug(tc, "Has CDI-discovered OIDC HAM in tracker: " + hasCDIDiscoveredHAMs);
            Tr.debug(tc, "Number of custom qualified OIDC HAM definitions: " + hamDefinitions.size());
        }

        if (!hasCDIDiscoveredHAMs && !hamDefinitions.isEmpty()) {
            HAMDefinition firstHAM = hamDefinitions.get(0);
            primarySecurityCDIExtension.addAuthMech(applicationName, firstHAM.implClass, firstHAM.implClass, Collections.emptySet(), null);
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Empty tracker so added HAM for Jakarta Security activation: "
                             + firstHAM.implClass.getName());
            }
        }

        // create a separate bean for each HAM definition with qualifiers
        for (int i = 0; i < hamDefinitions.size(); i++) {
            HAMDefinition hamDef = hamDefinitions.get(i);
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Adding OpenID hamDefinition for class ["
                             + hamDef.implClass.getCanonicalName() + "] with qualifiers ["
                             + hamDef.qualifiers.toString() + "].");
            }

            // add the specified qualifiers
            List<Annotation> qualifierAnnotations = new ArrayList<>();
            qualifierAnnotations.add(new AnnotationLiteral<Any>() {
                private static final long serialVersionUID = 1L;
            });
            for (Class<?> qClass : hamDef.qualifiers) {
                Annotation qualifier = createQualifierAnnotation(qClass);
                if (qualifier != null) {
                    qualifierAnnotations.add(qualifier);
                }
            }

            final Properties instanceProps = hamDef.props;

            // register the qualified bean
            // @formatter:off
            afterBeanDiscovery.addBean().beanClass
                (hamDef.implClass).types(
                     hamDef.implClass,
                     HttpAuthenticationMechanism.class,
                     Object.class).scope(ApplicationScoped.class).
                     qualifiers(qualifierAnnotations.toArray(new Annotation[0])).
                         createWith(ctx -> {
                             try {
                                 OidcHttpAuthenticationMechanism instance = (OidcHttpAuthenticationMechanism)
                                     hamDef.implClass.getDeclaredConstructor().newInstance();
                                 instance.setQualifiedProperties(instanceProps);
                                 return instance;
                             } catch (Exception e) {
                                 Tr.error(tc, "ERROR creating OpenID HAM instance when adding it as a separate bean during after bean discovery.", e);
                                 throw new RuntimeException("Failed to create OpenID HAM instance", e);
                             }
                         }
                 );
             // @formatter:on
        }

        // Verification of mechanisms and registration of ModulePropertiesProviderBean performed in JavaEESecCDIExtension's afterBeanDiscovery()
        for (Bean<?> bean : beansToAdd) {
            afterBeanDiscovery.addBean(bean);
        }
    }

    /**
     * For the OpenIdHAMDefinition annotation, extract the qualifiers value.
     *
     * @param annotation     is the specific annotation
     * @param annotationType is the specific annotation type
     * @return a list of qualifier classes, or an empty list if no qualifiers.
     */
    private List<Class<?>> getQualifierClassesFromAnnotation(Annotation annotation, Class<? extends Annotation> annotationType) {
        Method qualifiersMethod;
        Class<?>[] qualifiers = null;
        try {
            qualifiersMethod = annotationType.getMethod(JavaEESecConstants.QUALIFIERS);
            qualifiers = (Class<?>[]) qualifiersMethod.invoke(annotation);
        } catch (Exception e) {
            // no "qualifiers" attribute - pre Jakarta Security 4.0
            return Collections.emptyList();
        }

        if ((qualifiers != null) && (qualifiers.length > 0)) {
            return new ArrayList<>(Arrays.asList(qualifiers));
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Create a qualifier annotation instance from a qualifier class
     *
     * @param qualifierClass The qualifier class
     * @return Annotation instance, or null if creation fails
     */
    private Annotation createQualifierAnnotation(Class<?> qualifierClass) {
        String qualifierName = qualifierClass.getName();

        // handle default qualifiers (nested interfaces) using reflection
        if (isDefaultQualifier(qualifierClass)) {
            try {
                // Get the Literal.INSTANCE field using reflection
                Class<?> literalClass = Class.forName(qualifierName + "$Literal");
                Field instanceField = literalClass.getField("INSTANCE");
                return (Annotation) instanceField.get(null);
            } catch (Exception e) {
                if (tc.isDebugEnabled()) {
                    Tr.debug(tc, "Could not create default qualifier annotation for: " + qualifierName, e);
                }
                return null;
            }
        }

        // for custom application-defined qualifiers
        return new AnnotationLiteral() {
            private static final long serialVersionUID = 1L;

            @Override
            public Class<? extends Annotation> annotationType() {
                return (Class<? extends Annotation>) qualifierClass;
            }
        };
    }

    /**
     * Is a qualifier class the default qualifier for HAMs (JS 4.0+).
     *
     * @param qualifierClass The class to check.
     * @return true if class is the default qualifier, false else.
     */
    private boolean isDefaultQualifier(Class<?> qualifierClass) {
        String qualifierName = qualifierClass.getName();
        return qualifierName.contains("$OpenIdAuthenticationMechanism");
    }

    /**
     * Does a list of qualifier classes have a custom (non JS 4.0+ default)
     * qualifier (i.e. "Admin" or "User").
     *
     * @param qualifiers The list of classes to check if any of them contain a
     *                       custom one.
     * @return true if any qualifier is custom, false else (or if list is empty
     *         or if all are the JS 4.0+ default).
     */
    private boolean hasCustomQualifiers(List<Class<?>> qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty()) {
            return false;
        }

        // Check if any qualifier is NOT a default qualifier
        return qualifiers.stream().anyMatch(q -> !isDefaultQualifier(q));
    }

}
