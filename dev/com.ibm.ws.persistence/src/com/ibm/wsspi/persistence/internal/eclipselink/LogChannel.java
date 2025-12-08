/*******************************************************************************
 * Copyright (c) 2014, 2025 IBM Corporation and others.
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
package com.ibm.wsspi.persistence.internal.eclipselink;

import org.eclipse.persistence.logging.SessionLogEntry;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.websphere.ras.annotation.Trivial;
import com.ibm.wsspi.persistence.internal.PersistenceServiceConstants;

//TODO(151905) -- Cleanup. Poached from Liberty
@Trivial
class LogChannel {
    /*
     * WORKAROUND for Liberty bytecode instrumentation limitation:
     *
     * The instrumentation tool requires TraceComponent fields to be 'private static final'.
     * However, this class needs instance-specific trace components for different EclipseLink channels.
     *
     * Solution: Declare a dummy static TraceComponent to satisfy instrumentation, then store
     * the actual instance TraceComponent as Object type (which instrumentation doesn't check).
     * The _tc() helper method casts it back to TraceComponent when needed for logging.
     */
    private static final TraceComponent _stc = Tr.register(LogChannel.class);
    private final Object _tcInstance;
    
    private TraceComponent _tc() {
        return (TraceComponent) _tcInstance;
    }

    /**
     * Log levels (per EclipseLink)
     * <ul>
     * <li>8=OFF
     * <li>7=SEVERE
     * <li>6=WARNING
     * <li>5=INFO
     * <li>4=CONFIG
     * <li>3=FINE
     * <li>2=FINER
     * <li>1=FINEST
     * <li>0=TRACE
     * </ul>
     *
     * @param channel
     */
    LogChannel(String channel) {
        // Register a TraceComponent for this specific channel
        _tcInstance = Tr.register(channel, LogChannel.class, PersistenceServiceConstants.TRACE_GROUP);
    }

    boolean shouldLog(int level) {
        switch (level) {
            case 8:
                return false;
            case 7: // SEVERE
                return _tc().isErrorEnabled();
            case 6: // WARN
                return _tc().isWarningEnabled();
            case 5: // INFO
                return _tc().isInfoEnabled();
            case 4: // CONFIG
                return _tc().isConfigEnabled();
            case 3: // FINE
                return _tc().isEventEnabled();
            case 2: // FINER
                return _tc().isEntryEnabled();
            case 1: // FINEST
            case 0: // TRACE
            default:
                return _tc().isDebugEnabled();
        }// end switch
    }

    /**
     * This method will only be called AFTER affirming that we should be logging
     */
    void log(SessionLogEntry entry, String formattedMessage) {
        int level = entry.getLevel();
        Throwable loggedException = entry.getException();

        String msgParm;
        if ((formattedMessage == null || formattedMessage.equals("")) && loggedException != null) {
            msgParm = loggedException.toString();
        } else {
            msgParm = formattedMessage;
        }

        switch (level) {
            case 8:
                return;
            case 7: // SEVERE
                // With the persistence service, only errors will be logged.  The rest will be debug.
                String errMsg = Tr.formatMessage(_stc, "PROVIDER_ERROR_CWWKD0292E", msgParm);
                Tr.error(_tc(), errMsg);
                break;
            case 6:// WARN
                String wrnMsg = Tr.formatMessage(_stc, "PROVIDER_WARNING_CWWKD0291W", msgParm);
                Tr.debug(_tc(), wrnMsg); // 170432 - move warnings to debug
                break;
            case 3: // FINE
            case 2: // FINER
            case 1: // FINEST
            case 0: // TRACE
                Tr.debug(_tc(), formattedMessage);
                break;
        }// end switch

        // if there is an exception - only log it to trace
        if (_tc().isDebugEnabled() && loggedException != null) {
            Tr.debug(_tc(), "throwable", loggedException);
        }
    }
}