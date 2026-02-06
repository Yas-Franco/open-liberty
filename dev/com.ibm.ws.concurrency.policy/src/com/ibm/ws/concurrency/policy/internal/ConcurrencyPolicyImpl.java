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
package com.ibm.ws.concurrency.policy.internal;

import java.util.HashMap;
import java.util.Map;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

import com.ibm.ws.concurrency.policy.ConcurrencyPolicy;
import com.ibm.ws.threading.PolicyExecutor;
import com.ibm.ws.threading.PolicyExecutorProvider;

/**
 * Corresponds to a <code>concurrencyPolicy</code> configuration element.
 * Allows you to obtain the policy executor that implements the configured policy.
 */
@Component(name = "com.ibm.ws.concurrency.policy.concurrencyPolicy",
           configurationPolicy = ConfigurationPolicy.REQUIRE,
           immediate = true, // to keep this component active in order to be able to finish running tasks even if ref count drops to 0
           service = { ConcurrencyPolicy.class },
           property = { "service.pid=com.ibm.ws.concurrency.policy.concurrencyPolicy" })
public class ConcurrencyPolicyImpl implements ConcurrencyPolicy {
    /**
     * Lazily initialized policy executor instance corresponding to the configuration.
     */
    private PolicyExecutor executor;

    /**
     * OSGi service component properties. A non-null value indicates the service component is active.
     */
    private Map<String, Object> props;

    @Reference
    protected PolicyExecutorProvider provider;

    @Activate
    protected void activate(ComponentContext context, Map<String, Object> props) {
        this.props = setDefaultMaxPolicy(props);

    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        PolicyExecutor px;
        synchronized (this) {
            px = executor;
            executor = null;
            props = null;
        }
        if (px != null)
            px.shutdownNow();
    }

    /**
     * @see com.ibm.ws.concurrency.policy.ConcurrencyPolicy#getExecutor()
     */
    @Override
    public synchronized PolicyExecutor getExecutor() {
        if (props == null)
            throw new IllegalStateException();
        if (executor == null)
            executor = provider.create(props);
        return executor;
    }

    @Modified
    protected void modified(ComponentContext context, Map<String, Object> props) {
        PolicyExecutor px;
        synchronized (this) {
            px = executor;
            this.props = setDefaultMaxPolicy(props);
        }
        if (px != null)
            px.updateConfig(setDefaultMaxPolicy(props));
    }

    private Map<String, Object> setDefaultMaxPolicy(Map<String, Object> props) {
        if ((boolean) props.getOrDefault("virtual", false)) { //virtual threads
            if (!props.getOrDefault("maxPolicy", "").equals("loose")) {//default to strict for virtual threads
                props = new HashMap<String, Object>(props);
                props.put("maxPolicy", "strict");
            }
        } else { //platform threads
            if (!props.getOrDefault("maxPolicy", "").equals("strict")) {
                //The default value in metatype for maxPolicy was removed with Jakarta Concurrency 3.1 to
                //accommodate virtual threads. This maintains backward compatibility for platform threads
                //by keeping the default of maxPolicy=loose
                props = new HashMap<String, Object>(props);
                props.put("maxPolicy", "loose");
            }
        }

        return props;
    }
}