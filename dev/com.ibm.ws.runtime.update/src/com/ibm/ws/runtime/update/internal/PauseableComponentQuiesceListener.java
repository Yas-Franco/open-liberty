/*******************************************************************************
 * Copyright (c) 2018, 2026 IBM Corporation and others.
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
package com.ibm.ws.runtime.update.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.kernel.launch.service.PauseableComponent;
import com.ibm.ws.kernel.launch.service.PauseableComponentException;
import com.ibm.wsspi.kernel.service.utils.ServerQuiesceListener;

/**
 * TODO Find a better place for this to live
 * This ServerQuiesceListener implementation will pause all pausable components.
 */
@Component(configurationPolicy = ConfigurationPolicy.IGNORE)
public class PauseableComponentQuiesceListener implements ServerQuiesceListener {

    private static final TraceComponent tc = Tr.register(PauseableComponentQuiesceListener.class);

    /**
     * Note:
     * Refactored to use a synchronized list of pauseable components which causes lower memory overhead
     */
    @Reference(cardinality = ReferenceCardinality.MULTIPLE,
               policy = ReferencePolicy.DYNAMIC,
               fieldOption = FieldOption.UPDATE)
    private final List<PauseableComponent> pauseableComponents = Collections.synchronizedList(new ArrayList<>());

    /*
     * (non-Javadoc)
     *
     * @see com.ibm.wsspi.kernel.service.utils.ServerQuiesceListener#serverStopping()
     */
    @Override
    public void serverStopping() {
        pauseableComponents.forEach(pc -> {
            if (!pc.isPaused()) {
                try {
                    pc.pause();
                } catch (PauseableComponentException ex) {
                    Tr.warning(tc, "warn.did.not.pause.on.shutdown", ex.getMessage());
                } catch (Throwable t) {
                    // auto-ffdc but still call the rest of the pauseable components
                    Tr.error(tc, "error.unexpected.pause.failure", pc.getClass().getName(), t);
                }
            }
        });
    }

}