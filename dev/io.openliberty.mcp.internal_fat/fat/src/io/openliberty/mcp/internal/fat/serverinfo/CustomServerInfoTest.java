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
import componenttest.topology.impl.LibertyServer;
import io.openliberty.mcp.internal.fat.tool.basicToolApp.BasicTools;
import io.openliberty.mcp.internal.fat.utils.McpClient;

/**
 * Test that verifies custom mcpServerInfo configuration is properly applied
 * when specified in server.xml
 */
@RunWith(FATRunner.class)
public class CustomServerInfoTest {

    @Rule
    public McpClient client = new McpClient(server, "/customServerInfoTest");

    @Server("mcp-server-custom-info")
    public static LibertyServer server;

    @BeforeClass
    public static void setup() throws Exception {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "customServerInfoTest.war")
                                   .addPackage(BasicTools.class.getPackage());

        ShrinkHelper.exportAppToServer(server, war, SERVER_ONLY);

        server.startServer();
    }

    @AfterClass
    public static void teardown() throws Exception {
        server.stopServer();
    }

    @Test
    public void testCustomServerInfoInInitializeResponse() throws Exception {
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

        // Verify custom serverInfo values from server.xml
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
                              "name": "my-custom-server",
                              "title": "My Custom MCP Server",
                              "version": "2.5.0",
                              "description": "My custom description"
                            }
                          }
                        }
                        """;

        JSONAssert.assertEquals(expectedResponse, response, JSONCompareMode.STRICT);
    }

}