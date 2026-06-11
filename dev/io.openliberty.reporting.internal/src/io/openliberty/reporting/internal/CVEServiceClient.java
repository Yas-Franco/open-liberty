/*******************************************************************************
 * Copyright (c) 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package io.openliberty.reporting.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;

/**
 * <p>
 * The Usage Metering Client makes the connection to the metering service
 * and sends Liberty usage data.
 * </p>
 */
public class CVEServiceClient {

    private static final TraceComponent tc = Tr.register(CVEServiceClient.class);

    /**
     * <p>
     * Sends usage data to the metering service.
     * </p>
     *
     * @param data    Map<String, String>
     * @param urlLink URL link which is set in the Server.xml
     * @throws IOException
     */
    public void retrieveCVEData(Map<String, String> data, String urlLink) throws IOException {

        String jsonData = buildJsonString(data);

        if (!urlLink.startsWith("https")) {
            throw new MalformedURLException("Invalid protocol, expected https");
        }

        URL url = new URL(urlLink);

        HttpsURLConnection connection = getConnection(url);
        if (connection != null) {
            sendData(connection, jsonData);
        }

        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(this, tc, jsonData);
        }

        // Get response code to ensure request was sent
        int responseCode = connection.getResponseCode();
        if (TraceComponent.isAnyTracingEnabled() && tc.isDebugEnabled()) {
            Tr.debug(this, tc, "Response code: " + responseCode);
        }
    }

    /**
     * <p>
     * Creates a connection with the cloud Service.
     * </p>
     *
     * @param url URL
     * @return
     * @throws IOException
     */
    private static HttpsURLConnection getConnection(URL url) throws IOException {
        HttpsURLConnection connection = null;
        connection = (HttpsURLConnection) url.openConnection();
        SSLContext sc = null;
        try {
            // Create a trust manager that does not validate certificate chains (for -k flag behavior)
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };
            
            sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new SSLHandshakeException("Issue when creating a secure connection: " + e);
        }
        connection.setSSLSocketFactory(sc.getSocketFactory());
        
        // Disable hostname verification (for -k flag behavior)
        connection.setHostnameVerifier(new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
        
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer urh3urh823u48j2eikmwd");
        connection.setDoOutput(true);

        return connection;
    }

    /**
     * <p>
     * Sends the data collected from the server to the cloud service.
     * </p>
     *
     *
     * @param connection HttpsURLConnection
     * @param jsonData   String
     * @throws ConnectException
     * @throws IOException
     */
    private static void sendData(HttpsURLConnection connection, String jsonData) throws ConnectException, IOException {
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
    }

    /**
     * <p>
     * Builds the usage metering data as a JSON string to send.
     * </p>
     *
     * <pre>
     * 	Example:
     * 			 {"productId": "a1b2c3d4e5f6g7h8", "productName": "Liberty", "productMetric": "26.0.0.4", "metricValue": "1", "productCloudpakRatio": "", "cloudpakId": "", "cloudpakName": "", "cloudpakMetric": "", "logDate": "2026-06-11"}
     * </pre>
     *
     * @param data A Map<String, String> containing the usage metering data
     * @return A JSON string representation of the data
     */
    protected String buildJsonString(Map<String, String> data) {
        if (data.isEmpty()) {
            return "{}";
        }
        StringBuilder jsonData = new StringBuilder("{");
        for (String key : data.keySet()) {
            jsonData.append("\"").append(key).append("\": \"").append(data.get(key)).append("\", ");
        }
        jsonData.setLength(jsonData.length() - 2);
        jsonData.append("}");

        return jsonData.toString();
    }

}