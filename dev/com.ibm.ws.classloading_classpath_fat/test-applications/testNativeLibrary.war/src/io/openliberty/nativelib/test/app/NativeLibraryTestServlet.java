/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.nativelib.test.app;

import static io.openliberty.classloading.classpath.fat.FATSuite.LIB1_CLASS_NAME;
import static io.openliberty.classloading.classpath.fat.FATSuite.LIB2_CLASS_NAME;
import static io.openliberty.classloading.classpath.util.TestUtils.assertFindLibrary;

import javax.servlet.annotation.WebServlet;

import org.junit.Test;

import componenttest.app.FATServlet;

@WebServlet("/NativeLilbraryTestServlet")
public class NativeLibraryTestServlet extends FATServlet{

    private static final long serialVersionUID = 1L;


    @Test
    public void testPrivateLibraryNativeFromLibSuccess() {
        // The library class can find its own native from its class loader
        assertFindLibrary(LIB1_CLASS_NAME, "privateNative", true);
    }

    @Test
    public void testPrivateLibraryNativeFromAppSuccess() {
        // The app class can find the native from the private library because it uses the same class loader
        assertFindLibrary(getClass().getName(), "privateNative", true);
    }

    @Test
    public void testCommonLibraryNativeFromLibSuccess() {
        // The library class can find its own native from its class loader
        assertFindLibrary(LIB2_CLASS_NAME, "commonNative", true);
    }

    @Test
    public void testCommonLibraryNativeFormAppFail() {
        // The app class can NOT find the native from the common library because it uses a different class loader
        assertFindLibrary(getClass().getName(), "commonNative", false);
    }
}
