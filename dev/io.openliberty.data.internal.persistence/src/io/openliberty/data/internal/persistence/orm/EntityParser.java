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

import static io.openliberty.data.internal.persistence.cdi.DataExtension.exc;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import io.openliberty.data.internal.persistence.Util;
import io.openliberty.data.internal.persistence.orm.Models.AccessType;
import io.openliberty.data.internal.persistence.orm.Models.Attribute;
import io.openliberty.data.internal.persistence.orm.Models.AttributeKind;
import io.openliberty.data.internal.persistence.orm.Models.Converter;
import io.openliberty.data.internal.persistence.orm.Models.EmbeddableRecord;
import io.openliberty.data.internal.persistence.orm.Models.EntityRecord;
import io.openliberty.data.internal.persistence.orm.Models.MappedSuperclass;
import jakarta.data.exceptions.MappingException;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * TODO javadoc and trace
 */
public class EntityParser {

    // ORM sets
    private final SortedSet<MappedSuperclass> mappedSuperclasses;
    private final SortedSet<EntityRecord> entities;
    private final SortedSet<EmbeddableRecord> embeddables;
    private final SortedSet<Converter> converters;

    // ORM associated sets
    private final Set<Class<?>> convertibles;
    private final LinkedHashSet<String> tableNames;
    private final LinkedHashSet<String> classNames;

    // Relationships
    private final Relationships relate;

    // Global configurations
    private final String tablePrefix;

    // State controls flow from initialization to generation
    private boolean doneParsing;

    // Current entity being parsed
    private Class<?> currentEntity;
    private Set<Attribute> idAttributes;

    public EntityParser(String tablePrefix) {
        this.mappedSuperclasses = new TreeSet<>();
        this.entities = new TreeSet<>();
        this.embeddables = new TreeSet<>();
        this.converters = new TreeSet<>();

        this.convertibles = new HashSet<>();
        this.tableNames = new LinkedHashSet<>();
        this.classNames = new LinkedHashSet<>();

        this.relate = new Relationships();

        this.tablePrefix = tablePrefix;

        this.doneParsing = false;
    }

    // ENTRY POINTS
    public void parseAnnotatedEntity(Class<?> annotatedEntity) {
        if (doneParsing) {
            // Internal exception
            throw new IllegalStateException("Attempted to parse an entity after generating mapping");
        }

        for (Class<?> superclass = annotatedEntity; //
                        superclass != null && superclass != Object.class; //
                        superclass = superclass.getSuperclass()) {
            parseAnnotatedObject(superclass);
        }
    }

    public void parseRecord(Class<?> record, Class<?> generatedEntity) {
        if (doneParsing) {
            // Internal exception
            throw new IllegalStateException("Attempted to parse an entity after generating mapping");
        }

        relate.entityToRecord(generatedEntity, record);
        parse(generatedEntity, tablePrefix + record.getSimpleName());
    }

    public void parseUnannotatedEntity(Class<?> entity) {
        if (doneParsing) {
            // Internal exception
            throw new IllegalStateException("Attempted to parse an entity after generating mapping");
        }

        this.tableNames.add(tablePrefix + entity.getSimpleName());
        parse(entity, tablePrefix + entity.getSimpleName());

    }

    // PARSER

    private void parse(Class<?> entity, String tableName) {
        // If entity was unannotated then we must
        // construct an object relational mapping
        this.currentEntity = entity;
        this.idAttributes = new HashSet<>();

        for (Class<?> superclass = currentEntity; //
                        superclass != null && superclass != Object.class; //
                        superclass = superclass.getSuperclass()) {

            for (Convert convert : superclass.getAnnotationsByType(Convert.class)) {
                recordConverter(convert);
            }

            if (superclass == currentEntity) {
                // Record entity and any embeddables found along the way
                entities.add(new EntityRecord(superclass, tableName, finalizeAttributes(superclass, findAttributes(superclass))));
                continue;
            }

            // Record all mapped superclasses and any embeddables found along the way
            relate.entityToMappedSuperclass(currentEntity, superclass);
            mappedSuperclasses.add(new MappedSuperclass(superclass, finalizeAttributes(superclass, findAttributes(superclass))));
        }

        verify();
    }

    // HELPER METHODS

    private Set<Attribute> findAttributes(Class<?> c) {
        Set<Attribute> attributes = new HashSet<>();

        if (relate.entityHasRecord(c)) {
            Class<?> r = relate.recordForEntity(c);
            for (RecordComponent rc : r.getRecordComponents())
                attributes.add(new Attribute(rc.getType(), rc.getGenericType(), rc.getName(), AccessType.FIELD));
        } else {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isPublic(f.getModifiers())) {
                    attributes.add(new Attribute(f.getType(), f.getGenericType(), f.getName(), AccessType.FIELD));

                    for (Convert convert : f.getAnnotationsByType(Convert.class))
                        recordConverter(convert);
                }
            }

            try {
                PropertyDescriptor[] propertyDescriptors = Introspector //
                                .getBeanInfo(c).getPropertyDescriptors();
                if (propertyDescriptors != null)
                    for (PropertyDescriptor p : propertyDescriptors) {
                        if (p.getWriteMethod() != null) {
                            //Note: p.getName() utilizes Introspector.decapitalize method
                            //      which honors acryonyms like getURL/setURL -> URL (instead of uRL)
                            Type genericType = p.getWriteMethod().getGenericReturnType();
                            attributes.add(new Attribute(p.getPropertyType(), genericType, p.getName(), AccessType.PROPERTY));
                        }

                        if (p.getReadMethod() != null)
                            for (Convert convert : p.getReadMethod().getAnnotationsByType(Convert.class))
                                recordConverter(convert);
                    }

            } catch (IntrospectionException x) {
                throw new MappingException(x);
            }
        }
        return attributes;
    }

    private Set<Attribute> finalizeAttributes(Class<?> c, Set<Attribute> incompletes) {
        Attribute id = null;
        Attribute version = null;

        // Determine which attribute is the id and version (optional).
        // Id precedence:
        // (1) name is id, ignoring case.
        // (2) name ends with _id, ignoring case.
        // (3) name ends with Id or ID.
        // (4) type is UUID.
        // Version precedence (if also a valid version type):
        // (1) name is version, ignoring case.
        // (2) name is _version, ignoring case.
        int idPrecedence = 10;
        int vPrecedence = 10;
        for (Attribute attr : incompletes) {
            String name = attr.name();
            Class<?> type = attr.type();
            int len = name.length();

            if (idPrecedence > 1 &&
                len >= 2 &&
                name.regionMatches(true, len - 2, "id", 0, 2)) {
                if (name.length() == 2) {
                    id = attr;
                    idPrecedence = 1;
                } else if (idPrecedence > 2 &&
                           name.charAt(len - 3) == '_') {
                    id = attr;
                    idPrecedence = 2;
                } else if (idPrecedence > 3 &&
                           name.charAt(len - 2) == 'I') {
                    id = attr;
                    idPrecedence = 3;
                }
            } else if (idPrecedence > 4 && UUID.class.equals(type)) {
                id = attr;
                idPrecedence = 4;
            }

            if (vPrecedence > 1 &&
                len == 7 &&
                Util.VERSION_TYPES.contains(type) &&
                "version".equalsIgnoreCase(name)) {
                version = attr;
                vPrecedence = 1;
            } else if (vPrecedence > 2 &&
                       len == 8 &&
                       Util.VERSION_TYPES.contains(type) &&
                       "_version".equalsIgnoreCase(name)) {
                version = attr;
                vPrecedence = 2;
            }
        }

        for (Attribute attr : incompletes) {
            Class<?> type = attr.type();

            boolean isId = attr == id;
            boolean isVersion = attr == version;
            boolean isBasic = type.isPrimitive() || //
                              type.isInterface() || //
                              Serializable.class.isAssignableFrom(type);

            boolean isCollection = Collection.class.isAssignableFrom(type);
            Class<?> collectionType = isCollection ? //
                            (Class<?>) ((ParameterizedType) attr.genericType()).getActualTypeArguments()[0] : //
                            null;
            boolean isCollectionBasic = isCollection ? collectionType.isPrimitive() || //
                                                       collectionType.isInterface() || //
                                                       Serializable.class.isAssignableFrom(collectionType) : false;

            final AttributeKind kind;
            if (isCollection) {
                if (isCollectionBasic)
                    kind = AttributeKind.BASIC$ELEMENT_COLLECTION;
                else
                    kind = AttributeKind.EMBEDDED$ELEMENT_COLLECTION;
            } else if (isBasic) {
                if (isId)
                    kind = AttributeKind.ID;
                else if (isVersion)
                    kind = AttributeKind.VERSION;
                else
                    kind = AttributeKind.BASIC;
            } else {
                if (isId)
                    kind = AttributeKind.EMBEDDED_ID;
                else
                    kind = AttributeKind.EMBEDDED;
            }
            attr.setKind(kind);

            if (attr.isEmbedded()) {
                if (relate.embedHasEntity(type)) {
                    relate.entityToEmbed(c, type);
                } else {
                    Set<Attribute> overrides = finalizeAttributes(c, findAttributes(type));
                    attr.setOverrides(overrides);

                    embeddables.add(new EmbeddableRecord(type, overrides));
                    relate.entityToEmbed(c, type);
                }
            }

            if (attr.isEmbeddedCollection()) {
                if (relate.embedHasEntity(collectionType)) {
                    relate.entityToEmbed(c, collectionType);
                } else {
                    Set<Attribute> overrides = finalizeAttributes(c, findAttributes(collectionType));
                    attr.setOverrides(overrides);
                    attr.setCollectionId(id);

                    embeddables.add(new EmbeddableRecord(collectionType, overrides));
                    relate.entityToEmbed(c, collectionType);
                }
            }

            if (attr.isId()) {
                idAttributes.add(attr);
            }
        }

        return new TreeSet<Attribute>(incompletes);
    }

    private void recordConverter(Convert convert) {
        if (convert.converter() != null && convert.converter() != AttributeConverter.class) {
            Class<?> converterType = convert.converter();
            Converter converter = new Converter(convert.converter());

            if (!converters.contains(converter)) {
                for (Class<?> c = converterType; c != null; c = c.getSuperclass())
                    for (Type ifc : c.getGenericInterfaces())
                        if (ifc instanceof ParameterizedType type &&
                            ifc.getTypeName().startsWith(Util.ATTR_CONVERTER_CLASS_NAME)) {
                            Type[] typeParams = type.getActualTypeArguments();
                            if (Util.UNSUPPORTED_ATTR_TYPES.contains(typeParams[1]))
                                throw exc(MappingException.class,
                                          "CWWKD1111.unsupported.convert",
                                          converterType.getName(),
                                          typeParams[0].getTypeName(),
                                          typeParams[1].getTypeName(),
                                          Util.SUPPORTED_TEMPORAL_TYPES,
                                          Util.SUPPORTED_BASIC_TYPES);

                            if (typeParams[0] instanceof Class)
                                convertibles.add((Class<?>) typeParams[0]);
                        }
                converters.add(converter);
            }
        }
    }

    private void verify() {
        if (idAttributes.isEmpty()) {
            // Costly operations, only do for error state
            EntityRecord invalid = entities.stream()//
                            .filter(e -> e.type() == currentEntity)//
                            .findFirst()//
                            .orElseThrow();
            Set<Class<?>> supers = relate.superclassesForEntity(currentEntity);
            Set<MappedSuperclass> invalidSupers = supers.isEmpty() ? Set.of() : mappedSuperclasses.stream()//
                            .filter(e -> supers.contains(e.type()))//
                            .collect(Collectors.toSet());

            //TODO NLS
            throw new MappingException("The entity " + invalid + " had no id attribute"
                                       + (invalidSupers.isEmpty() ? " " : " nor was any id attribute found on any mapped superclass " + invalidSupers));
        }

        if (idAttributes.size() > 1) {
            // Costly operations, only do for error state
            EntityRecord invalid = entities.stream()//
                            .filter(e -> e.type() == currentEntity)//
                            .findFirst()//
                            .orElseThrow();
            Set<Class<?>> supers = relate.superclassesForEntity(currentEntity);
            Set<MappedSuperclass> invalidSupers = supers.isEmpty() ? Set.of() : mappedSuperclasses.stream()//
                            .filter(e -> supers.contains(e.type()))//
                            .collect(Collectors.toSet());

            //TODO NLS
            throw new MappingException("The entity " + invalid + " had more than one id attribute due to a combination of the entity's own attributes"
                                       + " and the attributes of the entity's mapped superclasses " + invalidSupers + ". "
                                       + "The id attributes are: " + idAttributes);
        }
    }

    private void parseAnnotatedObject(Class<?> c) {
        if (classNames.contains(c.getName())) {
            return;
        }

        if (c.isAnnotationPresent(Entity.class)) {
            classNames.add(c.getName());
            if (c.isAnnotationPresent(Table.class))
                tableNames.add(c.getAnnotation(Table.class).name());
            else
                tableNames.add(c.getSimpleName());
        }

        for (Convert convert : c.getAnnotationsByType(Convert.class))
            if (convert.converter() != null && convert.converter() != AttributeConverter.class)
                recordConverter(convert);

        for (Field f : c.getDeclaredFields()) {
            forEmbeddable(f).ifPresent(emb -> parseAnnotatedObject(emb));
            forMapping(f).ifPresent(entity -> parseAnnotatedObject(entity));
            for (Convert convert : f.getAnnotationsByType(Convert.class))
                if (convert.converter() != null && convert.converter() != AttributeConverter.class)
                    recordConverter(convert);
        }

        for (Method m : c.getDeclaredMethods()) {
            forEmbeddable(m).ifPresent(emb -> parseAnnotatedObject(emb));
            forMapping(m).ifPresent(entity -> parseAnnotatedObject(entity));
            for (Convert convert : m.getAnnotationsByType(Convert.class))
                if (convert.converter() != null && convert.converter() != AttributeConverter.class)
                    recordConverter(convert);
        }

    }

    private Optional<Class<?>> forEmbeddable(Field f) {
        if (f.isAnnotationPresent(Embedded.class) ||
            f.isAnnotationPresent(EmbeddedId.class)) {
            if (f.getType().isAnnotationPresent(Embeddable.class)) {
                return Optional.of(f.getType());
            }
            return Optional.of(getEmbeddableClass(f.getGenericType()));
        }
        return Optional.empty();
    }

    private Optional<Class<?>> forMapping(Field f) {
        if (f.isAnnotationPresent(ManyToOne.class) ||
            f.isAnnotationPresent(OneToOne.class)) {
            if (f.getType().isAnnotationPresent(Entity.class)) {
                return Optional.of(f.getType());
            }
        }

        if (f.isAnnotationPresent(ManyToMany.class) ||
            f.isAnnotationPresent(OneToMany.class)) {
            return Optional.of(getEntityClass(f.getGenericType()));
        }

        return Optional.empty();
    }

    private Optional<Class<?>> forEmbeddable(Method m) {
        if (m.isAnnotationPresent(Embedded.class) ||
            m.isAnnotationPresent(EmbeddedId.class)) {
            if (m.getReturnType().isAnnotationPresent(Embeddable.class)) {
                return Optional.of(m.getReturnType());
            }
            return Optional.of(getEmbeddableClass(m.getGenericReturnType()));
        }
        return Optional.empty();
    }

    private Optional<Class<?>> forMapping(Method m) {
        if (m.isAnnotationPresent(ManyToOne.class) ||
            m.isAnnotationPresent(OneToOne.class)) {
            if (m.getReturnType().isAnnotationPresent(Entity.class)) {
                return Optional.of(m.getReturnType());
            }
        }

        if (m.isAnnotationPresent(ManyToMany.class) ||
            m.isAnnotationPresent(OneToMany.class)) {
            return Optional.of(getEntityClass(m.getGenericReturnType()));
        }

        return Optional.empty();
    }

    private Class<?> getEmbeddableClass(java.lang.reflect.Type type) {
        if (type instanceof ParameterizedType) {
            java.lang.reflect.Type[] typeParams = //
                            ((ParameterizedType) type).getActualTypeArguments();
            for (java.lang.reflect.Type t : typeParams)
                if (t instanceof Class && ((Class<?>) t).isAnnotationPresent(Embeddable.class)) {
                    return (Class<?>) t;
                }
        }
        return null;
    }

    private Class<?> getEntityClass(java.lang.reflect.Type type) {
        if (type instanceof ParameterizedType) {
            java.lang.reflect.Type[] typeParams = //
                            ((ParameterizedType) type).getActualTypeArguments();
            for (java.lang.reflect.Type t : typeParams)
                if (t instanceof Class && ((Class<?>) t).isAnnotationPresent(Entity.class)) {
                    return (Class<?>) t;
                }
        }
        return null;
    }

    // GENERATORS

    public List<String> generateView() {
        doneParsing = true;

        View view = new View(mappedSuperclasses.size() + entities.size() + embeddables.size() + converters.size());

        for (MappedSuperclass sc : mappedSuperclasses) {
            view.mappedSuperclass(sc);
        }

        for (EntityRecord er : entities) {
            view.entity(er);
        }

        for (EmbeddableRecord emb : embeddables) {
            view.embedable(emb);
        }

        for (Converter con : converters) {
            view.converter(con);
        }

        return view.get();
    }

    public LinkedHashSet<String> getClassNames() {
        doneParsing = true;
        return classNames;
    }

    public LinkedHashSet<String> getTableNames() {
        doneParsing = true;
        return tableNames;
    }

    public Set<Class<?>> getConvertibiles() {
        doneParsing = true;
        return convertibles;
    }
}
