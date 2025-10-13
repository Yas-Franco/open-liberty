/*******************************************************************************
 * Copyright (c) 2017, 2026 IBM Corporation and others.
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
package com.ibm.ws.security.javaeesec.cdi.extensions;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessBeanAttributes;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.util.AnnotationLiteral;
import javax.security.enterprise.authentication.mechanism.http.BasicAuthenticationMechanismDefinition;
import javax.security.enterprise.authentication.mechanism.http.CustomFormAuthenticationMechanismDefinition;
import javax.security.enterprise.authentication.mechanism.http.FormAuthenticationMechanismDefinition;
import javax.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import javax.security.enterprise.authentication.mechanism.http.LoginToContinue;
import javax.security.enterprise.identitystore.DatabaseIdentityStoreDefinition;
import javax.security.enterprise.identitystore.IdentityStore;
import javax.security.enterprise.identitystore.IdentityStore.ValidationType;
import javax.security.enterprise.identitystore.IdentityStoreHandler;
import javax.security.enterprise.identitystore.LdapIdentityStoreDefinition;
import javax.security.enterprise.identitystore.PasswordHash;
import javax.security.enterprise.identitystore.Pbkdf2PasswordHash;

import org.osgi.service.component.annotations.Component;

import com.ibm.websphere.csi.J2EEName;
import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.websphere.ras.annotation.Sensitive;
import com.ibm.ws.cdi.extension.WebSphereCDIExtension;
import com.ibm.ws.ffdc.annotation.FFDCIgnore;
import com.ibm.ws.runtime.metadata.ModuleMetaData;
import com.ibm.ws.security.javaeesec.ApplicationUtils;
import com.ibm.ws.security.javaeesec.JavaEESecConstants;
import com.ibm.ws.security.javaeesec.cdi.beans.BasicHttpAuthenticationMechanism;
import com.ibm.ws.security.javaeesec.cdi.beans.CustomFormAuthenticationMechanism;
import com.ibm.ws.security.javaeesec.cdi.beans.FormAuthenticationMechanism;
import com.ibm.ws.security.javaeesec.properties.ModuleProperties;
import com.ibm.ws.threadContext.ModuleMetaDataAccessorImpl;
import com.ibm.ws.webcontainer.security.WebAppSecurityConfig;
import com.ibm.ws.webcontainer.security.metadata.LoginConfiguration;
import com.ibm.ws.webcontainer.security.metadata.SecurityMetadata;
import com.ibm.ws.webcontainer.security.util.WebConfigUtils;
import com.ibm.wsspi.webcontainer.metadata.WebModuleMetaData;

import io.openliberty.security.jakartasec.services.JakartaSecurityValidationService;

/**
 * TODO: Add all JSR-375 API classes that can be bean types to api.classes.
 *
 * @param <T>
 */
@Component(service = { WebSphereCDIExtension.class, PrimarySecurityCDIExtension.class },
           property = { "api.classes=javax.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;javax.security.enterprise.identitystore.IdentityStore;javax.security.enterprise.identitystore.IdentityStoreHandler;javax.security.enterprise.identitystore.RememberMeIdentityStore;javax.security.enterprise.SecurityContext;com.ibm.ws.security.javaeesec.properties.ModulePropertiesProvider",
                        "bean.defining.annotations=javax.security.enterprise.authentication.mechanism.http.BasicAuthenticationMechanismDefinition;javax.security.enterprise.authentication.mechanism.http.CustomFormAuthenticationMechanismDefinition;javax.security.enterprise.authentication.mechanism.http.FormAuthenticationMechanismDefinition;javax.security.enterprise.authentication.mechanism.http.LoginToContinue;javax.security.enterprise.identitystore.DatabaseIdentityStoreDefinition;javax.security.enterprise.identitystore.LdapIdentityStoreDefinition" },
           immediate = true)
public class JavaEESecCDIExtension<T> implements Extension, WebSphereCDIExtension, PrimarySecurityCDIExtension {

    private static final TraceComponent tc = Tr.register(JavaEESecCDIExtension.class);

    // TODO: Track beans by annotated type
    private final Set<Bean> beansToAdd = new HashSet<Bean>();
    private boolean identityStoreHandlerRegistered = false;
    private boolean identityStoreRegistered = false;
    private final boolean isAlternativeHAMAdded = false;
    private final String applicationName;
    private static final String DECORATOR = "Decorator";
    private static final String ALTERNATIVE = "Alternative";
    private final List<LdapIdentityStoreDefinition> ldapDefinitionList = new ArrayList<LdapIdentityStoreDefinition>();
    private final List<DatabaseIdentityStoreDefinition> databaseDefinitionList = new ArrayList<DatabaseIdentityStoreDefinition>();

    private static Set<Class<?>> mechanismClasses;
    private static HttpAuthenticationMechanismsTracker httpAuthenticationMechanismsTracker = new HttpAuthenticationMechanismsTracker();

    static {
        mechanismClasses = new HashSet<Class<?>>();
        mechanismClasses.add(BasicHttpAuthenticationMechanism.class);
        mechanismClasses.add(FormAuthenticationMechanism.class);
        mechanismClasses.add(CustomFormAuthenticationMechanism.class);
        mechanismClasses.add(HttpAuthenticationMechanism.class);
    }

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

        HAMDefinition(Class<?> implClass, List<Class<?>> qualifiers, Properties props,
                      Class<?> annotatedClass, Set<Annotation> annotations) {
            this.implClass = implClass;
            this.qualifiers = qualifiers;
            this.props = props;
        }
    }

    public JavaEESecCDIExtension() {
        applicationName = getApplicationName();
        httpAuthenticationMechanismsTracker.initialize(applicationName);
    }

    // For unit testing
    protected static void setHttpAuthenticationMechanismsTracker(HttpAuthenticationMechanismsTracker anHttpAuthenticationMechanismsTracker) {
        httpAuthenticationMechanismsTracker = anHttpAuthenticationMechanismsTracker;
    }

    /**
     * Get the shared HttpAuthenticationMechanismsTracker instance.
     * Used by other extensions (e.g., JakartaSecurity30CDIExtension) to coordinate default HAM selection.
     *
     * @return the shared tracker instance
     */
    public static HttpAuthenticationMechanismsTracker getHttpAuthenticationMechanismsTracker() {
        return httpAuthenticationMechanismsTracker;
    }

    @Override
    public void registerMechanismClass(Class<?> mechanismClass) {
        mechanismClasses.add(mechanismClass);
    }

    @Override
    public void deregisterMechanismClass(Class<?> mechanismClass) {
        mechanismClasses.remove(mechanismClass);
    }

    /**
     * Generic method to process all HAM definitions, with or without custom qualifiers (JS 4.0+).
     *
     * @param hamType              The HAM implementation class (BasicHttpAuthenticationMechanism, FormAuthenticationMechanism, or CustomFormAuthenticationMechanism)
     * @param annotation           The annotation instance
     * @param annotationType       The annotation type
     * @param isAuthMechOverridden Whether auth mechanism is overridden by global login
     * @param beanManager          CDI BeanManager
     * @param javaClass            The annotated class
     * @param annotations          Class-level annotations
     * @param annotatedType        The annotated type
     */
    private <U> void processHAMDefinition(Class<?> hamType, Annotation annotation, Class<? extends Annotation> annotationType,
                                          boolean isAuthMechOverridden, BeanManager beanManager, Class<?> javaClass,
                                          Set<Annotation> annotations, AnnotatedType<U> annotatedType) {

        List<Class<?>> qualifiers = getQualifierClassesFromAnnotation(annotation, annotationType);

        // only add hamType (i.e. FormAuthenticationMechanism) to hamDefinitions
        //   if it contains custom specified qualifiers, not default ones or none
        if (hasCustomQualifiers(qualifiers)) {
            Properties props = extractHAMProperties(hamType, annotation, annotationType);

            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Processing HAM type [" + hamType.getSimpleName()
                             + "] with custom qualifiers [" + qualifiers.toString()
                             + "] and props [" + props.toString() + "].");
            }
            // will use this in afterBeanDiscovery() to add the multiple HAMs,
            //   even for the same type, qualifiers mean they must be different instances
            hamDefinitions.add(new HAMDefinition(hamType, qualifiers, props, javaClass, annotations));
        } else {
            // has JS 4.0+ default qualifiers or no qualifiers at all, it will
            //   be handled (created, configured) naturally by the CDI and existing process
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Processing HAM type [" + hamType.getSimpleName()
                             + "] with qualifiers [" + ((qualifiers == null) ? "none" : qualifiers.toString() + "]"));
            }

            if (isAuthMechOverridden) {
                createModulePropertiesProviderBeanForGlobalLogin(beanManager, javaClass, annotations, qualifiers);
            } else {
                // BasicHAM
                if (hamType.equals(BasicHttpAuthenticationMechanism.class)) {
                    createModulePropertiesProviderBeanForBasicToAdd(annotation, annotationType, javaClass, annotations);
                } else {
                    // [Custom]FormHAM
                    createModulePropertiesProviderBeanForFormToAdd(annotation, annotationType, javaClass, annotations);
                }
            }
        }
    }

    /**
     * Process a List annotation containing multiple HAM definitions
     *
     * @param hamType              The HAM implementation class
     * @param hamTypeName          The HAM type name (e.g., "Basic", "Form", "CustomForm")
     * @param singleAnnotationType The single annotation type (e.g., BasicAuthenticationMechanismDefinition.class)
     * @param annotation           The List annotation instance
     * @param annotationType       The List annotation type
     * @param isAuthMechOverridden Whether auth mechanism is overridden
     * @param beanManager          CDI BeanManager
     * @param javaClass            The annotated class
     * @param annotations          Class-level annotations
     * @param annotatedType        The annotated type
     */
    private <U> void processHAMDefinitionList(Class<?> hamType, String hamTypeName,
                                              Class<? extends Annotation> singleAnnotationType,
                                              Annotation annotation, Class<? extends Annotation> annotationType,
                                              boolean isAuthMechOverridden, BeanManager beanManager,
                                              Class<?> javaClass, Set<Annotation> annotations,
                                              AnnotatedType<U> annotatedType) {
        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "Processing " + hamTypeName + "HAMList.");
        }

        try {
            // get the array of HAM annotations from the List annotation
            Method valueMethod = annotationType.getMethod("value");
            Annotation[] hamAnnotations = (Annotation[]) valueMethod.invoke(annotation);

            // process each HAM in the list
            for (Annotation hamAnnotation : hamAnnotations) {
                processHAMDefinition(hamType, hamAnnotation, singleAnnotationType,
                                     isAuthMechOverridden, beanManager, javaClass, annotations, annotatedType);
            }
        } catch (Exception e) {
            if (tc.isDebugEnabled()) {
                Tr.error(tc, "Error processing " + hamTypeName + "AuthenticationMechanismDefinition.List.", e);
            }
        }
    }

    /**
     * Extract properties from HAM annotation based on HAM type
     *
     * @param hamType        The HAM implementation class
     * @param annotation     The annotation instance
     * @param annotationType The annotation type
     * @return Properties extracted from the annotation
     */
    private Properties extractHAMProperties(Class<?> hamType, Annotation annotation, Class<? extends Annotation> annotationType) {
        if (hamType.equals(BasicHttpAuthenticationMechanism.class)) {
            return extractBasicHAMProperties(annotation, annotationType);
        } else if (hamType.equals(FormAuthenticationMechanism.class) || hamType.equals(CustomFormAuthenticationMechanism.class)) {
            return extractFormHAMProperties(annotation, annotationType);
        }
        return new Properties();
    }

    /**
     * Get the properties from BasicAuthenticationMechanismDefinition
     */
    private Properties extractBasicHAMProperties(Annotation annotation, Class<? extends Annotation> annotationType) {
        Properties props = new Properties();
        try {
            Method realmNameMethod = annotationType.getMethod("realmName");
            String realmName = (String) realmNameMethod.invoke(annotation);
            props.put(JavaEESecConstants.REALM_NAME, realmName);
        } catch (Exception e) {
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Error extracting realmName from BasicAuthenticationMechanismDefinition", e);
            }
        }
        return props;
    }

    /**
     * Get the properties from [Custom|CustomForm]AuthenticationMechanismDefinition
     */
    private Properties extractFormHAMProperties(Annotation annotation, Class<? extends Annotation> annotationType) {
        Properties props = new Properties();
        try {
            Method loginToContinueMethod = annotationType.getMethod("loginToContinue");
            Annotation ltcAnnotation = (Annotation) loginToContinueMethod.invoke(annotation);
            props = parseLoginToContinue(ltcAnnotation);
        } catch (Exception e) {
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Error extracting loginToContinue from " + annotationType.getSimpleName(), e);
            }
        }
        return props;
    }

    /**
     * This method process custom HAM classes in the application.
     *
     * @param processAnnotatedType The annotated type, i.e. the custom HAM class name.
     * @param beanManager          CDI BeanManager
     */
    public void processApplicationHAMClass(@Observes ProcessAnnotatedType<? extends HttpAuthenticationMechanism> processAnnotatedType, BeanManager beanManager) {
        processAnnotatedType(processAnnotatedType, beanManager);
    }

    /**
     * This method processes annotations in the application which specify HAMs and identity stores -
     * all new for Jakarta Security.
     *
     * Note: we can't explicitly "listen" for List annotations as they are nested
     * interfaces that only exist in Jakarta Security 4.0+.
     *
     * @param <T>
     * @param processAnnotatedType The annotated type, see @WithAnnotations
     * @param beanManager          CDI BeanManager
     */
    public <T> void processAnnotatedHAMandIS(@Observes @WithAnnotations({ BasicAuthenticationMechanismDefinition.class,
                                                                          FormAuthenticationMechanismDefinition.class,
                                                                          CustomFormAuthenticationMechanismDefinition.class, LdapIdentityStoreDefinition.class,
                                                                          DatabaseIdentityStoreDefinition.class,
                                                                          LoginToContinue.class, ApplicationScoped.class }) ProcessAnnotatedType<T> processAnnotatedType,
                                             BeanManager beanManager) {
        processAnnotatedType(processAnnotatedType, beanManager);
    }

    public <T> void processAnnotatedType(ProcessAnnotatedType<T> processAnnotatedType, BeanManager beanManager) {
        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "instance: " + Integer.toHexString(this.hashCode()) + " BeanManager: " + Integer.toHexString(beanManager.hashCode()));
        }

        AnnotatedType<T> annotatedType = processAnnotatedType.getAnnotatedType();
        Class<?> javaClass = annotatedType.getJavaClass();
        Set<Annotation> annotations = annotatedType.getAnnotations();

        boolean isAuthMechOverridden = isAuthMechOverridden();
        boolean isApplicationAuthMech = isApplicationAuthMech(javaClass);
        if (isApplicationAuthMech) {
            if (isAuthMechOverridden) {
                if (tc.isDebugEnabled()) {
                    Tr.debug(tc, "Found an application specific HttpAuthenticationMechanism: "
                                 + javaClass
                                 + ", now creating module properties for global login.");
                }
                createModulePropertiesProviderBeanForGlobalLogin(beanManager, javaClass, annotations, null);
            } else {
                Annotation ltc = annotatedType.getAnnotation(LoginToContinue.class);
                if (tc.isDebugEnabled()) {
                    Tr.debug(tc, "Found an application specific HttpAuthenticationMechanism: "
                                 + javaClass
                                 + ", now creating module properties application auth mech.");
                }
                createModulePropertiesProviderBeanForApplicationAuthMechToAdd(beanManager, ltc, javaClass, annotations);
            }
        }

        // look at the class level annotations
        for (Annotation annotation : annotations) {
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Annotations found: " + annotation);
                Tr.debug(tc, "Annotation class: ", annotation.getClass());
            }

            Class<? extends Annotation> annotationType = annotation.annotationType();

            // based on the annotation type which will correspond to @WithAnnotations
            //   values, process the HAM.  Multiple HAMs of the same type are Lists!
            if (BasicAuthenticationMechanismDefinition.class.equals(annotationType)) {
                processHAMDefinition(BasicHttpAuthenticationMechanism.class, annotation, annotationType,
                                     isAuthMechOverridden, beanManager, javaClass, annotations, annotatedType);
            } else if (FormAuthenticationMechanismDefinition.class.equals(annotationType)) {
                processHAMDefinition(FormAuthenticationMechanism.class, annotation, annotationType,
                                     isAuthMechOverridden, beanManager, javaClass, annotations, annotatedType);
            } else if (CustomFormAuthenticationMechanismDefinition.class.equals(annotationType)) {
                processHAMDefinition(CustomFormAuthenticationMechanism.class, annotation, annotationType,
                                     isAuthMechOverridden, beanManager, javaClass, annotations, annotatedType);
            } else if (isAuthenticationMechanismDefinitionList(annotationType, "Basic")) {
                processHAMDefinitionList(BasicHttpAuthenticationMechanism.class, "Basic",
                                         BasicAuthenticationMechanismDefinition.class, annotation, annotationType,
                                         isAuthMechOverridden, beanManager, javaClass, annotations, annotatedType);
            } else if (isAuthenticationMechanismDefinitionList(annotationType, "CustomForm")) {
                // NOTE: always check CustomForm BEFORE Form since "CustomForm" contains "Form"
                processHAMDefinitionList(CustomFormAuthenticationMechanism.class, "CustomForm",
                                         CustomFormAuthenticationMechanismDefinition.class, annotation, annotationType,
                                         isAuthMechOverridden, beanManager, javaClass, annotations, annotatedType);
            } else if (isAuthenticationMechanismDefinitionList(annotationType, "Form")) {
                processHAMDefinitionList(FormAuthenticationMechanism.class, "Form",
                                         FormAuthenticationMechanismDefinition.class, annotation, annotationType,
                                         isAuthMechOverridden, beanManager, javaClass, annotations, annotatedType);
            } else if (LdapIdentityStoreDefinition.class.equals(annotationType)) {
                createLdapIdentityStoreBeanToAdd(beanManager, annotation, annotationType);
                identityStoreRegistered = true;
            } else if (DatabaseIdentityStoreDefinition.class.equals(annotationType)) {
                createDatabaseIdentityStoreBeanToAdd(beanManager, annotation, annotationType);
                identityStoreRegistered = true;
            }
        }
    }

    /***
     * Pre processing of beans before any beans have been discovered.
     *
     * @param beforeBeanDiscovery event type of the container fired before bean discovery begins.
     * @param beanManager         CDI BeanManager
     */
    public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery beforeBeanDiscovery, BeanManager beanManager) {
        AnnotatedType<SecurityContextProducer> securityContextProducerType = beanManager.createAnnotatedType(SecurityContextProducer.class);
        beforeBeanDiscovery.addAnnotatedType(securityContextProducerType, SecurityContextProducer.class.getName() + ":" + getClass().getClassLoader().hashCode());

        AnnotatedType<AutoApplySessionInterceptor> autoApplySessionInterceptorType = beanManager.createAnnotatedType(AutoApplySessionInterceptor.class);
        beforeBeanDiscovery.addAnnotatedType(autoApplySessionInterceptorType, AutoApplySessionInterceptor.class.getName() + ":" + getClass().getClassLoader().hashCode());

        AnnotatedType<RememberMeInterceptor> rememberMeInterceptorInterceptorType = beanManager.createAnnotatedType(RememberMeInterceptor.class);
        beforeBeanDiscovery.addAnnotatedType(rememberMeInterceptorInterceptorType, RememberMeInterceptor.class.getName() + ":" + getClass().getClassLoader().hashCode());
    }

    /***
     * Post processing of beans after all the beans have been discovered.
     *
     * @param <T>
     * @param afterBeanDiscovery event type of the container fired after bean discovery ends.
     * @param beanManager        CDI BeanManager
     */
    public <T> void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery, BeanManager beanManager) {

        if (tc.isDebugEnabled())
            Tr.debug(tc, "instance: " + Integer.toHexString(this.hashCode()) + " BeanManager: " + Integer.toHexString(beanManager.hashCode()));
        try {
            verifyConfiguration();
            if (!identityStoreHandlerRegistered) {
                beansToAdd.add(new IdentityStoreHandlerBean(beanManager));
                if (tc.isDebugEnabled())
                    Tr.debug(tc, "registering the default IdentityStoreHandler.");
            } else {
                if (tc.isDebugEnabled())
                    Tr.debug(tc, "IdentityStoreHandler is not registered because a custom IdentityStoreHandler has been registered,");
            }
        } catch (DeploymentException de) {
            afterBeanDiscovery.addDefinitionError(de);
        }

        boolean hasCDIDiscoveredHAMs = !httpAuthenticationMechanismsTracker.isEmptyModuleMap(applicationName);
        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "Has CDI-discovered HAMs in tracker: " + hasCDIDiscoveredHAMs);
            Tr.debug(tc, "Number of custom qualified HAM definitions: " + hamDefinitions.size());
        }

        if (!hasCDIDiscoveredHAMs && !hamDefinitions.isEmpty()) {
            // no CDI-discovered HAMs so we must have only custom qualified HAMs,
            //   add the first one to tracker this signals Jakarta Security is active

            HAMDefinition firstHAM = hamDefinitions.get(0);
            addAuthMech(applicationName, firstHAM.implClass, firstHAM.implClass, Collections.emptySet(), null);
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Empty tracker so added HAM for Jakarta Security activation: "
                             + firstHAM.implClass.getName());
            }
        }

        // create a separate bean for each HAM definition with qualifiers
        for (int i = 0; i < hamDefinitions.size(); i++) {
            HAMDefinition hamDef = hamDefinitions.get(i);
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Adding qualified hamDefinition for class ["
                             + hamDef.implClass.getCanonicalName() + "] with qualifiers ["
                             + hamDef.qualifiers.toString() + "].");
            }

            // add the specified qualifiers - always add @Any
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

            // Create QualifiedHAMBean instead of using createWith()
            Set<Annotation> qualifierSet = new HashSet<>(qualifierAnnotations);
            QualifiedHAMBean qualifiedHAMBean = new QualifiedHAMBean(beanManager, hamDef.implClass, qualifierSet, instanceProps);
            beansToAdd.add(qualifiedHAMBean);

            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "Added QualifiedHAMBean for " + hamDef.implClass.getName() +
                             " with qualifiers: " + qualifierSet);
            }
        }

        if (!httpAuthenticationMechanismsTracker.isEmptyModuleMap(applicationName)) {
            // this is a JSR375 app.
            ModulePropertiesProviderBean bean = new ModulePropertiesProviderBean(beanManager, httpAuthenticationMechanismsTracker.getModuleMap(applicationName));
            beansToAdd.add(bean);
            // register the application name for recycle the apps.
            ApplicationUtils.registerApplication(getApplicationName());
        }

        // TODO: Validate beans to add.
        for (Bean<?> bean : beansToAdd) {
            afterBeanDiscovery.addBean(bean);
        }

        if (tc.isDebugEnabled()) {
            printBeans(beanManager, "After addBean()");
        }
    }

    /**
     * For the *HamDefinition annotations, extract the qualifiers value.
     *
     * @param annotation     is the specific annotation
     * @param annotationType is the specific annotation type
     * @return a list of qualifier classes, or an empty list if no qualifiers.
     */

    @FFDCIgnore(value = { Exception.class })
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

        if ((qualifiers != null) & (qualifiers.length > 0)) {
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
                // get the Literal.INSTANCE field using reflection
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

    void printBeans(BeanManager beanManager, String stage) {
        Set<Bean<?>> beans = beanManager.getBeans(Object.class, new AnnotationLiteral<Any>() {
            private static final long serialVersionUID = 1L;
        });
        for (Bean<?> bean : beans) {
            Tr.debug(tc, stage + " bean name: " + bean.getBeanClass().getName());
        }
    }

    public void processBean(@Observes ProcessBean<?> processBean, BeanManager beanManager) {
        if (tc.isDebugEnabled())
            Tr.debug(tc, "instance: " + Integer.toHexString(this.hashCode()) + " BeanManager: " + Integer.toHexString(beanManager.hashCode()));
        if (!identityStoreHandlerRegistered) {
            if (isIdentityStoreHandler(processBean)) {
                identityStoreHandlerRegistered = true;
            }
        }
        if (!identityStoreRegistered) {
            if (isIdentityStore(processBean)) {
                identityStoreRegistered = true;
            }
        }
    }

    /**
     * Common method to process HAM bean attributes and veto if qualified HAMs exist.
     *
     * This is called *after* all the HAMs have been processed, so the internal tracker
     * and ham definitions lists are complete.
     *
     * @param <T>                   The HAM implementation type
     * @param processBeanAttributes The ProcessBeanAttributes event
     * @param hamClass              The HAM class to check for
     * @param hamTypeName           The HAM type name for debug messages
     *                                  (e.g., "Basic", "Form", "CustomForm")
     */
    private <T extends HttpAuthenticationMechanism> void processHttpAuthMechNeeded(ProcessBeanAttributes<T> processBeanAttributes,
                                                                                   Class<T> hamClass,
                                                                                   String hamTypeName) {
        // don't veto if in tracker OR in hamDefinitions
        boolean inTracker = existAuthMech(applicationName, hamClass);
        boolean inHamDefinitions = hamDefinitions.stream().anyMatch(def -> def.implClass.equals(hamClass));

        if (!inTracker && !inHamDefinitions) {
            processBeanAttributes.veto();
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, hamTypeName + "HttpAuthenticationMechanism is disabled since it is not configured within the application.");
            }
        }
    }

    /**
     * Invoked when BasicHAM discovered in application.
     *
     * @param processBeanAttributes
     * @param beanManager           CDI BeanManager
     */
    public void processBasicHttpAuthMechNeeded(@Observes ProcessBeanAttributes<BasicHttpAuthenticationMechanism> processBeanAttributes,
                                               BeanManager beanManager) {
        processHttpAuthMechNeeded(processBeanAttributes, BasicHttpAuthenticationMechanism.class, "Basic");
    }

    /**
     * Invoked when FormHAM discovered in application.
     *
     * @param processBeanAttributes
     * @param beanManager
     */
    public void processFormAuthMechNeeded(@Observes ProcessBeanAttributes<FormAuthenticationMechanism> processBeanAttributes,
                                          BeanManager beanManager) {
        processHttpAuthMechNeeded(processBeanAttributes, FormAuthenticationMechanism.class, "Form");
    }

    /**
     * Invoked when CustomFormHAM discovered in application.
     *
     * @param processBeanAttributes
     * @param beanManager
     */
    public void processCustomFormAuthMechNeeded(@Observes ProcessBeanAttributes<CustomFormAuthenticationMechanism> processBeanAttributes,
                                                BeanManager beanManager) {
        processHttpAuthMechNeeded(processBeanAttributes, CustomFormAuthenticationMechanism.class, "CustomForm");
    }

    /**
     * Update the HTTP authentication mechanism tracker which provides configuration for the HAMs,
     * with the properties for the [Custom]FormHAM.
     *
     * @param annotation     The annotation instance
     * @param annotatedType  The annotated type
     * @param annotatedClass The annotated class
     * @param annotations    Class-level annotations
     */
    private <T> void createModulePropertiesProviderBeanForFormToAdd(Annotation annotation, Class<? extends Annotation> annotationType,
                                                                    Class<?> annotatedClass, Set<Annotation> annotations) {
        try {
            Method loginToContinueMethod = annotationType.getMethod("loginToContinue");
            Annotation ltcAnnotation = (Annotation) loginToContinueMethod.invoke(annotation);
            Properties props = parseLoginToContinue(ltcAnnotation);
            Class<?> implClass;
            if (FormAuthenticationMechanismDefinition.class.equals(annotationType)) {
                implClass = FormAuthenticationMechanism.class;
            } else {
                implClass = CustomFormAuthenticationMechanism.class;
            }
            addAuthMech(applicationName, annotatedClass, implClass, annotations, props);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            // Do you need FFDC here? Remember FFDC instrumentation and @FFDCIgnore
            e.printStackTrace();
        }
    }

    /**
     * Update the HTTP authentication mechanism tracker which provides configuration for the HAMs,
     * with the properties for the BasicHAM.
     *
     * @param annotation     The annotation instance
     * @param annotatedType  The annotated type
     * @param annotatedClass The annotated class
     * @param annotations    Class-level annotations
     */
    private void createModulePropertiesProviderBeanForBasicToAdd(Annotation annotation, Class<? extends Annotation> annotationType,
                                                                 Class<?> annotatedClass, Set<Annotation> annotations) {
        try {
            Method realmNameMethod = annotationType.getMethod("realmName");
            String realmName = (String) realmNameMethod.invoke(annotation);
            Properties props = new Properties();
            props.put(JavaEESecConstants.REALM_NAME, realmName);
            addAuthMech(applicationName, annotatedClass, BasicHttpAuthenticationMechanism.class, annotations, props);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            // Do you need FFDC here? Remember FFDC instrumentation and @FFDCIgnore
            e.printStackTrace();
        }
    }

    /**
     * @param annotations
     * @param props
     */
    private void addDecoratOrAlternativeProps(Set<Annotation> annotations, Properties props) {
        //This is class level annotation
        for (Annotation annt : annotations) {
            Class<? extends Annotation> annType = annt.annotationType();
            if (DECORATOR.equals(annType.getSimpleName())) {
                if (tc.isDebugEnabled()) {
                    Tr.debug(tc, "Add Decorator=true");
                }
                props.put(DECORATOR, true);
            } else if (ALTERNATIVE.equals(annType.getSimpleName())) {
                if (tc.isDebugEnabled()) {
                    Tr.debug(tc, "Add Alternative=true");
                }
                props.put(ALTERNATIVE, true);

            }
        }
    }

    /**
     * @param beanManager
     * @param ltc         LoginToContinue annotation if it exists.
     * @param implClass   the implementation class
     */
    @SuppressWarnings("rawtypes")
    private void createModulePropertiesProviderBeanForApplicationAuthMechToAdd(BeanManager beanManager, Annotation ltc, Class implClass, Set<Annotation> annotations) {
        Properties props = null;
        if (ltc != null) {
            try {
                props = parseLoginToContinue(ltc);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                // Do you need FFDC here? Remember FFDC instrumentation and @FFDCIgnore
                e.printStackTrace();
            }
        }
        if (props == null) {
            props = new Properties();
        }

        addAuthMech(applicationName, implClass, implClass, annotations, props);
    }

    /**
     * @param beanManager
     * @param annotations
     */
    private void createModulePropertiesProviderBeanForGlobalLogin(BeanManager beanManager, Class annotatedClass, Set<Annotation> annotations, List<Class<?>> qualifiers) {
        try {
            Properties props;
            Class implClass;

            if (isAuthMechOverriddenByForm()) {
                props = getGlobalLoginFormProps();
                implClass = FormAuthenticationMechanism.class;
            } else {
                // basic
                props = getGlobalLoginBasicProps();
                implClass = BasicHttpAuthenticationMechanism.class;
            }
            addAuthMech(applicationName, annotatedClass, implClass, annotations, props);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            // Do you need FFDC here? Remember FFDC instrumentation and @FFDCIgnore
            e.printStackTrace();
        }
    }

    @Override
    public void addAuthMech(String applicationName, Class<?> annotatedClass, Class<?> implClass, Set<Annotation> annotations, Properties props) {
        if (props == null) {
            props = new Properties();
        }
        addDecoratOrAlternativeProps(annotations, props);
        httpAuthenticationMechanismsTracker.addAuthMech(applicationName, annotatedClass, implClass, annotations, props);
    }

    /**
     * @param ltcAnnotation
     */
    private Properties parseLoginToContinue(Annotation ltcAnnotation) throws Exception {
        Properties props = new Properties();
        Class<? extends Annotation> ltcAnnotationType = ltcAnnotation.annotationType();
        props.put(JavaEESecConstants.LOGIN_TO_CONTINUE_LOGINPAGE, getAnnotatedString(ltcAnnotation, ltcAnnotationType, JavaEESecConstants.LOGIN_TO_CONTINUE_LOGINPAGE));
        props.put(JavaEESecConstants.LOGIN_TO_CONTINUE_ERRORPAGE, getAnnotatedString(ltcAnnotation, ltcAnnotationType, JavaEESecConstants.LOGIN_TO_CONTINUE_ERRORPAGE));
        props.put(JavaEESecConstants.LOGIN_TO_CONTINUE_USEFORWARDTOLOGINEXPRESSION,
                  getAnnotatedString(ltcAnnotation, ltcAnnotationType, JavaEESecConstants.LOGIN_TO_CONTINUE_USEFORWARDTOLOGINEXPRESSION));
        props.put(JavaEESecConstants.LOGIN_TO_CONTINUE_USEFORWARDTOLOGIN,
                  getAnnotatedBoolean(ltcAnnotation, ltcAnnotationType, JavaEESecConstants.LOGIN_TO_CONTINUE_USEFORWARDTOLOGIN));
        return props;
    }

    private String getAnnotatedString(final Annotation annotation, final Class<? extends Annotation> annotationType, final String element) throws Exception {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<String>() {
                @Override
                public String run() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
                    return (String) annotationType.getMethod(element).invoke(annotation);
                }
            });
        } catch (PrivilegedActionException e) {
            throw e.getException();
        }
    }

    private boolean getAnnotatedBoolean(final Annotation annotation, final Class<? extends Annotation> annotationType, final String element) throws Exception {
        try {
            Boolean result = AccessController.doPrivileged(new PrivilegedExceptionAction<Boolean>() {
                @Override
                public Boolean run() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
                    return (Boolean) annotationType.getMethod(element).invoke(annotation);
                }
            });
            return result.booleanValue();
        } catch (PrivilegedActionException e) {
            throw e.getException();
        }
    }

    /**
     * @param beanManager
     * @param annotation
     * @param annotationType
     */
    private void createLdapIdentityStoreBeanToAdd(BeanManager beanManager, Annotation annotation, Class<? extends Annotation> annotationType) {
        try {
            Map<String, Object> identityStoreProperties = new HashMap<String, Object>();
            if (tc.isDebugEnabled())
                Tr.debug(tc, "JavaEESec.createLdapISBeanToAdd");
            Method[] methods = annotationType.getMethods();
            for (Method m : methods) {
                Tr.debug(tc, m.getName());
                if (!m.getName().equals("equals"))
                    identityStoreProperties.put(m.getName(), m.invoke(annotation));
            }
            LdapIdentityStoreDefinition ldapDefinition = getInstanceOfAnnotation(identityStoreProperties);
            if (!containsLdapDefinition(ldapDefinition, ldapDefinitionList)) {
                ldapDefinitionList.add(ldapDefinition);
                LdapIdentityStoreBean bean = new LdapIdentityStoreBean(beanManager, ldapDefinition);
                beansToAdd.add(bean);
                if (tc.isDebugEnabled())
                    Tr.debug(tc, "registering the default LdapIdentityStore.");
            } else {
                if (tc.isDebugEnabled())
                    Tr.debug(tc, "the same annotation exists, skip registering..");
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            // Do you need FFDC here? Remember FFDC instrumentation and @FFDCIgnore
            e.printStackTrace();
        }
    }

    private boolean containsLdapDefinition(LdapIdentityStoreDefinition ldapDefinition, List<LdapIdentityStoreDefinition> ldapDefinitionList) {
        for (LdapIdentityStoreDefinition lisd : ldapDefinitionList) {
            if (equalsLdapDefinition(ldapDefinition, lisd)) {
                return true;
            }
        }
        return false;
    }

    private LdapIdentityStoreDefinition getInstanceOfAnnotation(final Map<String, Object> overrides) {
        LdapIdentityStoreDefinition annotation = new LdapIdentityStoreDefinition() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return null;
            }

            @Override
            public String bindDn() {
                return (overrides != null && overrides.containsKey("bindDn")) ? (String) overrides.get("bindDn") : "";
            }

            @Override
            @Sensitive
            public String bindDnPassword() {
                return (overrides != null && overrides.containsKey("bindDnPassword")) ? (String) overrides.get("bindDnPassword") : "";
            }

            @Override
            public String callerBaseDn() {
                return (overrides != null && overrides.containsKey("callerBaseDn")) ? (String) overrides.get("callerBaseDn") : "";
            }

            @Override
            public String callerNameAttribute() {
                return (overrides != null && overrides.containsKey("callerNameAttribute")) ? (String) overrides.get("callerNameAttribute") : "uid";
            }

            @Override
            public String callerSearchBase() {
                return (overrides != null && overrides.containsKey("callerSearchBase")) ? (String) overrides.get("callerSearchBase") : "";
            }

            @Override
            public String callerSearchFilter() {
                return (overrides != null && overrides.containsKey("callerSearchFilter")) ? (String) overrides.get("callerSearchFilter") : "";

            }

            @Override
            public LdapSearchScope callerSearchScope() {
                return (overrides != null && overrides.containsKey("callerSearchScope")) ? (LdapSearchScope) overrides.get("callerSearchScope") : LdapSearchScope.SUBTREE;
            }

            @Override
            public String callerSearchScopeExpression() {
                return (overrides != null && overrides.containsKey("callerSearchScopeExpression")) ? (String) overrides.get("callerSearchScopeExpression") : "";
            }

            @Override
            public String groupMemberAttribute() {
                return (overrides != null && overrides.containsKey("groupMemberAttribute")) ? (String) overrides.get("groupMemberAttribute") : "member";
            }

            @Override
            public String groupMemberOfAttribute() {
                return (overrides != null && overrides.containsKey("groupMemberOfAttribute")) ? (String) overrides.get("groupMemberOfAttribute") : "memberOf";
            }

            @Override
            public String groupNameAttribute() {
                return (overrides != null && overrides.containsKey("groupNameAttribute")) ? (String) overrides.get("groupNameAttribute") : "cn";
            }

            @Override
            public String groupSearchBase() {
                return (overrides != null && overrides.containsKey("groupSearchBase")) ? (String) overrides.get("groupSearchBase") : "";
            }

            @Override
            public String groupSearchFilter() {
                return (overrides != null && overrides.containsKey("groupSearchFilter")) ? (String) overrides.get("groupSearchFilter") : "";
            }

            @Override
            public LdapSearchScope groupSearchScope() {
                return (overrides != null && overrides.containsKey("groupSearchScope")) ? (LdapSearchScope) overrides.get("groupSearchScope") : LdapSearchScope.SUBTREE;
            }

            @Override
            public String groupSearchScopeExpression() {
                return (overrides != null && overrides.containsKey("groupSearchScopeExpression")) ? (String) overrides.get("groupSearchScopeExpression") : "";
            }

            @Override
            public int maxResults() {
                return (overrides != null && overrides.containsKey("maxResults")) ? (Integer) overrides.get("maxResults") : 1000;
            }

            @Override
            public String maxResultsExpression() {
                return (overrides != null && overrides.containsKey("maxResultsExpression")) ? (String) overrides.get("maxResultsExpression") : "";
            }

            @Override
            public int priority() {
                return (overrides != null && overrides.containsKey(JavaEESecConstants.PRIORITY)) ? (Integer) overrides.get(JavaEESecConstants.PRIORITY) : 80;
            }

            @Override
            public String priorityExpression() {
                return (overrides != null && overrides.containsKey(JavaEESecConstants.PRIORITY_EXPRESSION)) ? (String) overrides.get(JavaEESecConstants.PRIORITY_EXPRESSION) : "";
            }

            @Override
            public int readTimeout() {
                return (overrides != null && overrides.containsKey("readTimeout")) ? (Integer) overrides.get("readTimeout") : 0;
            }

            @Override
            public String readTimeoutExpression() {
                return (overrides != null && overrides.containsKey("readTimeoutExpression")) ? (String) overrides.get("readTimeoutExpression") : "";
            }

            @Override
            public String url() {
                return (overrides != null && overrides.containsKey("url")) ? (String) overrides.get("url") : "";
            }

            @Override
            public ValidationType[] useFor() {
                return (overrides != null
                        && overrides.containsKey(JavaEESecConstants.USE_FOR)) ? (ValidationType[]) overrides.get(JavaEESecConstants.USE_FOR) : new ValidationType[] { ValidationType.PROVIDE_GROUPS,
                                                                                                                                                                      ValidationType.VALIDATE };
            }

            @Override
            public String useForExpression() {
                return (overrides != null && overrides.containsKey(JavaEESecConstants.USE_FOR_EXPRESSION)) ? (String) overrides.get(JavaEESecConstants.USE_FOR_EXPRESSION) : "";
            }

        };

        return annotation;
    }

    protected boolean equalsLdapDefinition(final LdapIdentityStoreDefinition lisd1, final LdapIdentityStoreDefinition lisd2) {
        return lisd1.bindDn().equals(lisd2.bindDn()) &&
               lisd1.bindDnPassword().equals(lisd2.bindDnPassword()) &&
               lisd1.callerBaseDn().equals(lisd2.callerBaseDn()) &&
               lisd1.callerNameAttribute().equals(lisd2.callerNameAttribute()) &&
               lisd1.callerSearchBase().equals(lisd2.callerSearchBase()) &&
               lisd1.callerSearchFilter().equals(lisd2.callerSearchFilter()) &&
               lisd1.callerSearchScope().equals(lisd2.callerSearchScope()) &&
               lisd1.callerSearchScopeExpression().equals(lisd2.callerSearchScopeExpression()) &&
               lisd1.groupMemberAttribute().equals(lisd2.groupMemberAttribute()) &&
               lisd1.groupMemberOfAttribute().equals(lisd2.groupMemberOfAttribute()) &&
               lisd1.groupNameAttribute().equals(lisd2.groupNameAttribute()) &&
               lisd1.groupSearchBase().equals(lisd2.groupSearchBase()) &&
               lisd1.groupSearchFilter().equals(lisd2.groupSearchFilter()) &&
               lisd1.groupSearchScope().equals(lisd2.groupSearchScope()) &&
               lisd1.groupSearchScopeExpression().equals(lisd2.groupSearchScopeExpression()) &&
               (lisd1.maxResults() == lisd2.maxResults()) &&
               lisd1.maxResultsExpression().equals(lisd2.maxResultsExpression()) &&
               (lisd1.priority() == lisd2.priority()) &&
               lisd1.priorityExpression().equals(lisd2.priorityExpression()) &&
               (lisd1.readTimeout() == lisd2.readTimeout()) &&
               lisd1.readTimeoutExpression().equals(lisd2.readTimeoutExpression()) &&
               lisd1.url().equals(lisd2.url()) &&
               equalsUseFor(lisd1.useFor(), lisd2.useFor()) &&
               lisd1.useForExpression().equals(lisd2.useForExpression());
    }

    private boolean equalsUseFor(ValidationType[] vt1, ValidationType[] vt2) {
        if (vt1 == vt2) {
            return true;
        } else if ((vt1.length == vt2.length) && (vt1.length == 1)) {
            return vt1[0] == vt2[0];
        } else {
            List<ValidationType> list1 = Arrays.asList(vt1);
            List<ValidationType> list2 = Arrays.asList(vt2);
            return (list1.contains(ValidationType.PROVIDE_GROUPS) == list2.contains(ValidationType.PROVIDE_GROUPS)) &&
                   (list1.contains(ValidationType.VALIDATE) == list2.contains(ValidationType.VALIDATE));
        }
    }

    /**
     * @param beanManager
     * @param annotation
     * @param annotationType
     */
    private void createDatabaseIdentityStoreBeanToAdd(BeanManager beanManager, Annotation annotation, Class<? extends Annotation> annotationType) {
        try {
            Map<String, Object> identityStoreProperties = new HashMap<String, Object>();
            if (tc.isDebugEnabled())
                Tr.debug(tc, "JavaEESec.createDatabaseIdentityStoreBeanToAdd");
            Method[] methods = annotationType.getMethods();
            for (Method m : methods) {
                Tr.debug(tc, m.getName());
                if (!m.getName().equals("equals"))
                    identityStoreProperties.put(m.getName(), m.invoke(annotation));
            }
            DatabaseIdentityStoreDefinition databaseDefinition = getInstanceOfDBAnnotation(identityStoreProperties);
            if (!containsDatabaseDefinition(databaseDefinition, databaseDefinitionList)) {
                DatabaseIdentityStoreBean bean = new DatabaseIdentityStoreBean(beanManager, databaseDefinition);
                beansToAdd.add(bean);
                if (tc.isDebugEnabled())
                    Tr.debug(tc, "registering the default DatabaseIdentityStore.");
            } else {
                if (tc.isDebugEnabled())
                    Tr.debug(tc, "the same annotation exists, skip registering..");
            }
        } catch (InvocationTargetException | IllegalAccessException e) {
            if (tc.isEventEnabled()) {
                Tr.event(tc, "unexpected", e);
            }
        }
    }

    private boolean containsDatabaseDefinition(DatabaseIdentityStoreDefinition dbDefinition, List<DatabaseIdentityStoreDefinition> dbDefinitionList) {
        for (DatabaseIdentityStoreDefinition disd : dbDefinitionList) {
            if (equalsDatabaseDefinition(dbDefinition, disd)) {
                return true;
            }
        }
        return false;
    }

    protected boolean equalsDatabaseDefinition(final DatabaseIdentityStoreDefinition disd1, final DatabaseIdentityStoreDefinition disd2) {
        return disd1.callerQuery().equals(disd2.callerQuery()) &&
               disd1.dataSourceLookup().equals(disd2.dataSourceLookup()) &&
               disd1.groupsQuery().equals(disd2.groupsQuery()) &&
               disd1.hashAlgorithm().equals(disd2.hashAlgorithm()) &&
               equalsHashAlgorithmParameters(disd1.hashAlgorithmParameters(), disd2.hashAlgorithmParameters()) &&
               (disd1.priority() == disd2.priority()) &&
               disd1.priorityExpression().equals(disd2.priorityExpression()) &&
               equalsUseFor(disd1.useFor(), disd2.useFor()) &&
               disd1.useForExpression().equals(disd2.useForExpression());
    }

    private boolean equalsHashAlgorithmParameters(String[] params1, String[] params2) {
        // don't need to consider null.
        if (params1 == params2) {
            return true;
        } else if (params1.length != params2.length) {
            return false;
        } else {
            Set<String> set1 = new HashSet<String>(Arrays.asList(params1));
            Set<String> set2 = new HashSet<String>(Arrays.asList(params2));
            return set1.equals(set2);
        }
    }

    protected boolean isIdentityStoreHandler(ProcessBean<?> processBean) {
        Bean<?> bean = processBean.getBean();
        // skip interface class.
        if (bean.getBeanClass() != IdentityStoreHandler.class) {
            Set<Type> types = bean.getTypes();
            if (types.contains(IdentityStoreHandler.class)) {
                // if it's not already registered.
                if (tc.isDebugEnabled())
                    Tr.debug(tc, "found a custom IdentityStoreHandler : " + bean.getBeanClass());
                return true;
            }
        }
        return false;
    }

    protected boolean isIdentityStore(ProcessBean<?> processBean) {
        Bean<?> bean = processBean.getBean();
        // skip interface class.
        if (bean.getBeanClass() != IdentityStore.class) {
            Set<Type> types = bean.getTypes();
            if (types.contains(IdentityStore.class)) {
                // if it's not already registered.
                if (tc.isDebugEnabled())
                    Tr.debug(tc, "found a custom IdentityStore : " + bean.getBeanClass());
                return true;
            }
        }
        return false;
    }

    /**
     * This is for the unit test.
     */
    protected Set<Bean> getBeansToAdd() {
        return beansToAdd;
    }

    /**
     * This is for the unit test.
     */
    protected boolean getIdentityStoreHandlerRegistered() {
        return identityStoreHandlerRegistered;
    }

    /**
     * This is for the unit test.
     */
    protected boolean getIdentityStoreRegistered() {
        return identityStoreRegistered;
    }

    // For unit testing
    protected Map<String, ModuleProperties> getModuleMap() {
        return httpAuthenticationMechanismsTracker.getModuleMap(applicationName);
    }

    protected Map<URL, ModuleMetaData> getModuleMetaDataMap() {
        return ModuleMetaDataAccessorImpl.getModuleMetaDataAccessor().getModuleMetaDataMap();
    }

    protected WebAppSecurityConfig getWebAppSecurityConfig() {
        return WebConfigUtils.getWebAppSecurityConfig();
    }

    private DatabaseIdentityStoreDefinition getInstanceOfDBAnnotation(final Map<String, Object> overrides) {
        DatabaseIdentityStoreDefinition annotation = new DatabaseIdentityStoreDefinition() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return null;
            }

            @Override
            public String callerQuery() {
                return (overrides != null && overrides.containsKey(JavaEESecConstants.CALLER_QUERY)) ? (String) overrides.get(JavaEESecConstants.CALLER_QUERY) : "";
            }

            @Override
            public String dataSourceLookup() {
                return (overrides != null
                        && overrides.containsKey(JavaEESecConstants.DS_LOOKUP)) ? (String) overrides.get(JavaEESecConstants.DS_LOOKUP) : JavaEESecConstants.DEFAULT_DS_NAME;
            }

            @Override
            public String groupsQuery() {
                return (overrides != null && overrides.containsKey(JavaEESecConstants.GROUPS_QUERY)) ? (String) overrides.get(JavaEESecConstants.GROUPS_QUERY) : "";
            }

            @Override
            public Class<? extends PasswordHash> hashAlgorithm() {
                return (overrides != null
                        && overrides.containsKey(JavaEESecConstants.PWD_HASH_ALGORITHM)) ? (Class<? extends PasswordHash>) overrides.get(JavaEESecConstants.PWD_HASH_ALGORITHM) : Pbkdf2PasswordHash.class;
            }

            @Override
            public String[] hashAlgorithmParameters() {
                return (overrides != null
                        && overrides.containsKey(JavaEESecConstants.PWD_HASH_PARAMETERS)) ? (String[]) overrides.get(JavaEESecConstants.PWD_HASH_PARAMETERS) : new String[] {};
            }

            @Override
            public int priority() {
                return (overrides != null && overrides.containsKey(JavaEESecConstants.PRIORITY)) ? (Integer) overrides.get(JavaEESecConstants.PRIORITY) : 70;
            }

            @Override
            public String priorityExpression() {
                return (overrides != null && overrides.containsKey(JavaEESecConstants.PRIORITY_EXPRESSION)) ? (String) overrides.get(JavaEESecConstants.PRIORITY_EXPRESSION) : "";
            }

            @Override
            public ValidationType[] useFor() {
                return (overrides != null
                        && overrides.containsKey(JavaEESecConstants.USE_FOR)) ? (ValidationType[]) overrides.get(JavaEESecConstants.USE_FOR) : new ValidationType[] { ValidationType.PROVIDE_GROUPS,
                                                                                                                                                                      ValidationType.VALIDATE };
            }

            @Override
            public String useForExpression() {
                return (overrides != null && overrides.containsKey(JavaEESecConstants.USE_FOR_EXPRESSION)) ? (String) overrides.get(JavaEESecConstants.USE_FOR_EXPRESSION) : "";
            }
        };
        return annotation;
    }

    /**
     * Verify the configuration after all the beans have been discovered.
     *
     * - ensure for Jakarta Security 1.0-3.0, there is one HAM for each module, and
     * - if there is a HAM in a module, make sure there is no login configuration in web.xml
     **/
    private void verifyConfiguration() throws DeploymentException {
        Map<URL, ModuleMetaData> mmds = getModuleMetaDataMap();
        if (mmds != null) {
            for (Map.Entry<URL, ModuleMetaData> entry : mmds.entrySet()) {
                ModuleMetaData mmd = entry.getValue();
                if (mmd instanceof WebModuleMetaData) {
                    String j2eeModuleName = mmd.getJ2EEName().getModule();
                    Map<Class<?>, Properties> authMechs = httpAuthenticationMechanismsTracker.getAuthMechs(applicationName, j2eeModuleName);
                    if (authMechs != null && !authMechs.isEmpty()) {

                        // one HAM for each module (JS 1.0-3.0 only)
                        if (!JakartaSecurityValidationService.isJakartaSecurity40OrHigher() && authMechs.size() != 1 && !isDecoratorOrAlternative(authMechs)) {
                            String appName = mmd.getJ2EEName().getApplication();
                            String authMechNames = getAuthMechNames(authMechs);
                            Tr.error(tc, "JAVAEESEC_CDI_ERROR_MULTIPLE_HTTPAUTHMECHS", j2eeModuleName, appName, authMechNames);
                            String msg = Tr.formatMessage(tc, "JAVAEESEC_CDI_ERROR_MULTIPLE_HTTPAUTHMECHS", j2eeModuleName, appName, authMechNames);
                            throw new DeploymentException(msg);
                        }

                        // correct number of HAMs, ensure no login config in web.xml
                        SecurityMetadata smd = (SecurityMetadata) ((WebModuleMetaData) mmd).getSecurityMetaData();
                        if (smd != null) {
                            LoginConfiguration lc = smd.getLoginConfiguration();
                            if (lc != null && !lc.isAuthenticationMethodDefaulted()) {
                                String appName = mmd.getJ2EEName().getApplication();
                                String msg = Tr.formatMessage(tc, "JAVAEESEC_CDI_ERROR_LOGIN_CONFIG_EXISTS", j2eeModuleName, appName);
                                Tr.error(tc, "JAVAEESEC_CDI_ERROR_LOGIN_CONFIG_EXISTS", j2eeModuleName, appName);
                                throw new DeploymentException(msg);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * @param authMechs
     */
    private boolean isDecoratorOrAlternative(Map<Class<?>, Properties> authMechs) {
        for (Entry<Class<?>, Properties> authMech : authMechs.entrySet()) {
            Properties value = authMech.getValue();
            //if (value != null && (value.contains(DECORATOR) || value.contains(ALTERNATIVE))) {
            if (value != null && (value.toString().contains(DECORATOR) || value.toString().contains(ALTERNATIVE))) {
                return true;
            }
        }
        return false;
    }

    private String getAuthMechNames(Map<Class<?>, Properties> authMechs) {
        StringBuffer result = new StringBuffer();
        boolean first = true;
        for (Class<?> authMech : authMechs.keySet()) {
            if (first) {
                first = false;
            } else {
                result.append(", ");
            }
            result.append(authMech.getName());
        }
        return result.toString();
    }

    protected String getApplicationName() {
        String result = null;
        Map<URL, ModuleMetaData> mmds = getModuleMetaDataMap();
        if (mmds != null && !mmds.isEmpty()) {
            for (Map.Entry<URL, ModuleMetaData> entry : mmds.entrySet()) {
                ModuleMetaData mmd = entry.getValue();
                if (mmd instanceof WebModuleMetaData) {
                    J2EEName j2eeName = mmd.getJ2EEName();
                    if (j2eeName != null) {
                        result = j2eeName.getApplication();
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns BasicAuth realm name for container override basic login
     */
    private Properties getGlobalLoginBasicProps() throws Exception {
        String realm = getWebAppSecurityConfig().getBasicAuthRealmName();
        Properties props = new Properties();
        if (realm == null) {
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "basicAuthenticationMechanismRealmName is not set. the default value " + JavaEESecConstants.DEFAULT_REALM + " is used.");
            }
        } else {
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "The container provided BasicAuthenticationMechanism will be used with the realm name  : " + realm);
            }
            props.put(JavaEESecConstants.REALM_NAME, realm);
        }
        return props;
    }

    /**
     * Returns LoginToContinue properties for container override form login
     */
    private Properties getGlobalLoginFormProps() throws Exception {
        WebAppSecurityConfig webAppSecConfig = getWebAppSecurityConfig();
        String loginURL = webAppSecConfig.getLoginFormURL();
        String errorURL = webAppSecConfig.getLoginErrorURL();
        if (loginURL == null || loginURL.isEmpty()) {
            Tr.error(tc, "JAVAEESEC_CDI_ERROR_NO_URL", "loginFormURL");
        }
        if (errorURL == null || errorURL.isEmpty()) {
            Tr.error(tc, "JAVAEESEC_CDI_ERROR_NO_URL", "loginErrorURL");
        }
        String contextRoot = webAppSecConfig.getLoginFormContextRoot();
        if (contextRoot == null) {
            // if a context root is not set, use the first path element of the login page.
            contextRoot = getFirstPathElement(loginURL);
            if (tc.isDebugEnabled()) {
                Tr.debug(tc, "loginFormContextRoot is not set, use the first element of loginURL  : " + contextRoot);
            }
        } else {
            if (!validateContextRoot(contextRoot, loginURL)) {
                Tr.error(tc, "JAVAEESEC_CDI_ERROR_INVALID_CONTEXT_ROOT", contextRoot, loginURL, "loginFormURL");
            }
            if (!validateContextRoot(contextRoot, errorURL)) {
                Tr.error(tc, "JAVAEESEC_CDI_ERROR_INVALID_CONTEXT_ROOT", contextRoot, errorURL, "loginErrorURL");
            }
        }
        // adjust the login and error url which need to be relative path from the context root.
        loginURL = FixUpUrl(loginURL, contextRoot);
        errorURL = FixUpUrl(errorURL, contextRoot);
        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "The container provided FormAuthenticationMechanism will be used with the following attributes. login page  : " + loginURL + ", error page : " + errorURL
                         + ", context root : " + contextRoot);
        }
        Properties props = new Properties();
        if (loginURL != null) {
            props.put(JavaEESecConstants.LOGIN_TO_CONTINUE_LOGINPAGE, loginURL);
        }
        if (errorURL != null) {
            props.put(JavaEESecConstants.LOGIN_TO_CONTINUE_ERRORPAGE, errorURL);
        }
        props.put(JavaEESecConstants.LOGIN_TO_CONTINUE_USEFORWARDTOLOGIN, true);
        props.put(JavaEESecConstants.LOGIN_TO_CONTINUE_USE_GLOBAL_LOGIN, true);
        if (contextRoot != null) {
            props.put(JavaEESecConstants.LOGIN_TO_CONTINUE_LOGIN_FORM_CONTEXT_ROOT, contextRoot);
        }
        return props;
    }

    private boolean isApplicationAuthMech(Class<?> javaClass) {
        if (HttpAuthenticationMechanism.class.isAssignableFrom(javaClass)) {
            if (!mechanismClasses.contains(javaClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method validates whether the authentication mechanism needs to be overridden by the global
     * login setting in webAppSecurityConfig element.
     * There are two condtions when the global login setting needs to be used:
     * 1. when overrideHttpAuthMethod attribute is set to FORM or BASIC.
     * 2. when overrideHttpAuthMethod attribute is set to CLIENT_CERT, and allowAuthenticationFailOverToAuthMethod
     * attribute is set to BASIC or FORM.
     */
    private boolean isAuthMechOverridden() {
        WebAppSecurityConfig webAppSecConfig = getWebAppSecurityConfig();
        if (webAppSecConfig == null) {
            // In an EJB-only context, WebAppSecurityConfig is not initialized
            if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
                Tr.debug(tc, "WebAppSecurityConfig is null, likely in an EJB-only context");
            }
            return false;
        }
        String value = webAppSecConfig.getOverrideHttpAuthMethod();
        if (value != null) {
            if ((value.equals(LoginConfiguration.FORM) || value.equals(LoginConfiguration.BASIC))) {
                return true;
            } else if (value.equals(LoginConfiguration.CLIENT_CERT)) {
                // if CLIENT_CERT is set, check failover setting.
                if (webAppSecConfig.getAllowFailOverToFormLogin() || webAppSecConfig.getAllowFailOverToBasicAuth()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAuthMechOverriddenByForm() {
        WebAppSecurityConfig webAppSecConfig = getWebAppSecurityConfig();
        String value = webAppSecConfig.getOverrideHttpAuthMethod();
        if (value != null) {
            if (value.equals(LoginConfiguration.FORM)) {
                return true;
            } else if (value.equals(LoginConfiguration.CLIENT_CERT)) {
                // if CLIENT_CERT is set, check failover setting.
                if (webAppSecConfig.getAllowFailOverToFormLogin()) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getFirstPathElement(String input) {
        // the input may or may not start with "/".
        String[] output = input.split("/");
        if (output[0].isEmpty()) {
            return "/" + output[1];
        } else {
            return "/" + output[0];
        }
    }

    private boolean validateContextRoot(String contextRoot, String url) {
        if (!contextRoot.startsWith("/")) {
            contextRoot = "/" + contextRoot;
        }
        if (!url.startsWith("/")) {
            url = "/" + url;
        }
        return (url.startsWith(contextRoot) && url.charAt(contextRoot.length()) == '/');
    }

    private String FixUpUrl(String input, String contextRoot) {
        // returns relative path from the contextRoot. if it does not find a match, return as it is.
        String output = input;
        if (input != null) {
            if (!input.startsWith("/")) {
                input = "/" + input;
            }
            if (input.startsWith(contextRoot) && input.charAt(contextRoot.length()) == '/') {
                output = input.substring(contextRoot.length());
            }
        }
        return output;
    }

    @Override
    public boolean existAuthMech(String applicationName, Class<?> authMechToExist) {
        return httpAuthenticationMechanismsTracker.existAuthMech(applicationName, authMechToExist);
    }

    @Override
    public boolean isEmptyModuleMap(String applicationName) {
        return httpAuthenticationMechanismsTracker.isEmptyModuleMap(applicationName);
    }

    /**
     * Check if the annotation type is an AuthenticationMechanismDefinition.List
     * Works for Basic, Form, and CustomForm HAM List annotations.
     *
     * Note: we can't explicitly "listen" for List annotations as they are nested
     * interfaces that only exist in Jakarta Security 4.0+
     *
     * @param annotationType The annotation type to check.
     * @param hamTypeName    The HAM type name to match (e.g., "Basic", "Form", "CustomForm").
     * @return true if the annotation is a List annotation for the specified HAM type.
     */
    private boolean isAuthenticationMechanismDefinitionList(Class<? extends Annotation> annotationType, String hamTypeName) {
        return annotationType.getName().contains(hamTypeName + "AuthenticationMechanismDefinition$List");
    }

    /**
     * Is a qualifier class the default qualifier for HAMs (JS 4.0+).
     *
     * @param qualifierClass The class to check.
     * @return true if class is the default qualifier, false else.
     */
    private boolean isDefaultQualifier(Class<?> qualifierClass) {
        String qualifierName = qualifierClass.getName();
        return qualifierName.contains("$BasicAuthenticationMechanism") ||
               qualifierName.contains("$FormAuthenticationMechanism") ||
               qualifierName.contains("$CustomFormAuthenticationMechanism");
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
