/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.mcp.internal.fat.serverinfo;

import static com.ibm.websphere.simplicity.ShrinkHelper.DeployOptions.SERVER_ONLY;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import com.ibm.websphere.simplicity.ShrinkHelper;

import componenttest.annotation.Server;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import io.openliberty.mcp.internal.fat.tool.basicToolApp.BasicTools;
import io.openliberty.mcp.internal.fat.utils.McpClient;

/**
 * Test that verifies partial/null serverInfo fields are handled gracefully without NPE.
 * This test uses a server configuration with only the 'name' field set, leaving
 * version as the default value. Title and description are unset.
 */
@RunWith(FATRunner.class)
public class PartialServerInfoTest {

    private static final Class<?> c = PartialServerInfoTest.class;

    @Rule
    public McpClient client = new McpClient(server, "/partialServerInfoTest");

    @Server("mcp-server-partial-info")
    public static LibertyServer server;

    @BeforeClass
    public static void setup() throws Exception {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "partialServerInfoTest.war")
                                   .addPackage(BasicTools.class.getPackage());

        ShrinkHelper.exportAppToServer(server, war, SERVER_ONLY);

        server.startServer();
    }

    @AfterClass
    public static void teardown() throws Exception {
        server.stopServer();
    }

    /**
     * Test that verifies partial serverInfo with defaults applied correctly:
     * name: "partial-server" (explicitly configured)
     * version: "1.0.0" (default as not explicitly configured)
     * title and description are optional values and are not expected as they were not explicitly configured
     */
    @Test
    @Mode(TestMode.FULL)
    public void testPartialServerInfoHandling() throws Exception {
        String request = """
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "method": "initialize",
                          "params": {
                            "protocolVersion": "2025-11-25",
                            "clientInfo": {
                              "name": "test-client",
                              "version": "1.0"
                            },
                            "capabilities": {}
                          }
                        }
                        """;

        String response = client.callMCP(request);

        // Verify partial serverInfo with defaults applied correctly:
        String expectedResponse = """
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "result": {
                            "protocolVersion": "2025-11-25",
                            "capabilities": {
                              "tools": {
                                "listChanged": false
                              }
                            },
                            "serverInfo": {
                              "name": "partial-server",
                              "version": "1.0.0"
                            }
                          }
                        }
                        """;

        JSONAssert.assertEquals(expectedResponse, response, JSONCompareMode.STRICT);
    }

}