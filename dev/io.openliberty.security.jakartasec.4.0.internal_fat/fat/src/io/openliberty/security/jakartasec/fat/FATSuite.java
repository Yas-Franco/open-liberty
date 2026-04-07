/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package io.openliberty.security.jakartasec.fat;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import io.openliberty.security.jakartasec.fat.tests.MultipleIdentityStoreTypesTests;

@RunWith(Suite.class)
@SuiteClasses({
//                AlwaysPassesTest.class,
//                MultipleHAMCustomTests.class,
//                MultipleHAMDuplicateTests.class,
//                MultipleHAMInbuiltTests.class,
//                MultipleHAMInbuiltQualifiersTests.class,
//                HAMWithInBuiltTests.class,
//                SingleHAMInbuiltCustomQualifierTests.class,
//                MissingCustomHandlerTests.class,
//                InMemoryIdentityStoreTests.class,
//                InMemoryIdentityStoreELWarningTest.class,
//                InMemoryIdentityStorePropertyNotFoundTest.class,
//                InMemoryIdentityStoreEnablementTests.class,
//                InMemoryIdStoreBadlyEncodedPwdTests.class,
//                InMemoryIdStoreAesEncodedPwdTests.class,
//                MultipleInMemoryIdentityStoresTests.class,
                MultipleIdentityStoreTypesTests.class,
//                MultipleAppsInMemoryIdStoresTests.class,
//                AppRolesTests.class,
//                AppBndRolesTests.class
})

public class FATSuite {
}
