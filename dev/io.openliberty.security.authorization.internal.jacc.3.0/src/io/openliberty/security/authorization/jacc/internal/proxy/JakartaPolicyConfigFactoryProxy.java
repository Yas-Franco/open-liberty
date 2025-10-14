/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.security.authorization.jacc.internal.proxy;

import com.ibm.ws.ffdc.annotation.FFDCIgnore;

import jakarta.security.jacc.PolicyConfiguration;
import jakarta.security.jacc.PolicyConfigurationFactory;
import jakarta.security.jacc.PolicyContextException;

public class JakartaPolicyConfigFactoryProxy extends PolicyConfigurationFactory {

    private final PolicyConfigurationFactory providedConfigFactory;

    JakartaPolicyConfigFactoryProxy(PolicyConfigurationFactory providedFactory) {
        providedConfigFactory = providedFactory;

    }

    @Override
    public PolicyConfiguration getPolicyConfiguration() {
        PolicyConfigurationFactory factory = getFactory();
        return factory == null ? null : factory.getPolicyConfiguration();
    }

    @Override
    public PolicyConfiguration getPolicyConfiguration(String contextId) {
        PolicyConfigurationFactory factory = getFactory();
        return factory == null ? null : factory.getPolicyConfiguration(contextId);
    }

    @Override
    public PolicyConfiguration getPolicyConfiguration(String contextId, boolean remove) throws PolicyContextException {
        PolicyConfigurationFactory factory = getFactory();
        return factory == null ? null : factory.getPolicyConfiguration(contextId, remove);
    }

    @Override
    public boolean inService(String contextId) throws PolicyContextException {
        PolicyConfigurationFactory factory = getFactory();
        return factory == null ? false : factory.inService(contextId);
    }

    @FFDCIgnore(IllegalStateException.class)
    private PolicyConfigurationFactory getFactory() {
        PolicyConfigurationFactory factory = null;
        try {
            factory = PolicyConfigurationFactory.get();
        } catch (IllegalStateException ise) {
            // expected if nothing we set up
            // if the user provided a ConfigurationFactory, set it
            if (providedConfigFactory != null) {
                PolicyConfigurationFactory.setPolicyConfigurationFactory(providedConfigFactory);
                factory = providedConfigFactory;
            }
        }
        return factory;
    }
}
