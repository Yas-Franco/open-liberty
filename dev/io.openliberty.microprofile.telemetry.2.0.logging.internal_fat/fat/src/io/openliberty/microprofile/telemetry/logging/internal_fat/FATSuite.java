/*******************************************************************************
 * Copyright (c) 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.microprofile.telemetry.logging.internal_fat;

import static org.junit.Assert.assertNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.ibm.websphere.simplicity.RemoteFile;

import componenttest.custom.junit.runner.RepeatTestFilter;
import componenttest.rules.repeater.MicroProfileActions;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.HttpUtils;
import io.openliberty.microprofile.telemetry.internal_fat.shared.TelemetryActions;

@RunWith(Suite.class)
@SuiteClasses({
                TelemetryMessagesTest.class,
                TelemetryMessagesCheckpointTest.class,
                TelemetryFFDCTest.class,
                TelemetryFFDCCheckpointTest.class,
                TelemetryTraceTest.class,
                TelemetryTraceCheckpointTest.class,
                TelemetrySourcesTest.class,
                TelemetryApplicationConfigTest.class,
                TelemetryDropinsTest.class
})

public class FATSuite {

    private static final int CONN_TIMEOUT = 10;

    static void hitWebPage(LibertyServer server, String contextRoot, String servletName, boolean failureAllowed,
                           String params) throws MalformedURLException, IOException, ProtocolException, InterruptedException {
        try {
            String urlStr = "http://" + server.getHostname() + ":" + server.getHttpDefaultPort() + "/" + contextRoot + "/" + servletName;
            urlStr = params != null ? urlStr + params : urlStr;
            URL url = new URL(urlStr);
            int expectedResponseCode = failureAllowed ? HttpURLConnection.HTTP_INTERNAL_ERROR : HttpURLConnection.HTTP_OK;
            HttpURLConnection con = HttpUtils.getHttpConnection(url, expectedResponseCode, CONN_TIMEOUT);
            BufferedReader br = HttpUtils.getConnectionStream(con);
            String line = br.readLine();
            // Make sure the server gave us something back
            assertNotNull(line);
            con.disconnect();
        } catch (IOException e) {
            // A message about a 500 code may be fine
            if (!failureAllowed) {
                throw e;
            }
        }
    }

    public static String getTelemetryVersionUnderTest() {
        if (RepeatTestFilter.isRepeatActionActive(MicroProfileActions.MP60_ID)) {
            return "1.0";
        } else if (RepeatTestFilter.isAnyRepeatActionActive(MicroProfileActions.MP70_EE11_ID, MicroProfileActions.MP70_EE10_ID, TelemetryActions.MP61_MPTEL20_ID,
                                                            TelemetryActions.MP50_MPTEL20_JAVA8_ID,
                                                            TelemetryActions.MP50_MPTEL20_ID, TelemetryActions.MP41_MPTEL20_ID, TelemetryActions.MP14_MPTEL20_ID)) {
            return "2.0";
        } else if (RepeatTestFilter.isAnyRepeatActionActive(MicroProfileActions.MP71_EE11_ID, MicroProfileActions.MP71_EE10_ID, TelemetryActions.MP50_MPTEL21_JAVA8_ID,
                                                            TelemetryActions.MP50_MPTEL21_ID, TelemetryActions.MP41_MPTEL21_ID, TelemetryActions.MP14_MPTEL21_ID)) {
            return "2.1";
        } else {
            return "1.1";
        }
    }

    public static void setConfig(LibertyServer server, RemoteFile logFile, String fileName, String appName) throws Exception {
        String version = getTelemetryVersionUnderTest();
        if (version.equals("2.1")) { //MpTelemetry version 2.1
            Path pathToFile = Paths.get(server.pathToAutoFVTTestFiles, fileName);
            Charset charset = StandardCharsets.UTF_8;
            String content = new String(Files.readAllBytes(pathToFile), charset);
            content = content.replaceAll("mpTelemetry-2.0", "mpTelemetry-2.1");
            content = content.replaceAll("restfulWS-3.0", "restfulWS-4.0");
            content = content.replaceAll("componenttest-1.0", "componenttest-2.0");

            Files.write(pathToFile, content.getBytes(charset));
        }

        server.setMarkToEndOfLog(logFile);
        server.setServerConfigurationFile(fileName);
        server.waitForConfigUpdateInLogUsingMark(Collections.singleton(appName), new String[] {});
    }
}