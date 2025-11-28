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
package com.ibm.ws.jaxrs21.client.threadleak.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Test servlet that simulates the thread leak scenario by making repeated
 * JAX-RS client requests to non-routable IP addresses, causing connection
 * failures and timeouts.
 */
@WebServlet("/JAXRS21ClientTestServlet")
public class JAXRS21ClientTestServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        
        PrintWriter pw = resp.getWriter();
        
        String testMethod = req.getParameter("test");
        if (testMethod == null) {
            pw.write("no test to run");
            return;
        }
        
        runTest(testMethod, pw, req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }

    private void runTest(String testMethod, PrintWriter pw, HttpServletRequest req, HttpServletResponse resp) {
        try {
            Method testM = this.getClass().getDeclaredMethod(testMethod, new Class[] { Map.class, StringBuilder.class });
            Map<String, String> m = new HashMap<String, String>();

            Iterator itr = req.getParameterMap().keySet().iterator();

            while (itr.hasNext()) {
                String key = (String) itr.next();
                if (key.indexOf("@") == 0) {
                    m.put(key.substring(1), req.getParameter(key));
                }
            }

            m.put("serverIP", req.getLocalAddr());
            m.put("serverPort", String.valueOf(req.getLocalPort()));

            StringBuilder ret = new StringBuilder();
            testM.invoke(this, m, ret);
            pw.write(ret.toString());

        } catch (Exception e) {
            e.printStackTrace(pw);
        }
    }

    
    /**
     * Get the base_thread_count from mpMetrics endpoint.
     * @param hostname the server hostname
     * @param port the server port
     * @return the base_thread_count value, or -1 if unable to retrieve
     */
    private int getBaseThreadCountFromMetrics(String hostname, String port) {
        try {
            String metricsUrl = "http://" + hostname + ":" + port + "/metrics/base";
            System.out.println("metricsUrl: " + metricsUrl);
            URL url = new URL(metricsUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    // Look for: base_thread_count 123
                    if (line.startsWith("base_thread_count ") && !line.contains("#")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2) {
                            in.close();
                            return Integer.parseInt(parts[1]);
                        }
                    }
                }
                in.close();
            }
        } catch (Exception e) {
            System.out.println("Neena: Error fetching metrics: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Test method that makes CONCURRENT JAX-RS client requests to a slow endpoint,
     * simulating the customer's real-time scenario where requests pile up.
     * Like the sample app, this creates a singleton client and fires requests
     * without waiting for responses, causing thread buildup.
     */
    public void testThreadLeak(Map<String, String> param, StringBuilder ret) {
        String iterationsParam = param.get("iterations");
        int iterations = iterationsParam != null ? Integer.parseInt(iterationsParam) : 10;
        
        // Get initial thread count
       // int initialThreadCount = getThreadCount();
       // System.out.println("Neena: Initial thread count: " + initialThreadCount);
        
        // Get server details from parameters
        String hostname = param.get("hostname");
        String port = param.get("serverPort");
        if (hostname == null) {
            hostname = "localhost";
        }
        if (port == null) {
            port = "8010";
        }
        
        // Create a SINGLETON JAX-RS client (like the sample app) - NOT closed between requests
        Client client = ClientBuilder.newBuilder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build();
    
        int timeoutCount = 0;

        int initialBaseThreadCount = getBaseThreadCountFromMetrics(hostname, port);
        
        System.out.println("Neena: Starting SEQUENTIAL execution of 100 requests with 250ms delay...");
        
        // Make SEQUENTIAL requests with 250ms delay between each request
        for (int i = 0; i < iterations; i++) {
            try {
                // Build URL with varying student number 
                // Each student number triggers a different IP in ComputerApiRestClient
                // This causes varying URLs in the JAX-RS client, creating new Bus instances
                String endpoint = "http://" + hostname + ":" + port + "/jaxrs21clientthreadleak/Test/rest/" + i + "/data";
                // JAXRS21MyResource -> ComputerService -> ComputerApiRestClient -> RestClient
                WebTarget target = client.target(endpoint);
                Response resp = target.request(MediaType.APPLICATION_JSON).get();
                resp.close();
            } catch (Exception e) {
                // Unexpected exception
                timeoutCount++;
            }           
            // 250ms delay between requests
            try {
                Thread.sleep(250);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            
        }
    
        client.close();
        
        // Get final thread count BEFORE closing client
        // If there's a leak, threads will still be high
        int finalBaseThreadCount = getBaseThreadCountFromMetrics(hostname, port);

        // Calculate thread increase
        int threadCountIncrease = finalBaseThreadCount - initialBaseThreadCount;
        
        System.out.println("Neena: ========================================");
        System.out.println("Neena: === THREAD LEAK TEST RESULTS (SEQUENTIAL) ===");
        System.out.println("Neena: ========================================");
        System.out.println("Neena: Initial base_thread_count: " + initialBaseThreadCount);
        System.out.println("Neena: Final base_thread_count: " + finalBaseThreadCount);
        System.out.println("Neena: Thread count increase: " + threadCountIncrease);
        System.out.println("Neena: ========================================");
        
        // Return result to test
        ret.append("Thread leak test completed\n");
        
        
        // CRITICAL: If thread count increased significantly, there's a leak
        if (threadCountIncrease > 10) {
            System.out.println("Neena: ⚠️  WARNING: Thread leak detected! Threads increased by " + threadCountIncrease);
            //ret.append("LEAK DETECTED: Thread count increased by " + threadCountIncrease + "\n");
        } else {
            System.out.println("Neena: ✅ No significant thread leak detected (increase: " + threadCountIncrease + ")");
            //ret.append("No significant leak detected\n");
        }
    }
}

