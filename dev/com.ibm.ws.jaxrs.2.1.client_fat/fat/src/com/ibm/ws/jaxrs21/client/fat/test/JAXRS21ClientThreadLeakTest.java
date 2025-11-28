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
package com.ibm.ws.jaxrs21.client.fat.test;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.websphere.simplicity.log.Log;

import componenttest.annotation.Server;
import componenttest.annotation.SkipForRepeat;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.HttpUtils;

@RunWith(FATRunner.class)
@SkipForRepeat({SkipForRepeat.EE9_FEATURES, SkipForRepeat.EE10_FEATURES, SkipForRepeat.EE11_FEATURES})
public class JAXRS21ClientThreadLeakTest extends JAXRS21AbstractTest {

    @Server("jaxrs21.client.JAXRS21ClientThreadLeakTest")
    public static LibertyServer server;

    private static final String clientcallbackwar = "jaxrs21clientthreadleak";

    private final static String target = "jaxrs21clientthreadleak/JAXRS21ClientTestServlet";

    @BeforeClass
    public static void setup() throws Exception {

        ShrinkHelper.defaultDropinApp(server, clientcallbackwar,
                                      "com.ibm.ws.jaxrs21.client.threadleak.client",
                                      "com.ibm.ws.jaxrs21.client.threadleak.server");

        // Make sure we don't fail because we try to start an
        // already started server
        try {
            server.startServer(true);
        } catch (Exception e) {
            System.out.println(e.toString());
        }

    }

    @AfterClass
    public static void tearDown() throws Exception {
        server.stopServer("CWWKE1102W");
    }

    @Before
    public void preTest() {
        serverRef = server;
    }

    @After
    public void afterTest() {
        serverRef = null;
    }

   /**
     * Test that verifies thread count does not continuously increase when making
     * repeated JAX-RS client requests that fail due to connection timeouts.
     * To test the fix CXF-9171 added by cxf team for thread leak
     * This test:
     * 1. Gets the initial thread count from metrics
     * 2. Makes multiple requests to non-routable IP addresses (simulating connection failures)
     * 3. Waits for cleanup to occur
     * 4. Verifies that thread count has not increased significantly
     */
    @Test
    public void testThreadLeakWithFailedConnections() throws Exception {
        Class<?> c = this.getClass();
        
        // Get initial thread count
        int initialThreadCount = getThreadCount();
        System.out.println("Initial thread count: " + initialThreadCount);
        
        // Make multiple requests that will fail (non-routable IP)
        Map<String, String> params = new HashMap<String, String>();
        params.put("param", "testThreadLeak");
        params.put("iterations", "100"); 
        
        this.runTestOnServer(target, "testThreadLeak", params, "Thread leak test completed");
        
        // Get final thread count
        int finalThreadCount = getThreadCount();
        System.out.println("Final thread count: " + finalThreadCount);
        
        // Calculate the increase
        int threadIncrease = finalThreadCount - initialThreadCount;
        System.out.println("Thread count increase: " + threadIncrease);
        
        // Thread count should not increase by more than a reasonable amount (e.g., 10 threads)
        // Some increase is acceptable due to normal server operations
        assertTrue("Thread count increased by " + threadIncrease + " which exceeds acceptable threshold of 10",
                   threadIncrease <= 10);
    }

    /**
     * Helper method to get the current thread count from the server's metrics endpoint.
     */
    private int getThreadCount() throws Exception {
        String metricsUrl = "http://" + server.getHostname() + ":" + 
                           server.getHttpDefaultPort() + "/metrics/base";
        
        HttpURLConnection con = HttpUtils.getHttpConnection(new URL(metricsUrl), 
                                                            HttpURLConnection.HTTP_OK, 10);
        BufferedReader br = HttpUtils.getConnectionStream(con);
        String line;
        int threadCount = -1;
        
        while ((line = br.readLine()) != null) {
            if (line.contains("base_thread_count") && !line.startsWith("#")) {
                // Parse the thread count from the metrics output
                // Format: base_thread_count 123
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    threadCount = Integer.parseInt(parts[1]);
                    break;
                }
            }
        }
        br.close();
        
        if (threadCount == -1) {
            throw new Exception("Could not find base_thread_count in metrics");
        }
        
        return threadCount;
    }
}
