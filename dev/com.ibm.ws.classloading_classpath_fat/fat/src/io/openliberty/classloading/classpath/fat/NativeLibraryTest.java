/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.classloading.classpath.fat;

import static io.openliberty.classloading.classpath.fat.FATSuite.NATIVE_LIBRARY_TEST_SERVER;
import static io.openliberty.classloading.classpath.fat.FATSuite.TEST_LIB1_JAR;
import static io.openliberty.classloading.classpath.fat.FATSuite.TEST_LIB2_JAR;
import static io.openliberty.classloading.classpath.fat.FATSuite.TEST_NATIVE_LIBRARY_APP;
import static io.openliberty.classloading.classpath.fat.FATSuite.TEST_NATIVE_LIBRARY_WAR;

import java.io.File;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.websphere.simplicity.ShrinkHelper.DeployOptions;
import com.ibm.websphere.simplicity.config.ConfigElementList;
import com.ibm.websphere.simplicity.config.Library;
import com.ibm.websphere.simplicity.config.Path;
import com.ibm.websphere.simplicity.config.ServerConfiguration;

import componenttest.annotation.Server;
import componenttest.annotation.TestServlet;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.FATServletClient;
import io.openliberty.nativelib.test.app.NativeLibraryTestServlet;

/**
 *
 */
@RunWith(FATRunner.class)
public class NativeLibraryTest extends FATServletClient {

    @Server(NATIVE_LIBRARY_TEST_SERVER)
    @TestServlet(servlet = NativeLibraryTestServlet.class, contextRoot = TEST_NATIVE_LIBRARY_APP)
    public static LibertyServer server;

    @BeforeClass
    public static void setupTestServer() throws Exception {
        ShrinkHelper.exportAppToServer(server, TEST_NATIVE_LIBRARY_WAR, DeployOptions.SERVER_ONLY);

        ShrinkHelper.exportToServer(server, "/lib1", TEST_LIB1_JAR, DeployOptions.SERVER_ONLY);
        ShrinkHelper.exportToServer(server, "/lib2", TEST_LIB2_JAR, DeployOptions.SERVER_ONLY);

        ServerConfiguration serverConfiguration = server.getServerConfiguration();
        ConfigElementList<Library> libraries = serverConfiguration.getLibraries();

        File serverRoot = new File(server.getServerRoot());
        File lib1Dir = new File(serverRoot, "lib1");
        lib1Dir.mkdirs();
        File lib2Dir = new File(serverRoot, "lib2");
        lib2Dir.mkdirs();

        String privateNativeFileName = System.mapLibraryName("privateNative");
        new File(lib1Dir, privateNativeFileName).createNewFile();
        Library privateLibrary = libraries.get(0);
        Path privateLibraryNative = new Path();
        privateLibraryNative.setName("lib1/" + privateNativeFileName);
        privateLibrary.getPaths().add(privateLibraryNative);

        String commonNativeFileName = System.mapLibraryName("commonNative");
        new File(lib2Dir, commonNativeFileName).createNewFile();
        Library commonLibrary = libraries.get(1);
        Path commonLibraryNative = new Path();
        commonLibraryNative.setName("lib2/" + commonNativeFileName);
        commonLibrary.getPaths().add(commonLibraryNative);

        server.updateServerConfiguration(serverConfiguration);
        server.startServer();
    }

    @AfterClass
    public static void stopServer() throws Exception {
        server.stopServer();
    }
}
