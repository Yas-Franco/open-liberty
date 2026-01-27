/*******************************************************************************
 * Copyright (c) 2025, 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.security.authorization.jacc.internal.proxy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import com.ibm.websphere.ras.annotation.Trivial;
import com.ibm.ws.ffdc.annotation.FFDCIgnore;

import jakarta.security.jacc.PolicyConfiguration;
import jakarta.security.jacc.PolicyConfigurationFactory;
import jakarta.security.jacc.PolicyContext;
import jakarta.security.jacc.PolicyContextException;

public class JakartaPolicyConfigFactoryProxy extends PolicyConfigurationFactory {

    private final Map<String, JakartaPolicyConfigProxy> configMap = new ConcurrentHashMap<>();

    private final PolicyConfigurationFactory providedConfigFactory;

    private static volatile JakartaPolicyConfigFactoryProxy configFactoryProxy = null;

    static JakartaPolicyConfigFactoryProxy getInstance(PolicyConfigurationFactory providedFactory) {
        if (configFactoryProxy == null || configFactoryProxy.providedConfigFactory != providedFactory) {
            synchronized (JakartaPolicyConfigFactoryProxy.class) {
                if (configFactoryProxy == null || configFactoryProxy.providedConfigFactory != providedFactory) {
                    configFactoryProxy = new JakartaPolicyConfigFactoryProxy(providedFactory);
                }
            }
        }
        return configFactoryProxy;
    }

    public static JakartaPolicyConfigFactoryProxy getInstance() {
        return configFactoryProxy;
    }

    private JakartaPolicyConfigFactoryProxy(PolicyConfigurationFactory providedFactory) {
        providedConfigFactory = providedFactory;
    }

    @Override
    public PolicyConfiguration getPolicyConfiguration() {
        String currentContextID = PolicyContext.getContextID();
        if (currentContextID == null) {
            return null;
        }
        return configMap.get(currentContextID);
    }

    @Override
    public PolicyConfiguration getPolicyConfiguration(String contextId) {
        return configMap.get(contextId);
    }

    @Override
    public PolicyConfiguration getPolicyConfiguration(String contextId, boolean remove) throws PolicyContextException {

        // Do the fast check first for if it doesn't exist
        JakartaPolicyConfigProxy existingConfig = configMap.get(contextId);
        final AtomicBoolean newProxyAdded = new AtomicBoolean(false);
        if (existingConfig == null) {
            existingConfig = configMap.computeIfAbsent(contextId, new Function<String, JakartaPolicyConfigProxy>() {

                @Override
                public JakartaPolicyConfigProxy apply(String contextId) {
                    try {
                        JakartaPolicyConfigProxy newProxy = new JakartaPolicyConfigProxy(JakartaPolicyConfigFactoryProxy.this, contextId, remove);
                        newProxyAdded.set(true);
                        return newProxy;
                    } catch (PolicyContextException pce) {
                        sneakyThrow(pce);
                        return null;
                    }
                }
            });
        }

        if (newProxyAdded.get()) {
            return existingConfig;
        }

        existingConfig.resetDelegatePolicyConfig(remove);
        return existingConfig;
    }

    @SuppressWarnings("unchecked")
    static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    @Override
    public boolean inService(String contextId) {
        JakartaPolicyConfigProxy policyConfig = configMap.get(contextId);
        return policyConfig == null ? false : policyConfig.inService();
    }

    @FFDCIgnore(IllegalStateException.class)
    PolicyConfigurationFactory getFactory() {
        PolicyConfigurationFactory factory = null;
        try {
            factory = PolicyConfigurationFactory.get();
        } catch (IllegalStateException ise) {
            // expected if nothing was set up
            // if the user provided a ConfigurationFactory, set it
            if (providedConfigFactory != null) {
                PolicyConfigurationFactory.setPolicyConfigurationFactory(providedConfigFactory);
                factory = providedConfigFactory;
            }
        }
        return factory;
    }

    public void ensurePolicyConfigInitialized() {
        for (JakartaPolicyConfigProxy policyConfig : configMap.values()) {
            if (policyConfig != null) {
                policyConfig.ensureInitialized();
            }
        }
    }

    @Override
    @Trivial
    public String toString() {
        return super.toString() + " " + providedConfigFactory;
    }
}
