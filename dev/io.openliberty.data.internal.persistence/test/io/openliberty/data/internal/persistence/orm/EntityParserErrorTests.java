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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import io.openliberty.data.internal.persistence.orm.TestConverters.InvalidConverter;
import jakarta.data.exceptions.MappingException;

/**
 *
 */
public class EntityParserErrorTests {

    @Test
    public void noIdEntityTest() {
        EntityParser p = new EntityParser("");

        try {
            p.parseUnannotatedEntity(WithoutId.class);
            fail("Should not have been able to parse an entity without an id atribute");
        } catch (MappingException e) {
            assertTrue("Error message should have contained entity class name " + WithoutId.class.getName() + " but was " + e.getMessage(),
                       e.getMessage().contains("WithoutId"));
        }
    }

    @Test
    public void noIdInMappedSuperclassEntityTest() {
        EntityParser p = new EntityParser("");

        try {
            p.parseUnannotatedEntity(WithoutIdMappedSuperclass.class);
            fail("Should not have been able to parse an entity without an id atribute");
        } catch (MappingException e) {
            assertTrue("Error message should have contained entity class name " + WithoutIdMappedSuperclass.class.getName() + " but was " + e.getMessage(),
                       e.getMessage().contains("WithoutIdMappedSuperclass"));

            assertTrue("Error message should have contained mappedsuperclass name " + SuperAlpha.class.getName() + " but was " + e.getMessage(),
                       e.getMessage().contains("SuperAlpha"));
        }
    }

    @Test
    public void multipleIdInMappedSuperclassEntityTest() {
        EntityParser p = new EntityParser("");

        try {
            p.parseUnannotatedEntity(WithMultipleIds.class);
            fail("Should not have been able to parse an entity with multiple id atributes");
        } catch (MappingException e) {
            assertTrue("Error message should have contained entity class name " + WithMultipleIds.class.getName() + " but was " + e.getMessage(),
                       e.getMessage().contains("WithMultipleIds"));

            assertTrue("Error message should have contained mappedsuperclass name " + SuperGammaPrime.class.getName() + " but was " + e.getMessage(),
                       e.getMessage().contains("SuperGammaPrime"));
        }
    }

    @Test
    public void invalidConverterEntityTest() {
        EntityParser p = new EntityParser("");

        try {
            p.parseUnannotatedEntity(WithConverterInvalid.class);
            fail("Should not have been able to parse an entity with a converter for the Calendar type.");
        } catch (MappingException e) {
            assertTrue("Error message should have contained converter class name " + InvalidConverter.class.getName() + " but was " + e.getMessage(),
                       e.getMessage().contains("InvalidConverter"));
        }
    }

}
