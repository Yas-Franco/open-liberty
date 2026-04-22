/*******************************************************************************
 * Copyright (c) 2009, 2026 IBM Corporation and others.
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
package com.ibm.ws.channelfw.internal;

import java.util.ArrayList;
import java.util.Iterator;

import com.ibm.websphere.channelfw.ChainData;
import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.wsspi.channelfw.ChannelFramework;
import com.ibm.wsspi.channelfw.ChannelFrameworkFactory;

/**
 * Polls a list of chains until they're stopped or the quiesce timeout is hit.
 *
 */
public class UtilsChainListener {
    /** Trace service */
    private static final TraceComponent tc = Tr.register(UtilsChainListener.class, ChannelFrameworkConstants.BASE_TRACE_NAME, ChannelFrameworkConstants.BASE_BUNDLE);

    private final ArrayList<String> waitingChainNames = new ArrayList<String>();

    /**
     * Constructor.
     */
    public UtilsChainListener() {

    }

    /**
     * Notify this listener to watch another chain.
     *
     * @param chain
     */
    public void watchChain(ChainData chain) {
        waitingChainNames.add(chain.getName());
    }

    /**
     * Perform a quick check (up to 1 second) to see if any chains stopped immediately.
     * Remove any chains found to be stopped.
     * This does not wait for the full chainQuiesceTimeout.
     * Chains that are still quiescing will be forcefully stopped by StopChainTask
     * when the chainQuiesceTimeout expires.
     *
     * @param chainQuiesceTimeout Determines if we should pause for a second.
     *                            If 1 second or less, just do a quick check with no delay.
     *
     */
    public void cleanUpChains(long chainQuiesceTimeout) {

        if (waitingChainNames.isEmpty()) {
            return;
        }
        
        if (TraceComponent.isAnyTracingEnabled() && tc.isEventEnabled()) {
            Tr.event(this, tc, "Quick check: " + waitingChainNames.size() + " chain(s) may still be quiescing");
        }
        
        // Remove any chains that already stopped
        removeStoppedChains();
        
        if (waitingChainNames.isEmpty() || chainQuiesceTimeout <= 1000) {
            if (TraceComponent.isAnyTracingEnabled() && tc.isEventEnabled()) {
                if (waitingChainNames.isEmpty()) {
                    Tr.event(this, tc, "All chains already stopped");
                } else {
                    Tr.event(this, tc, "Skipping 1-second wait (timeout too short)");
                }
            }
            return;
        }

        
        // Give chains 1 second to stop
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            // ignore
        }
        
        // Remove chains that stopped during the wait
        removeStoppedChains();
        
        if (TraceComponent.isAnyTracingEnabled() && tc.isEventEnabled()) {
            Tr.event(this, tc, "After quick check: " + waitingChainNames.size() + " chain(s) still quiescing");
        }
    }

    /**
     * Remove stopped chains from the watch list.
     */
    private void removeStoppedChains() {
        if (waitingChainNames.isEmpty()) {
            return;
        }
        
        ChannelFramework cf = ChannelFrameworkFactory.getChannelFramework();
        Iterator<String> iter = waitingChainNames.iterator();
        while (iter.hasNext()) {
            if (!cf.isChainRunning(iter.next())) {
                iter.remove();
            }
        }
    }

    /**
     * Wait for all watched chains to stop, polling periodically.
     * This method blocks until either all chains have stopped or the timeout expires.
     * Stopped chains are removed from the list.
     *
     * @param chainQuiesceTimeout Maximum time to wait in milliseconds
     */
    public void waitForChainsToStop(long chainQuiesceTimeout) {
        if (waitingChainNames.isEmpty()) {
            return;
        }
        
        if (TraceComponent.isAnyTracingEnabled() && tc.isEventEnabled()) {
            Tr.event(this, tc, "Waiting for " + waitingChainNames.size() + " chain(s) to stop");
        }

        long startTime = System.nanoTime();
        long expireTime = chainQuiesceTimeout * 1_000_000L;  // Convert ms to ns
        long elapsedTime;
               
        // Always cleanup at least once, then keep checking until timeout or all chains stopped
        do {
            removeStoppedChains();  // Remove chains that have stopped
            
            elapsedTime = System.nanoTime() - startTime;
            if (waitingChainNames.isEmpty() || (elapsedTime >= expireTime)) {
                break;  // All chains stopped, or timeout expired
            }
            
            try {
                Thread.sleep(100);  // Poll every 100ms
            } catch (InterruptedException ie) {
                break;
            }
            
        } while (true);
        
        elapsedTime = System.nanoTime() - startTime;
        
        if (TraceComponent.isAnyTracingEnabled() && tc.isEventEnabled()) {
            if (waitingChainNames.isEmpty()) {
                Tr.event(this, tc, "All chains stopped after " + elapsedTime + " ns");
            } else {
                Tr.event(this, tc, "Timeout expired, " + waitingChainNames.size() + " chain(s) still running");
            }
        }
    }

}
