/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
<<<<<<< HEAD
=======
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
>>>>>>> e5aafff2fa6 (Add mptelemetry 2.1 TCK)
 *******************************************************************************/
package io.openliberty.microprofile.telemetry.internal_fat_tck;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import componenttest.annotation.MinimumJavaLevel;
import componenttest.custom.junit.runner.AlwaysPassesTest;
<<<<<<< HEAD
import componenttest.custom.junit.runner.Mode.TestMode;
=======
>>>>>>> e5aafff2fa6 (Add mptelemetry 2.1 TCK)
import componenttest.rules.repeater.MicroProfileActions;
import componenttest.rules.repeater.RepeatTests;
import componenttest.topology.utils.tck.TCKUtilities;

@RunWith(Suite.class)
@SuiteClasses({
                AlwaysPassesTest.class,
                Telemetry21TCKLauncher.class,
                Telemetry21MetricsConfigTCKLauncher.class,
                Telemetry21LogsConfigTCKLauncher.class
})
@MinimumJavaLevel(javaLevel = 11)
public class FATSuite {

    public static RepeatTests allMPTel21Repeats(String serverName) {
        return MicroProfileActions
<<<<<<< HEAD
                        .repeatIf(serverName, TCKUtilities::areAllFeaturesPresent, TestMode.FULL, true, MicroProfileActions.MP71_EE10, MicroProfileActions.MP71_EE11);
    }
}
=======
                        .repeatIf(serverName, TCKUtilities::areAllFeaturesPresent, MicroProfileActions.MP71_EE11, MicroProfileActions.MP71_EE10);
    }
}
>>>>>>> e5aafff2fa6 (Add mptelemetry 2.1 TCK)
