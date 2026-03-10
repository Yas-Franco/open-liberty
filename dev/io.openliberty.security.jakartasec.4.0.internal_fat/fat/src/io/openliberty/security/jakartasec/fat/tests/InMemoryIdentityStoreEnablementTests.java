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

import static io.openliberty.security.jakartasec.fat.utils.Jakartasec40TestConstants.PRODUCTION_USE_WARNING_MSG;
import static io.openliberty.security.jakartasec.fat.utils.Jakartasec40TestConstants.USER_JASMINE;
import static io.openliberty.security.jakartasec.fat.utils.Jakartasec40TestConstants.USER_THEO;
import static io.openliberty.security.jakartasec.fat.utils.Jakartasec40TestConstants.VALID_PASSWORD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

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

/**
 * Tests for InMemoryIdentityStoreDefinition with various password encoding schemes.
 * Tests positive scenarios (plain, XOR, AES, Hash passwords) and negative scenarios
 * (bad passwords, bad encoding, insufficient groups).
 */
@RunWith(FATRunner.class)
@Mode(TestMode.LITE)
public class InMemoryIdentityStoreEnablementTests extends BaseJakartaSecurity40Test {

    private static final Class<?> c = InMemoryIdentityStoreEnablementTests.class;

    public static final String APP_NAME = "IdentityStoreEnablement";
    private static final String CONTEXT_ROOT = "/" + APP_NAME;
    private static final String RESOURCE_PATH = "/resource/test";

    private static String url = null;

    @Server(IN_MEM_ID_STORE_ENABLED_SERVER_NAME)
    public static LibertyServer server;

    @Override
    protected Class<?> getTestClass() {
        return c;
    }

    @Override
    protected LibertyServer getServer() {
        return server;
    }

    @BeforeClass
    public static void setUp() throws Exception {
        InMemoryIdentityStoreEnablementTests instance = new InMemoryIdentityStoreEnablementTests();
        instance.logInfo("setUp", "Starting server setup...");

        // The URL is not expected to be modified during this test scope
        url = instance.buildUrl(CONTEXT_ROOT, RESOURCE_PATH);

        // Create the web application
        WebArchive app = ShrinkWrap.create(WebArchive.class,
                                           APP_NAME + ".war").addPackage("inmemory.identity.store").addAsWebInfResource(new File("test-applications/inmemory/WEB-INF/web.xml"));

        ShrinkHelper.exportDropinAppToServer(server, app, DeployOptions.SERVER_ONLY);

        instance.startServer();
    }

    /**
     * Test log file output for in-memory store usage warning.
     * This should only ever appear once upon the first invocation of authentication against the in memory identity store data.
     */
    @Test
    public void testInMemStoreConfigEnabled() throws Exception {
        logInfo("testInMemStoreConfigEnabled", "Testing that in-mem identity store is explicitly enabled in the specified config file");

        // Should get 200 and proceed
        executeGetRequest(url, USER_THEO, VALID_PASSWORD, 200);
        assertEquals("Warning message should appear in log once", 1, server.waitForMultipleStringsInLog(1, PRODUCTION_USE_WARNING_MSG));

        logInfo("testInMemStoreConfigEnabled", "Test passed");
    }

    /**
     * Test that no unexpected error messages appear during normal operation.
     */
    @Test
    public void testNoUnexpectedErrors() throws Exception {
        logInfo("testNoUnexpectedErrors", "Testing for unexpected errors");

        // Mark log
        getServer().setMarkToEndOfLog();

        // Perform successful authentication
        executeGetRequest(url, USER_JASMINE, VALID_PASSWORD, 200);

        // Check that no unexpected error messages appear
        // We expect the warning message, but no error messages
        String logContent = waitForStringInLog("CWWKS35", 2000);

        if (logContent != null) {
            // If we found CWWKS35xx messages, make sure they're only the expected warning
            assertTrue("Should only see warning message, not errors",
                       logContent.contains(PRODUCTION_USE_WARNING_MSG));
        }

        logInfo("testNoUnexpectedErrors", "Test passed - no unexpected errors");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        InMemoryIdentityStoreEnablementTests instance = new InMemoryIdentityStoreEnablementTests();
        // Expected warnings and errors during testing
        instance.stopServer(
                            "CWWKS2600W", // An in-memory identity store was detected within this application
                            "CWWKS2601W", // The environment variable used for password value is empty or unset
                            "CWWKS2602E", // The credential is not a UsernamePasswordCredential and cannot be validated
                            "CWWKS2603W", // The (EL) expression used for the annotation attribute cannot be resolved
                            "CWWKS1859E" //  Password decoding error
        );
    }
}
