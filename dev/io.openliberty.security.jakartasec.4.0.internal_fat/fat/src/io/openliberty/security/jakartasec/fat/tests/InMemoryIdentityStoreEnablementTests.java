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
import static io.openliberty.security.jakartasec.fat.utils.Jakartasec40TestConstants.SERVER_CONFIG_UPDATE_MESSAGES_REGEX;
import static io.openliberty.security.jakartasec.fat.utils.Jakartasec40TestConstants.USER_JASMINE;
import static io.openliberty.security.jakartasec.fat.utils.Jakartasec40TestConstants.USER_THEO;
import static io.openliberty.security.jakartasec.fat.utils.Jakartasec40TestConstants.VALID_PASSWORD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.RemoteFile;
import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.websphere.simplicity.ShrinkHelper.DeployOptions;

import componenttest.annotation.Server;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;

/**
 * Tests for InMemoryIdentityStore enabling element on server configuration.
 * Tests several scenarios with specific server configuration files
 * (missing element, existing element set to false/true).
 */
@RunWith(FATRunner.class)
@Mode(TestMode.LITE)
public class InMemoryIdentityStoreEnablementTests extends BaseJakartaSecurity40Test {

    private static final Class<?> c = InMemoryIdentityStoreEnablementTests.class;

    public static final String APP_NAME = "IdentityStoreEnablement";
    private static final String CONTEXT_ROOT = "/" + APP_NAME;
    private static final String RESOURCE_PATH = "/resource/test";
    public static final String SERVER_XML_ID_STORE_DISABLED = "inMemoryIdStoreDisabled.xml";
    public static final String SERVER_XML_ID_STORE_MISSING_ELEMENT = "inMemoryIdStoreNoElement.xml";

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
    public static void initialSetup() throws Exception {
        // Save the server configuration before all tests.
        server.saveServerConfiguration();
    }

    @Before
    public void testSetUp() throws Exception {
        InMemoryIdentityStoreEnablementTests instance = new InMemoryIdentityStoreEnablementTests();
        logInfo("testSetUp", "Starting server setup for the test scenario...");

        // The URL is not expected to be modified during this test scope
        url = instance.buildUrl(CONTEXT_ROOT, RESOURCE_PATH);

        // Create the web application
        WebArchive app = ShrinkWrap.create(WebArchive.class,
                                           APP_NAME + ".war").addPackage("inmemory.identity.store").addAsWebInfResource(new File("test-applications/inmemory/WEB-INF/web.xml"));

        ShrinkHelper.exportDropinAppToServer(server, app, DeployOptions.SERVER_ONLY);

        instance.startServer();
    }

    @After
    public void testTearDown() throws Exception {
        server.stopServer(PRODUCTION_USE_WARNING_MSG);

        // Restore the server configuration, after each test case.
        server.restoreServerConfiguration();
    }

    /**
     * Test that the the specified in-memory identity store is only involved in the authentication work flow
     * if it is explicitly enabled in the server.xml via the new attribute allowInMemoryIdentityStores
     * under the existing webAppSecurity element.
     * A custom server configuration is used with allowInMemoryIdentityStores = true.
     * Test the log file output for in-memory store usage warning for sanity.
     */
    @Test
    public void testInMemStoreIsAllowed() throws Exception {
        logInfo("testInMemStoreIsAllowed", "Testing that in-mem identity store is enabled when having the required element in the config file");

        // Should get 200 and proceed
        executeGetRequest(url, USER_THEO, VALID_PASSWORD, 200);
        assertEquals("Warning message should appear in log once", 1, server.waitForMultipleStringsInLog(1, PRODUCTION_USE_WARNING_MSG));

        logInfo("testInMemStoreIsAllowed", "Test passed");
    }

    /**
     * Test that the specified in-memory identity store is not used during the authentication process
     * when the use of this store is not enabled via server XML configuration.
     * A custom server configuration is used with no such element specified (defaulted to false if missing)
     */
    //@Test
    public void testInMemStoreNotAllowedIfElementIsAbsent() throws Exception {
        logInfo("testInMemStoreNotAllowedIfElementIsAbsent", "Testing that in-mem identity store is not allowed when element is missing");

        // Replace the server configuration file with the test case
        updateServerConfiguration(SERVER_XML_ID_STORE_MISSING_ELEMENT);

        // Should get 401 since in-memory id-store is not involved in authentication
        executeGetRequest(url, USER_THEO, VALID_PASSWORD, 401);

        logInfo("testInMemStoreNotAllowedIfElementIsAbsent", "Test passed");
    }

    /**
     * Test that the specified in-memory identity store is not used during the authentication process
     * when the use of this store is not enabled via server XML configuration.
     * A custom server configuration is used with allowInMemoryIdentityStores = false.
     */
    //@Test
    public void testInMemStoreCustomConfigIsNotAllowed() throws Exception {
        logInfo("testInMemStoreCustomConfigIsNotAllowed", "Testing that in-mem identity store is not allowed when element is set to false");

        // Replace the server configuration file with the test case
        updateServerConfiguration(SERVER_XML_ID_STORE_DISABLED);

        // Should get 401 since in-memory id-store is not involved in authentication
        executeGetRequest(url, USER_JASMINE, VALID_PASSWORD, 401);

        // Check that no unexpected error messages appear
        // We expect the warning message, but no error messages
//        String logContent = waitForStringInLog("CWWKS35", 2000);
//
//        if (logContent != null) {
//            // If we found CWWKS35xx messages, make sure they're only the expected warning
//            assertTrue("Should only see warning message, not errors",
//                       logContent.contains(PRODUCTION_USE_WARNING_MSG));
//        }

        logInfo("testInMemStoreCustomConfigIsNotAllowed", "Test passed");
    }

    /**
     * Update the server configuration with a specified file
     * And assert successful completion
     *
     * @param fileName
     * @throws Exception if the update fails
     */
    private void updateServerConfiguration(String fileName) throws Exception {
        String updatedMessage = setServerConfig(fileName, server.getConsoleLogFile(), server);
        assertNotNull("The server config change was not completed",
                      updatedMessage);
    }

    /**
     * Update the server configuration with a specified file
     * Wait for message that indicates the config change
     *
     * @param fileName the name of the custom configuration file
     * @param logFile
     * @param server
     * @return the matching line in the log, or null if no matches
     *         appear before the timeout expires
     * @throws Exception
     */
    private static String setServerConfig(String fileName, RemoteFile logFile, LibertyServer server) throws Exception {
        server.setMarkToEndOfLog(logFile);
        server.setServerConfigurationFile(fileName);
        return server.waitForStringInLogUsingMark(SERVER_CONFIG_UPDATE_MESSAGES_REGEX);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        // Expected warnings and errors during testing
        if (server != null && server.isStarted()) {
            server.stopServer();
        }
    }
}
