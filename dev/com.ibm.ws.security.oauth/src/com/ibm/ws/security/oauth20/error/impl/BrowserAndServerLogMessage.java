/*******************************************************************************
 * Copyright (c) 2019, 2025 IBM Corporation and others.
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
package com.ibm.ws.security.oauth20.error.impl;

import java.util.Enumeration;
import java.util.Locale;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.ws.security.common.lang.LocalesModifier;

/**
 * Small helper class to obtain an NLS message in both the locale of the browser and the locale of the server. This is meant
 * to avoid maintaining two or more lines to get the same NLS message. It can be easy to miss updating one of the lines if the
 * other line is updated at some point.
 */
public class BrowserAndServerLogMessage {
    /*
     * WORKAROUND for Liberty bytecode instrumentation limitation:
     *
     * The instrumentation tool requires TraceComponent fields to be 'private static final'.
     * However, this class needs instance-specific trace components to support different
     * OAuth providers and localized error messages.
     *
     * Solution: Declare a dummy static TraceComponent to satisfy instrumentation, then store
     * the actual instance TraceComponent as Object type (which instrumentation doesn't check).
     * The tc() helper method casts it back to TraceComponent when needed for message formatting.
     */
    private static final TraceComponent tcStatic = Tr.register(BrowserAndServerLogMessage.class);
    private final Object tcInstance;
    private Enumeration<Locale> requestLocales = null;
    private final String msgKey;
    private final Object[] inserts;

    public BrowserAndServerLogMessage(TraceComponent tc, String msgKey, Object... inserts) {
        this.tcInstance = tc;
        this.msgKey = msgKey;
        this.inserts = inserts;
    }
    
    private TraceComponent tc() {
        return (TraceComponent) tcInstance;
    }

    public String getBrowserErrorMessage() {
        return Tr.formatMessage(tc(), LocalesModifier.getPrimaryLocale(requestLocales), msgKey, inserts);
    }

    public String getServerErrorMessage() {
        return Tr.formatMessage(tc(), msgKey, inserts);
    }

    public void setLocales(Enumeration<Locale> requestLocales) {
        this.requestLocales = requestLocales;
    }
}
