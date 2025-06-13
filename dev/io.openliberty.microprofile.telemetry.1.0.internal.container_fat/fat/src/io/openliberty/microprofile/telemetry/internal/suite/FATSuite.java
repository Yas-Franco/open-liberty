/*******************************************************************************
 * Copyright (c) 2022, 2025 IBM Corporation and others.
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
package io.openliberty.microprofile.telemetry.internal.suite;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import componenttest.containers.TestContainerSuite;
import componenttest.custom.junit.runner.AlwaysPassesTest;
import componenttest.custom.junit.runner.RepeatTestFilter;
import io.openliberty.microprofile.telemetry.internal.tests.Agent129Test;
import io.openliberty.microprofile.telemetry.internal.tests.Agent214Test;
import io.openliberty.microprofile.telemetry.internal.tests.Agent250Test;
import io.openliberty.microprofile.telemetry.internal.tests.AgentConfigMultiAppTest;
import io.openliberty.microprofile.telemetry.internal.tests.AgentConfigTest;
import io.openliberty.microprofile.telemetry.internal.tests.AgentTest;
import io.openliberty.microprofile.telemetry.internal.tests.CrossFeatureJaegerTest;
import io.openliberty.microprofile.telemetry.internal.tests.CrossFeatureZipkinTest;
import io.openliberty.microprofile.telemetry.internal.tests.JaegerLegacyTest;
import io.openliberty.microprofile.telemetry.internal.tests.JaegerOtelCollectorTest;
import io.openliberty.microprofile.telemetry.internal.tests.JaegerOtlpTest;
import io.openliberty.microprofile.telemetry.internal.tests.JaegerSecureOtelCollectorTest;
import io.openliberty.microprofile.telemetry.internal.tests.JaegerSecureOtlpTest;
import io.openliberty.microprofile.telemetry.internal.tests.TracingNotEnabledTest;
import io.openliberty.microprofile.telemetry.internal.tests.ZipkinOtelCollectorTest;
import io.openliberty.microprofile.telemetry.internal.tests.ZipkinTest;
import io.openliberty.microprofile.telemetry.internal.tests.JvmMetricsOtelCollectorTest;
import io.openliberty.microprofile.telemetry.internal.tests.MetricsApiOtelCollectorTest;
import componenttest.rules.repeater.MicroProfileActions;
import io.openliberty.microprofile.telemetry.internal_fat.shared.TelemetryActions;

@RunWith(Suite.class)
@SuiteClasses({
                AlwaysPassesTest.class, //Must keep this test to run something in the Java 6 builds.
                AgentTest.class,
                Agent129Test.class,
                Agent214Test.class,
                Agent250Test.class,
                AgentConfigTest.class,
                AgentConfigMultiAppTest.class,
                CrossFeatureJaegerTest.class,
                CrossFeatureZipkinTest.class,
                JaegerSecureOtelCollectorTest.class,
                JaegerSecureOtlpTest.class,
                JaegerOtlpTest.class,
                JaegerOtelCollectorTest.class,
                JaegerLegacyTest.class,
                TracingNotEnabledTest.class,
                JvmMetricsOtelCollectorTest.class,
                MetricsApiOtelCollectorTest.class,
                ZipkinOtelCollectorTest.class,
                ZipkinTest.class,

})

/**
 * Purpose: This suite collects and runs all known good test suites.
 */
public class FATSuite extends TestContainerSuite {
    public static String getTelemetryVersionUnderTest() {
        if (RepeatTestFilter.isRepeatActionActive(MicroProfileActions.MP60_ID)) {
            return "1.0";
        } else if (RepeatTestFilter.isAnyRepeatActionActive(MicroProfileActions.MP70_EE11_ID, MicroProfileActions.MP70_EE10_ID, TelemetryActions.MP61_MPTEL20_ID, TelemetryActions.MP50_MPTEL20_JAVA8_ID,
                                                            TelemetryActions.MP50_MPTEL20_ID, TelemetryActions.MP41_MPTEL20_ID, TelemetryActions.MP14_MPTEL20_ID)) {
            return "2.0";
        } else if (RepeatTestFilter.isAnyRepeatActionActive(MicroProfileActions.MP71_EE11_ID, MicroProfileActions.MP71_EE10_ID,TelemetryActions.MP50_MPTEL21_JAVA8_ID,
                                                            TelemetryActions.MP50_MPTEL21_ID, TelemetryActions.MP41_MPTEL21_ID, TelemetryActions.MP14_MPTEL21_ID)) {
            return "2.1";
        } else {
            return "1.1";
        }
    }
}