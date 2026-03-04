/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package com.ibm.ws.resource;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;

import com.ibm.wsspi.resource.ResourceInfo;

/**
 * A convenience class that integrates a ResourceFactory and SynchronousBundleListener when
 * the resource factory requires that a specific bundle be available at runtime.
 */
public abstract class AbstractWaitForBundleResourceFactory implements SynchronousBundleListener, ResourceFactory {

    private final BundleContext bundleContext;
    private final String targetBundleLocation;
    private final int requiredStateMask;

    private final AtomicReference<ResourceFactory> delegate = new AtomicReference<>();
    private final AtomicReference<Exception> createException = new AtomicReference<>();

    protected AbstractWaitForBundleResourceFactory(BundleContext bundleContext, String targetBundleLocation) {
        this(bundleContext, targetBundleLocation, Bundle.STARTING | Bundle.ACTIVE);
    }

    protected AbstractWaitForBundleResourceFactory(BundleContext bundleContext,
                                                   String targetBundleLocation,
                                                   int requiredStateMask) {
        this.bundleContext = bundleContext;
        this.targetBundleLocation = targetBundleLocation;
        this.requiredStateMask = requiredStateMask;
    }

    public final void listenForBundle() {
        bundleContext.addBundleListener(this);

        // If it already exists, we might have missed the event.
        Bundle b = bundleContext.getBundle(targetBundleLocation);
        if (b != null) {
            tryCreateDelegate(b);
        }
    }

    @Override
    public final void bundleChanged(BundleEvent event) {
        tryCreateDelegate(event.getBundle());
    }

    private void tryCreateDelegate(Bundle b) {
        delegate.getAndUpdate(existing -> {
            if (existing != null) {
                return existing;
            }
            if (!matchesTargetBundle(b)) {
                return null;
            }
            if ((b.getState() & requiredStateMask) == 0) {
                return null;
            }

            try {
                ResourceFactory rf = createDelegate(b);
                return rf;
            } catch (Exception ex) {
                createException.compareAndSet(null, ex);
                return null;
            } finally {
                // Stop listening once we've either created successfully or failed creation.
                // If you want to keep listening after failures, move this into the success path only.
                cleanupListener();
            }
        });
    }

    protected boolean matchesTargetBundle(Bundle b) {
        return targetBundleLocation.equals(b.getLocation());
    }

    protected final String getTargetBundleLocation() {
        return targetBundleLocation;
    }

    protected abstract ResourceFactory createDelegate(Bundle b) throws Exception;

    protected RuntimeException notReadyException() {
        return new IllegalStateException("ResourceFactory not ready; bundle not available/active: "
                                         + targetBundleLocation);
    }

    private void cleanupListener() {
        try {
            bundleContext.removeBundleListener(this);
        } catch (IllegalStateException ignored) {
            // BundleContext might already be stopping.
        }
    }

    protected final ResourceFactory getDelegate() throws Exception {
        ResourceFactory rf = delegate.get();
        if (rf != null) {
            return rf;
        }
        Exception ex = createException.get();
        if (ex != null) {
            throw ex;
        }
        throw notReadyException();
    }

    @Override
    public final Object createResource(ResourceInfo info) throws Exception {
        return getDelegate().createResource(info);
    }

    @Override
    public final Object createResource(ResourceRefInfo info) throws Exception {
        return getDelegate().createResource(info);
    }

    @Override
    public void destroy() throws Exception {
        ResourceFactory rf = delegate.get();
        if (rf != null) {
            rf.destroy();
        }
    }

    @Override
    public void modify(Map<String, Object> props) throws Exception {
        getDelegate().modify(props);
    }
}
