/*******************************************************************************
 * Copyright (c) 2019, 2026 IBM Corporation and others.
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
package com.ibm.ws.jaxws.fat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.websphere.simplicity.log.Log;

import componenttest.annotation.Server;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.HttpUtils;

/**
 * This case is added for service change work item 87104.
 * When MTOM is enabled on service provider, if client use Service.create(QName serviceName)
 * to create service and there is no need to add
 * service.addPort(portName, SOAPBinding.SOAP11HTTP_MTOM_BINDING, mtom11URL) statement.
 *
 * Additional tests have been added for testing SOAPAction headers when set on the request and the ability for an MTOM enabled
 * Web Service Endpoint to handle them depending on if allowing for mismatching Actions are set in the server.xml configuration
 */
@RunWith(FATRunner.class)
//@Mode(TestMode.FULL)
public class AttachmentPropertiesTest {
    private static final int REQUEST_TIMEOUT = 10;

    private static final String APP_NAME = "AttachmentProperties";

    private static final String SERVLET_PATH = "/" + APP_NAME + "/AttachmentPropertiesTestServlet?file=catalogfiles";

    @Server("AttachmentPropertiesServer")
    public static LibertyServer server;

    @Rule
    public TestName testName = new TestName();

    @BeforeClass
    public static void setUp() throws Exception {
        WebArchive defaultApp = ShrinkHelper.buildDefaultApp(APP_NAME, "com.ibm.jaxws.attachment.mtom", "com.ibm.jaxws.attachment.servlet");
        defaultApp.addAsWebInfResource(new File("lib/LibertyFATTestFiles/AttachmentPropertiesTest/soaphandlers.xml"), "classes/com/ibm/jaxws/attachment/mtom/soaphandlers.xml");
        ShrinkHelper.exportAppToServer(server, defaultApp);

        server.startServer("AttachmentProperties.log");

        // Pause for application to start successfully
        server.waitForStringInLog("CWWKZ0001I.*" + APP_NAME);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (server != null && server.isStarted()) {
            server.stopServer("SRVE0777E", "SRVE0315E");
        }

    }

    /**
     * Negative test for 0KB return
     *
     * @throws IOException
     */
    @Test
    public void testNegative0KbDataSourceReturn() throws Exception {
        server.reconfigureServer("AttachmentPropertiesTest/noproperty-server.xml", "CWWKG0017I");

        String servletURL = new StringBuilder("http://").append(server.getHostname()).append(":").append(server.getHttpDefaultPort()).append(SERVLET_PATH).toString();

        List<String> expectedResponses = new ArrayList<String>(1);
        expectedResponses.add("FAILED: DataSource size is 0");
        assertFalse(printExpectedResponses(expectedResponses, false, true), checkExpectedResponses(servletURL, expectedResponses, true, HttpURLConnection.HTTP_OK));
    }

    /**
     * Positive test for 0KB return
     *
     * @throws IOException
     */
    @Test
    public void testPositiveAttachmentProperties() throws Exception {
        server.reconfigureServer("AttachmentPropertiesTest/ibm-hold-temp-files-server.xml", "CWWKG0017I");

        String servletURL = new StringBuilder("http://").append(server.getHostname()).append(":").append(server.getHttpDefaultPort()).append(SERVLET_PATH).toString();

        List<String> expectedResponses = new ArrayList<String>(1);
        expectedResponses.add("PASSED");
        assertTrue(printExpectedResponses(expectedResponses, false, false), checkExpectedResponses(servletURL, expectedResponses, true, HttpURLConnection.HTTP_OK));

        String propertyValues = server.waitForStringInTrace("ibm-hold-temp-files=true");
        assertNotNull("ibm-hold-temp-files endpoint property is not set properly!", propertyValues.contains("ibm-hold-temp-files=true"));
        assertNotNull("attachment-directory endpoint property is not set properly!", propertyValues.contains("attachment-directory=tmp"));
        assertNotNull("attachment-memory-threshold endpoint property is not set properly!", propertyValues.contains("attachment-memory-threshold=50000"));
        assertNotNull("Temporary file and stream are NOT set to hold from removal!",
                      server.waitForStringInTrace("attachmentOperation : Temporary file and stream are set to hold from removal."));
        assertNotNull("Temporary file and stream holds are NOT released",
                      server.waitForStringInTrace("attachmentOperation : Temporary file and stream holds are released. They will be removed."));
    }

    private boolean checkExpectedResponses(String servletUrl, List<String> expectedResponses, boolean exact, int responseCode) throws IOException {
        Log.info(this.getClass(), testName.getMethodName(), "Calling Application with URL=" + servletUrl);

        try {
            HttpURLConnection con = HttpUtils.getHttpConnection(new URL(servletUrl), responseCode, REQUEST_TIMEOUT);
            StringBuilder sb = new StringBuilder();
            BufferedReader br = null;
            try {
                br = HttpUtils.getConnectionStream(con);
                String line = br.readLine();
                while (line != null) {
                    sb.append(line);
                    line = br.readLine();
                }
            } finally {
                if (br != null) {
                    br.close();
                }
            }

            String responseContent = sb.toString();

            Log.info(AttachmentPropertiesTest.class, "checkExpectedResponses", "responseContent = " + responseContent);
            if (exact) { //the response content must contain all the expect strings
                for (String expectStr : expectedResponses) {
                    return responseContent.equals(expectStr);
                }
            } else { //the response content could contain one of the expect strings
                for (String expectStr : expectedResponses) {
                    return responseContent.contains(expectStr);
                }
            }
            return false;
        } catch (IOException e) {
            // Return true if the exception contains the expected response code
            if (e.getMessage().contains(Integer.toString(responseCode))) {
                return true;
            }
        }

        return false;
    }

    private String printExpectedResponses(List<String> expectedResponses, boolean exact, boolean negativeTest) {
        StringBuilder sb = new StringBuilder(negativeTest ? "" : "The expected output in server log is ");
        if (!exact && !negativeTest && expectedResponses.size() > 1) {
            sb.append("one of ");
        }
        sb.append("[");
        for (int i = 0; i < expectedResponses.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"" + expectedResponses.get(i) + "\"");
        }
        sb.append("]");
        return sb.toString();
    }

}
