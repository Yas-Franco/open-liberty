/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.security.authorization.jacc.internal.web;

import static org.osgi.service.component.annotations.ConfigurationPolicy.IGNORE;

import java.util.Set;

import org.osgi.service.component.annotations.Component;

import jakarta.security.jacc.PolicyConfigurationFactory;
import jakarta.security.jacc.PolicyFactory;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

/**
 * Listeners for web applications to start and reads the init parameters for the web.xml
 * to configure that policy and configuration for Jakarta Security
 */
@Component(
           name = "io.openliberty.security.authorization.jacc.servlet.initializer",
           property = "service.vendor=IBM",
           configurationPolicy = IGNORE,
           immediate = true)
public class AuthorizationServletInitializer implements ServletContainerInitializer {

    @Override
    public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException {
        String policyFactoryName = servletContext.getInitParameter(PolicyFactory.FACTORY_NAME);
        ClassLoader appClassLoader = servletContext.getClassLoader();
        if (policyFactoryName != null) {
            PolicyFactory policyFactory = loadClass(appClassLoader, policyFactoryName, PolicyFactory.class);
            if (policyFactory != null) {
                PolicyFactory.setPolicyFactory(policyFactory);
            }
        }
        String configFactoryName = servletContext.getInitParameter(PolicyConfigurationFactory.FACTORY_NAME);
        if (configFactoryName != null) {
            PolicyConfigurationFactory configFactory = loadClass(appClassLoader, configFactoryName, PolicyConfigurationFactory.class);
            if (configFactory != null) {
                PolicyConfigurationFactory.setPolicyConfigurationFactory(configFactory);
            }
        }
    }

    private <T> T loadClass(ClassLoader appClassLoader, String className, Class<T> classType) {
        try {
            Class<?> loadedClass = appClassLoader.loadClass(className);
            return classType.cast(loadedClass.newInstance());
        } catch (ClassNotFoundException e) {
            // output a message if not found
        } catch (ClassCastException e) {
            // output a message if the class type is now correct
        } catch (IllegalAccessException e) {
            // output a message if the constructor is not public
        } catch (InstantiationException e) {
            // output a message if the constructor fails
        }
        return null;
    }
}
