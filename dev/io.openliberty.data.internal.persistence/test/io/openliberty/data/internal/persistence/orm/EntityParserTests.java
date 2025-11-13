/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.data.internal.persistence.orm;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

/**
 *
 */
public class EntityParserTests {

    @Test
    public void simpleEntityTest() {
        EntityParser p = new EntityParser("");
        p.parse(Simple.class);
        List<String> xmls = p.generateView();

        assertEquals(1, xmls.size());

        final String expected = """
                          <entity class="io.openliberty.data.internal.persistence.orm.Simple">
                            <table name="Simple"/>
                            <attributes>
                              <id name="id" access="FIELD">
                                <column nullable="false"/>
                              </id>
                              <basic name="firstName" access="FIELD">
                              </basic>
                              <basic name="lastName" access="FIELD">
                              </basic>
                            </attributes>
                          </entity>
                        """;

        assertEquals(expected, xmls.get(0));
    }

    @Test
    public void versionedEntityTest() {
        EntityParser p = new EntityParser("");
        p.parse(Versioned.class);
        List<String> xmls = p.generateView();

        assertEquals(1, xmls.size());

        final String expected = """
                          <entity class="io.openliberty.data.internal.persistence.orm.Versioned">
                            <table name="Versioned"/>
                            <attributes>
                              <id name="identifier" access="FIELD">
                              </id>
                              <basic name="firstName" access="FIELD">
                              </basic>
                              <basic name="lastName" access="FIELD">
                              </basic>
                              <version name="version" access="FIELD">
                              </version>
                            </attributes>
                          </entity>
                        """;

        assertEquals(expected, xmls.get(0));
    }

    @Test
    public void collectionEntityTest() {
        EntityParser p = new EntityParser("");
        p.parse(Collection.class);
        List<String> xmls = p.generateView();

        assertEquals(1, xmls.size());

        final String expected = """
                          <entity class="io.openliberty.data.internal.persistence.orm.Collection">
                            <table name="Collection"/>
                            <attributes>
                              <id name="collectionId" access="FIELD">
                                <column nullable="false"/>
                              </id>
                              <basic name="firstName" access="FIELD">
                              </basic>
                              <basic name="lastName" access="FIELD">
                              </basic>
                              <element-collection name="friends" access="FIELD" fetch="EAGER">
                              </element-collection>
                            </attributes>
                          </entity>
                        """;

        assertEquals(expected, xmls.get(0));
    }

    @Test
    public void propertyEntityTest() {
        EntityParser p = new EntityParser("");
        p.parse(Property.class);
        List<String> xmls = p.generateView();

        assertEquals(1, xmls.size());

        final String expected = """
                          <entity class="io.openliberty.data.internal.persistence.orm.Property">
                            <table name="Property"/>
                            <attributes>
                              <id name="prop_id" access="FIELD">
                                <column nullable="false"/>
                              </id>
                              <basic name="URL" access="PROPERTY">
                              </basic>
                              <basic name="email" access="PROPERTY">
                              </basic>
                              <basic name="firstName" access="FIELD">
                              </basic>
                              <basic name="lastName" access="FIELD">
                              </basic>
                            </attributes>
                          </entity>
                        """;

        assertEquals(expected, xmls.get(0));
    }

    @Test
    public void embeddedEntityTest() {
        EntityParser p = new EntityParser("");
        p.parse(WithEmbedded.class);
        List<String> xmls = p.generateView();

        assertEquals(2, xmls.size());

        String expected = """
                          <entity class="io.openliberty.data.internal.persistence.orm.WithEmbedded">
                            <table name="WithEmbedded"/>
                            <attributes>
                              <id name="id" access="FIELD">
                                <column nullable="false"/>
                              </id>
                              <embedded name="name">
                                <attribute-override name="firstName">
                                  <column name="NAME_FIRSTNAME"/>
                                </attribute-override>
                                <attribute-override name="lastName">
                                  <column name="NAME_lastName"/>
                                </attribute-override>
                              </embedded>
                            </attributes>
                          </entity>
                        """;
        assertEquals(expected, xmls.get(0));

        expected = """
                          <embeddable class="io.openliberty.data.internal.persistence.orm.WithEmbedded$Name">
                            <attributes>
                              <basic name="firstName" access="FIELD">
                              </basic>
                              <basic name="lastName" access="FIELD">
                              </basic>
                            </attributes>
                          </embeddable>
                        """;
        assertEquals(expected, xmls.get(1));
    }

}
