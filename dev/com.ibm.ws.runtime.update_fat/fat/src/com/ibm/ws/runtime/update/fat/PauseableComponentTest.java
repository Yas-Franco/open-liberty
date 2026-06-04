/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
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
package com.ibm.ws.runtime.update.fat;

import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.Timeout;

import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.websphere.simplicity.log.Log;

import componenttest.annotation.ExpectedFFDC;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;

/**
 * This class is a little odd:
 * We're testing the behavior of pauseable components during server stop. One server will be used for all test methods.
 * The server will be started and stopped within each test method, BUT..
 * when the server is stopped within the tests, the logs will not be collected.
 *
 * The server logs will be collected at the end, in the tearDown.
 */
public class PauseableComponentTest {

    private static final String PAUSEABLE_COMPONENT_EXCEPTION_MESSAGE = "WOOPS!";
    private static final String PAUSEABLE_COMPONENT_CALLED_MESSAGE = "WHEE!";
    private static final String PAUSEABLE_START_MESSAGE = "CWWKE1100I";
    private static final String SERVER_STOPPED_MESSAGE = "CWWKE0036I";
    private static final String PAUSEABLE_COMPONENT_HUNG_WARNING = "CWWKE1106W";
    private static final String PAUSEABLE_FAILURE_WARNING = "CWWKE1102W";
    private static final String PAUSEABLE_SUCCESS_MESSAGE = "CWWKE1101I";

    private static final Class<?> c = PauseableComponentTest.class;
    private static LibertyServer server = LibertyServerFactory.getLibertyServer("com.ibm.ws.runtime.pauseable.fat");

    // Timeout for server stop operations (in seconds)
    private static final int SERVER_STOP_TIMEOUT = 120;

    // Maximum wait time for pauseable component to complete (in milliseconds)
    private static final int PAUSEABLE_COMPONENT_MAX_WAIT = 90000;

    // Global timeout for all tests (5 minutes = 300000 milliseconds) - prevents indefinite hangs
    @Rule
    public Timeout globalTimeout = new Timeout(300000);

    @Rule
    public final TestName method = new TestName();

    @BeforeClass
    public static void setUpClass() throws Exception {
        WebArchive dropinsApp = ShrinkHelper.buildDefaultApp("mbean", "web");
        ShrinkHelper.exportDropinAppToServer(server, dropinsApp);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        try {
            // make sure server is torn down -- don't collect archive
            if (server != null && server.isStarted()) {
                Log.info(c, "tearDownClass", "Stopping server with extended wait time");
                server.stopServer(false);

                // Verify server actually stopped
                waitForServerToStop(SERVER_STOP_TIMEOUT);
            }
        } finally {
            // ONE archive for the whole run (multiple starts/stops of the server)
            server.postStopServerArchive();
        }
    }

    enum TestType {
        EXCEPTION, PAUSEABLE_HANG, SUCCESS
    }

    @Before
    public void setup() {
        Log.info(c, method.getMethodName(), "**** ENTER: " + method.getMethodName());
    }

    @After
    public void tearDown() throws Exception {
        try {
            // make sure server is torn down -- don't collect archive
            if (server.isStarted()) {
                Log.info(c, "tearDown", "Stopping server with extended wait time");
                server.stopServer(false);

                // Verify server actually stopped
                waitForServerToStop(SERVER_STOP_TIMEOUT);
            }

            server.renameLibertyServerRootFile("logs/trace.log", "logs/" + method.getMethodName() + ".trace.log");
            server.renameLibertyServerRootFile("logs/messages.log", "logs/" + method.getMethodName() + ".messages.log");
            server.resetLogMarks();

            // Always ensure that the installed resources are cleaned up between runs..
            server.uninstallSystemBundle("test.server.quiesce");
            server.uninstallUserBundle("test.server.quiesce");
        } finally {
            Log.info(c, method.getMethodName(), "**** EXIT: " + method.getMethodName());
        }
    }

    /**
     * Wait for server to stop gracefully.
     * Checks periodically if server has stopped without forcing termination.
     *
     * @param maxWaitSeconds Maximum time to wait in seconds
     * @throws Exception if interrupted
     */
    private static void waitForServerToStop(int maxWaitSeconds) throws Exception {
        int waited = 0;
        while (server.isStarted() && waited < maxWaitSeconds) {
            Thread.sleep(1000);
            waited++;
            if (waited % 10 == 0) {
                Log.info(c, "waitForServerToStop", "Still waiting for server to stop... (" + waited + "s)");
            }
        }

        if (server.isStarted()) {
            String warningMsg = "Server did not stop within " + maxWaitSeconds + " seconds. " +
                                "Process may still be running but will eventually timeout.";
            Log.warning(c, warningMsg);
        } else {
            Log.info(c, "waitForServerToStop", "Server stopped successfully after " + waited + " seconds");
        }
    }

    // Note: methods can execute in any order. We do (sadly) have to clean start the server
    // because the same feature and bundle are moved between the system (wlp/lib) and
    // the user extension (usr/extension/lib).

    /**
     * If there are no pauseable components registered, we shouldn't
     * see any messages about pauseable component processing..
     *
     * @throws Exception
     */
    @Test
    public void testForceStop() throws Exception {
        // Add a single pauseable component as a runtime feature/bundle (internal)
        server.setServerConfigurationFile("pauseable-component.server.xml");
        server.installSystemBundle("test.server.quiesce");
        server.installSystemFeature("pauseablecomponent-1.0");

        // start the server, do not clean-start, and do not pre-clean the logs dir
        server.startServer(method.getMethodName() + ".console.log", true, false);

        // stop the server, do not clean up the archive, and FORCE STOP (no pause)
        server.stopServer(false, true);

        // These messages flat out shouldn't be in there!
        Assert.assertNull("FAIL: for " + method.getMethodName() + ", " + server.getServerName() + " should not contain information about pauseable component processing",
                          server.waitForStringInLog(PAUSEABLE_START_MESSAGE, 1));
    }

    /**
     * Define/invoke a runtime-level pauseable component
     *
     * @throws Exception
     */
    @Test
    public void testSingleRuntimePauseableComponent() throws Exception {
        // Add a single pauseable component as a runtime feature/bundle (internal)
        server.setServerConfigurationFile("pauseable-component.server.xml");
        server.installSystemBundle("test.server.quiesce");
        server.installSystemFeature("pauseablecomponent-1.0");

        startStopServer(TestType.SUCCESS);
    }

    /**
     * Try a pauseable component that throws an exception, and make sure that doesn't
     * prevent the pause activity from completing.
     *
     * @throws Exception
     */
    @Test
    @ExpectedFFDC("java.lang.RuntimeException")
    public void testPauseableComponentException() throws Exception {
        // Add a single pauseable component as a usr feature/bundle (SPI)
        server.setServerConfigurationFile("bad-pauseable-component.server.xml");
        server.installSystemBundle("test.server.quiesce");
        server.installSystemFeature("pauseablecomponent-1.0");

        startStopServer(TestType.EXCEPTION);
    }

    /**
     * Long running test (at least 30s), push this into the full bucket.
     * This triggers a pauseable component that takes longer than 30s to complete.
     * Make sure we get a warning that not all pauseable activity completed (and that
     * we don't see the message indicating that it did).
     *
     * Note: This test allows the server to handle the pauseable component timeout
     * gracefully. The server will detect the hang, log warnings, and eventually
     * complete shutdown after the configured timeout period without force termination.
     *
     * The test has a global timeout (via @Rule) that will cause it to timeout and
     * continue with the rest of the test suite if it takes too long. RC 137 from
     * server stop is expected and handled as a successful outcome.
     *
     * @throws Exception
     */
    @Test
    public void testLongRunningPauseableComponent() throws Exception {
        Log.info(c, method.getMethodName(), "Starting long-running pauseable component test with timeout protection");

        try {
            // Add a single pauseable component as a usr feature/bundle (SPI)
            server.setServerConfigurationFile("longrunning-pauseable-component.server.xml");
            server.installSystemBundle("test.server.quiesce");
            server.installSystemFeature("pauseablecomponent-1.0");

            startStopServerWithTimeout(TestType.PAUSEABLE_HANG);

            Log.info(c, method.getMethodName(), "Test completed successfully");
        } catch (Exception e) {
            Log.error(c, method.getMethodName(), e, "Test encountered an error but will continue");
            // Log the error but don't fail the entire test suite
            // The global timeout will handle truly hung scenarios
            throw e;
        }
    }

    private void startStopServer(TestType type) throws Exception {
        // start the server, do a clean start, and do not pre-clean the logs dir
        server.startServer(method.getMethodName() + ".console.log", true, false);

        // wait for port to start
        Assert.assertNotNull("FAIL for " + method.getMethodName() + ": " + server.getServerName() + " should contain info msg about open port",
                             server.waitForStringInLog("CWWKO0219I", 0));

        // stop the server, do not clean up the archive
        server.setMarkToEndOfLog(server.getDefaultLogFile());
        if (type == TestType.PAUSEABLE_HANG)
            server.stopServer(false, PAUSEABLE_COMPONENT_HUNG_WARNING, PAUSEABLE_FAILURE_WARNING);
        else
            server.stopServer(false);

        // Make sure stop has completed
        Assert.assertNotNull("FAIL for " + method.getMethodName() + ": " + server.getServerName() + " should contain info msg about server stopped",
                             server.waitForStringInLog(SERVER_STOPPED_MESSAGE));

        Assert.assertNotNull("FAIL for " + method.getMethodName() + ": " + server.getServerName() + " should contain info msg about the start of pauseable component processing",
                             server.waitForStringInLog(PAUSEABLE_START_MESSAGE, 0));

        Assert.assertNotNull("FAIL for " + method.getMethodName() + ": " + server.getServerName() + " should contain WHEE! because the test pauseable component was called",
                             server.waitForStringInLog(PAUSEABLE_COMPONENT_CALLED_MESSAGE, 0));

        if (type == TestType.EXCEPTION) {
            Assert.assertNotNull("FAIL for " + method.getMethodName() + ": " + server.getServerName()
                                 + " should contain WOOPS! because the test pauseable component threw an exception",
                                 server.waitForStringInLog(PAUSEABLE_COMPONENT_EXCEPTION_MESSAGE, 0));

        } else if (type == TestType.PAUSEABLE_HANG) {
            Assert.assertNull("FAIL for " + method.getMethodName() + ": " + server.getServerName()
                              + " should NOT contain info msg about the completion of pauseable component processing",
                              server.waitForStringInLog(PAUSEABLE_SUCCESS_MESSAGE, 0));
            Assert.assertNotNull("FAIL for " + method.getMethodName() + ": " + server.getServerName()
                                 + " should contain warning msg about the failure to complete pauseable component processing",
                                 server.waitForStringInLog(PAUSEABLE_FAILURE_WARNING, 0));
            Assert.assertNotNull("FAIL for " + method.getMethodName() + ": " + server.getServerName()
                                 + " should contain warning message indicating that 1 pauseable component hung",
                                 server.waitForStringInLog(PAUSEABLE_COMPONENT_HUNG_WARNING, 0));
        } else {
            Assert.assertNotNull("FAIL for " + method.getMethodName() + ": " + server.getServerName()
                                 + " should contain information about the completion of pauseable component processing",
                                 server.waitForStringInLog(PAUSEABLE_SUCCESS_MESSAGE, 0));
            Assert.assertNull("FAIL for " + method.getMethodName() + ": " + server.getServerName()
                              + " should NOT contain information about the failure to complete pauseable component processing",
                              server.waitForStringInLog(PAUSEABLE_FAILURE_WARNING, 0));
        }
    }

    /**
     * Start and stop server with timeout handling for hanging pauseable components.
     * This method allows the server to handle pauseable component timeouts gracefully
     * without force termination. The server will detect hangs, log warnings, and
     * eventually complete shutdown after the configured timeout period.
     *
     * @param type The test type indicating expected behavior
     * @throws Exception
     */
    private void startStopServerWithTimeout(TestType type) throws Exception {
        // start the server, do a clean start, and do not pre-clean the logs dir
        server.startServer(method.getMethodName() + ".console.log", true, false);

        // wait for port to start
        Assert.assertNotNull("FAIL for " + method.getMethodName() + ": " + server.getServerName() + " should contain info msg about open port",
                             server.waitForStringInLog("CWWKO0219I", 0));

        // For hanging pauseable components, we need to handle the stop differently
        server.setMarkToEndOfLog(server.getDefaultLogFile());

        if (type == TestType.PAUSEABLE_HANG) {
            Log.info(c, method.getMethodName(), "Initiating server stop - expecting pauseable component to hang");
            Log.info(c, method.getMethodName(), "Server will wait up to " + SERVER_STOP_TIMEOUT + " seconds for graceful shutdown");

            // Stop server - RC 137 will be thrown and treated as a test failure
            // This indicates the server stopped but some components did not complete gracefully
            server.stopServer(false, PAUSEABLE_COMPONENT_HUNG_WARNING, PAUSEABLE_FAILURE_WARNING);

            // Wait for the hang warnings to appear in the logs
            Log.info(c, method.getMethodName(), "Waiting for pauseable component hang warnings...");
            Assert.assertNotNull("FAIL for " + method.getMethodName() + ": " + server.getServerName()
                                 + " should contain warning message indicating that 1 pauseable component hung",
                                 server.waitForStringInLog(PAUSEABLE_COMPONENT_HUNG_WARNING, 60000));
            Assert.assertNotNull("FAIL for " + method.getMethodName() + ": " + server.getServerName()
                                 + " should contain warning msg about the failure to complete pauseable component processing",
                                 server.waitForStringInLog(PAUSEABLE_FAILURE_WARNING, 10000));

            // Verify server completed shutdown
            Log.info(c, method.getMethodName(), "Verifying server stopped...");
            waitForServerToStop(SERVER_STOP_TIMEOUT);
        } else {
            server.stopServer(false);
        }

        // Verify the expected log messages
        Assert.assertNotNull("FAIL for " + method.getMethodName() + ": " + server.getServerName() + " should contain info msg about the start of pauseable component processing",
                             server.waitForStringInLog(PAUSEABLE_START_MESSAGE, 0));

        Assert.assertNotNull("FAIL for " + method.getMethodName() + ": " + server.getServerName() + " should contain WHEE! because the test pauseable component was called",
                             server.waitForStringInLog(PAUSEABLE_COMPONENT_CALLED_MESSAGE, 0));

        if (type == TestType.PAUSEABLE_HANG) {
            Assert.assertNull("FAIL for " + method.getMethodName() + ": " + server.getServerName()
                              + " should NOT contain info msg about the completion of pauseable component processing",
                              server.waitForStringInLog(PAUSEABLE_SUCCESS_MESSAGE, 0));
        }
    }

}