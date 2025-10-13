/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.security.jakartasec40.cdi.extensions;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.security.javaeesec.cdi.extensions.HttpAuthenticationMechanismsTracker;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanismHandler;

/**
 * CDI Extension to register beans required for Jakarta Security 4.0.
 *
 * Copied and adapted from both Jakarta Security 3.0 and also com.ibm.ws.security.javaeesec (pre 3.0).
 */

@Component(service = {},
           immediate = true,
           configurationPolicy = ConfigurationPolicy.IGNORE,
           property = { "service.vendor=IBM", "api.classes=jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanismHandler" })
public class JakartaSecurity40CDIExtension implements Extension {

    private static final TraceComponent tc = Tr.register(JakartaSecurity40CDIExtension.class);
    private boolean httpAuthenticationMechanismHandlerRegistered = false;
    private final String applicationName;
    private final Set<Bean<?>> beansToAdd = new HashSet<Bean<?>>();

    public JakartaSecurity40CDIExtension() {
        applicationName = HttpAuthenticationMechanismsTracker.getApplicationName();
        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "JakartaSecurity40CDIExtension", "Using application name [" + applicationName + "].");
        }
    }

    public void processBean(@Observes ProcessBean<?> processBean, BeanManager beanManager) {
        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "instance: " + Integer.toHexString(this.hashCode()) + " BeanManager: " + Integer.toHexString(beanManager.hashCode()));
        }
        if (!httpAuthenticationMechanismHandlerRegistered) {
            if (isHttpAuthenticationMechanismHandler(processBean)) {
                httpAuthenticationMechanismHandlerRegistered = true;
            }
        }
    }

    protected boolean isHttpAuthenticationMechanismHandler(ProcessBean<?> processBean) {
        Bean<?> bean = processBean.getBean();
        // skip interface class.
        if (bean.getBeanClass() != HttpAuthenticationMechanismHandler.class) {
            Set<Type> types = bean.getTypes();
            if (types.contains(HttpAuthenticationMechanismHandler.class)) {
                // if it's not already registered.
                if (tc.isDebugEnabled())
                    Tr.debug(tc, "found a custom HttpAuthenticationMechanismHandler : " + bean.getBeanClass());
                return true;
            }
        }
        return false;
    }

    public <T> void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery, BeanManager beanManager) {
        if (!httpAuthenticationMechanismHandlerRegistered) {
            beansToAdd.add(new HttpAuthenticationMechanismHandlerBean(beanManager));
            if (tc.isDebugEnabled())
                Tr.debug(tc, "registering the default HttpAuthenticationMechanismHandler.");
        } else {
            if (tc.isDebugEnabled())
                Tr.debug(tc, "HttpAuthenticationMechanismHandler is not registered because a custom HttpAuthenticationMecahnismHandler has been registered");
        }

        if (tc.isDebugEnabled()) {
            Tr.debug(tc, "afterBeanDiscovery : instance : " + Integer.toHexString(this.hashCode()) + " BeanManager : " + Integer.toHexString(beanManager.hashCode()));
        }

        // Verification of mechanisms and registration of ModulePropertiesProviderBean performed in JavaEESecCDIExtension's afterBeanDiscovery()
        for (Bean<?> bean : beansToAdd) {
            afterBeanDiscovery.addBean(bean);
        }
    }
}
