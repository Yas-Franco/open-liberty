/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package inmemory.identity.store;

import static jakarta.security.enterprise.identitystore.IdentityStore.ValidationType.PROVIDE_GROUPS;
import static jakarta.security.enterprise.identitystore.IdentityStore.ValidationType.VALIDATE;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import componenttest.app.FATServlet;
import jakarta.security.enterprise.authentication.mechanism.http.BasicAuthenticationMechanismDefinition;
import jakarta.security.enterprise.identitystore.InMemoryIdentityStoreDefinition;
import jakarta.security.enterprise.identitystore.InMemoryIdentityStoreDefinition.Credentials;
import jakarta.servlet.annotation.WebServlet;

@BasicAuthenticationMechanismDefinition(realmName = "basicAuth")
@InMemoryIdentityStoreDefinition(
                                 priority = 10,
                                 priorityExpression = "${80/20}",
                                 useFor = { VALIDATE, PROVIDE_GROUPS },
                                 //useForExpression = "#{'VALIDATE'}",
                                 value = {
                                           @Credentials(callerName = "jasmine", password = "secret1", groups = { "caller", "user" }),
                                           @Credentials(callerName = "lisa", password = "{xor}LDo8LTorbg==", groups = { "caller", "user" }),
                                           // not in any valid groups
                                           @Credentials(callerName = "bill", password = "{xor}LDo8LTorbg==", groups = { "foo", "bar" }),
                                           // bad {xor} string leads to decoding error to test error message output
                                           @Credentials(callerName = "johnny", password = "{xor}LLTxlkwjljsdforbg=", groups = { "foo", "bar" }),
                                           @Credentials(callerName = "frank", groups = { "user" },
                                                        password = "{hash}ARAAAAAUUEJLREYyV2l0aEhtYWNTSEE1MTIwAAAAIKJLaCvuDfiQYK8H/6SWdTzmbMxndGqWyUWnCaA3ZPOZQAAAACBnjkRtcavFNC2k2qbwEitLlmHZKTYRmeuuCh8z1nFyyw=="),
                                           @Credentials(callerName = "sally", groups = { "user" },
                                                        password = "{aes}ARAFCrWIYJCL7ZBNjN+MKcJoozBmbZyJPood6X6sCqMIRBaouZSd4B0u0Gcgdp/tXWwuOwi/mqYLS0cGTwuU95tgP4Y+6hgtvG2ST2gT+ghTPhLJWfiXTrvBRvR5yf2F9hkmM7SG/WRQNA==")
                                 })
@WebServlet("/BasicServlet")
public class BasicServlet extends FATServlet {
    private static final long serialVersionUID = 1L;

    public BasicServlet() {
        super();
    }

    @Test
    public void testPassed() {
        assertEquals("test passed", "test passed");
    }
}