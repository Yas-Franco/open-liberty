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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.ibm.websphere.kernel.server.ServerInfoMBean;
import com.ibm.ws.kernel.boot.internal.KernelUtils;
import com.ibm.ws.kernel.feature.FeatureProvisioner;
import com.ibm.ws.kernel.feature.FixManager;
import com.ibm.ws.kernel.productinfo.ProductInfo;

import io.openliberty.reporting.internal.utils.HashUtils;

/**
 * <p>
 * Collects the required data from the kernel to pass to the cloud service to
 * check if there is any CVE's that could have impact.
 * </p>
 */
public class DataCollector {

    /**
     *
     * @return data Map<String, String>
     */
    public Map<String, String> getData() {
        Map<String, String> data = new HashMap<>();

        data.put("productId", productId);
        data.put("productName", productName);
        data.put("productMetric", productMetric);
        data.put("metricValue", metricValue);
        data.put("productCloudpakRatio", productCloudpakRatio);
        data.put("cloudpakId", cloudpakId);
        data.put("cloudpakName", cloudpakName);
        data.put("cloudpakMetric", cloudpakMetric);
        data.put("logDate", logDate);

        return data;
    }

    private final String productId;
    private final String productName = "Liberty";
    private final String productMetric;
    private final String metricValue = "1";
    private final String productCloudpakRatio = "";
    private final String cloudpakId = "";
    private final String cloudpakName = "";
    private final String cloudpakMetric = "";
    private final String logDate;

    /**
     * <p>
     * Collects all the required data.
     * </p>
     *
     * @param featureProvisioner
     * @param FixManager
     * @param serverInfo
     * @throws IOException
     * @throws DataCollectorException
     */
    public DataCollector(FeatureProvisioner featureProvisioner, FixManager FixManager, ServerInfoMBean serverInfo,
                         Map<String, ? extends ProductInfo> allProductInfo) throws IOException, DataCollectorException {

        String productVersion;
        String productEdition;
        
        // the key is the productId
        if (allProductInfo.containsKey("com.ibm.websphere.appserver")) {
            productVersion = allProductInfo.get("com.ibm.websphere.appserver").getVersion();
            productEdition = allProductInfo.get("com.ibm.websphere.appserver").getEdition();
        } else {
            productVersion = allProductInfo.get("io.openliberty").getVersion();
            productEdition = allProductInfo.get("io.openliberty").getEdition();
        }

        // Generate productId as a hash
        StringBuilder input = new StringBuilder();
        input.append(serverInfo.getInstallDirectory());
        input.append(productEdition);
        input.append(System.getProperty("java.vendor"));
        input.append(System.getProperty("os.arch"));
        input.append(System.getProperty("os.name"));
        input.append(KernelUtils.getServerHostName());

        this.productId = HashUtils.hashString(input.toString());
        
        // Use productVersion as productMetric
        this.productMetric = productVersion;
        
        // Get current date in YYYY-MM-DD format
        this.logDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

}
