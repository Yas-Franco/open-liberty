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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 *
 */
class Relationships {
    // one-to-many relationship between mapped-superclass and entity
    private final ConcurrentMap<Class<?>, Set<Class<?>>> mappedSuperclassToEntity;

    // many-to-many relationship between entity and embedded/embeddable
    private final ConcurrentMap<Class<?>, Set<Class<?>>> entityToEmbed;
    private final ConcurrentMap<Class<?>, Set<Class<?>>> embedToEntity;

    // one-to-one relationship between record and entity class;
    private final Map<Class<?>, Class<?>> entityToRecord;

    public Relationships() {
        this.mappedSuperclassToEntity = new ConcurrentHashMap<>();
        this.entityToEmbed = new ConcurrentHashMap<>();
        this.embedToEntity = new ConcurrentHashMap<>();
        this.entityToRecord = new HashMap<>();
    }

    // ASSOCIATIONS
    public void entityToMappedSuperclass(Class<?> entity, Class<?> mappedSuperclass) {
        mappedSuperclassToEntity.computeIfAbsent(mappedSuperclass, set -> {
            Set<Class<?>> newSet = new HashSet<>();
            newSet.add(entity);
            return newSet;
        });

        mappedSuperclassToEntity.computeIfPresent(mappedSuperclass, (key, set) -> {
            set.add(entity);
            return set;
        });
    }

    public void entityToEmbed(Class<?> entity, Class<?> embed) {
        entityToEmbed.computeIfAbsent(entity, set -> {
            Set<Class<?>> newSet = new HashSet<>();
            newSet.add(embed);
            return newSet;
        });

        entityToEmbed.computeIfPresent(entity, (key, set) -> {
            set.add(embed);
            return set;
        });

        embedToEntity.computeIfAbsent(embed, set -> {
            Set<Class<?>> newSet = new HashSet<>();
            newSet.add(entity);
            return newSet;
        });

        embedToEntity.computeIfPresent(embed, (key, set) -> {
            set.add(entity);
            return set;
        });
    }

    public void entityToRecord(Class<?> entity, Class<?> rec) {
        entityToRecord.put(entity, rec);
    }

    // Predicates
    public Set<Class<?>> embedsForEntity(Class<?> entity) {
        if (embedToEntity.containsKey(entity)) {
            return embedToEntity.get(entity);
        }
        return Set.of();
    }

    public Set<Class<?>> superclassesForEntity(Class<?> entity) {
        return mappedSuperclassToEntity.entrySet().stream()//
                        .filter(entry -> entry.getValue().contains(entity))//
                        .map(entry -> entry.getKey())//
                        .collect(Collectors.toSet());
    }

    public Class<?> recordForEntity(Class<?> entity) {
        return entityToRecord.get(entity);
    }

    public boolean entityHasRecord(Class<?> entity) {
        return entityToRecord.containsKey(entity);
    }

    public boolean embedHasEntity(Class<?> embed) {
        return embedToEntity.containsKey(embed);
    }
}
