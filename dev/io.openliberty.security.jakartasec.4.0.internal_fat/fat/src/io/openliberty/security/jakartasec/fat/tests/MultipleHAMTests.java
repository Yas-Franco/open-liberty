/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package io.openliberty.security.jakartasec.fat.tests;

import static org.junit.Assert.assertEquals;

import java.net.HttpURLConnection;
import java.net.URL;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.websphere.simplicity.ShrinkHelper.DeployOptions;

import componenttest.annotation.Server;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.FATServletClient;

/**
 * Tests appSecurity-6.0
 */
@RunWith(FATRunner.class)
@Mode(TestMode.LITE)
public class MultipleHAMTests extends FATServletClient {

    public static final String SERVER_NAME = "basicServer";
    public static final String APP_NAME = "basicApp";

    private static String url = null;

    @Server(SERVER_NAME)
    public static LibertyServer server;

    @BeforeClass
    public static void setUp() throws Exception {
        WebArchive multipleHamApp = ShrinkWrap.create(WebArchive.class,
                                                      APP_NAME + ".war").addPackage("multiple.ham.custom");

        // The URL is not expected to be modified during this test scope
        url = "http://localhost:" + server.getHttpDefaultPort() + "/basicServlet";

        ShrinkHelper.exportDropinAppToServer(server, multipleHamApp, DeployOptions.SERVER_ONLY);

        server.startServer();
    }

    @Test
    public void testCustomHam() throws Exception {
        System.out.println("We are testing the custom hams");
        URL urlObj = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();

        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        int responseCode = conn.getResponseCode();
        assertEquals("Expected status code 200 but got " + responseCode, 200, responseCode);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        server.stopServer();
    }

}
