/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.data.internal.persistence;

import static io.openliberty.data.internal.persistence.cdi.DataExtension.exc;

import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.ibm.websphere.ras.annotation.Sensitive;

import io.openliberty.data.internal.QueryType;
import io.openliberty.data.internal.version.DataVersionCompatibility;
import jakarta.data.Limit;
import jakarta.data.exceptions.DataException;
import jakarta.data.exceptions.EmptyResultException;
import jakarta.data.exceptions.MappingException;
import jakarta.data.exceptions.NonUniqueResultException;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Param;
import jakarta.data.repository.Query;

/**
 * This class consists of methods that raise exceptions.
 */
public class Fail {

    /**
     * Raises a new UnsupportedOperationException for a JPQL query that is
     * not compatible with cursor pagination.
     *
     * @param info              query information for the repository method.
     * @param ql                the query.
     * @param endOfWhereClause  position at which the WHERE clause ends.
     * @param endsAtOrderClause indicates if this error is being raised because an
     *                              ORDER BY clause was found in the query.
     * @throws UnsupportedOperationException
     */
    static UnsupportedOperationException cursorQueryIncompat(QueryInfo info,
                                                             String ql,
                                                             int endOfWhereClause,
                                                             boolean endsAtOrderClause) {

        if (endsAtOrderClause)
            throw exc(UnsupportedOperationException.class,
                      "CWWKD1033.ql.orderby.disallowed",
                      info.method.getName(),
                      info.repositoryInterface.getName(),
                      CursoredPage.class.getSimpleName(),
                      OrderBy.class.getSimpleName(),
                      ql);
        else
            throw exc(UnsupportedOperationException.class,
                      "CWWKD1034.ql.req.end.in.where",
                      info.method.getName(),
                      info.repositoryInterface.getName(),
                      CursoredPage.class.getSimpleName(),
                      endOfWhereClause,
                      ql.length(),
                      ql);
    }

    /**
     * Raise a new EmptyResultException.
     *
     * @param info query information for the repository method.
     * @throws the EmptyResultException.
     */
    static EmptyResultException emptyResult(QueryInfo info) {
        throw exc(EmptyResultException.class,
                  "CWWKD1053.empty.result",
                  info.method.getGenericReturnType().getTypeName(),
                  info.method.getName(),
                  info.repositoryInterface.getName(),
                  List.of(List.class.getSimpleName(),
                          Optional.class.getSimpleName(),
                          Page.class.getSimpleName(),
                          CursoredPage.class.getSimpleName(),
                          Stream.class.getSimpleName()));
    }

    /**
     * Raise a new DataException for the error where the repository method has an
     * extra parameter that does not apply to the query conditions and is not a
     * special parameter.
     *
     * @param info  query information for the repository method.
     * @param index index (0-based) of the repository method parameter.
     * @throws the DataException.
     */
    static DataException extraMethodParam(QueryInfo info, int index) {
        validateParameterPositions(info);

        throw exc(DataException.class,
                  "CWWKD1023.extra.param",
                  info.method.getName(),
                  info.repositoryInterface.getName(),
                  info.specialParamsStartAt,
                  info.method.getParameterTypes()[index].getName(),
                  info.jpql);
    }

    /**
     * Check if the cause of the lacking named parameter is a mispositioned
     * special parameter. If so, raises UnsupportedOperationException.
     *
     * Otherwise, raises a MappingException for the error where one or more
     * of the named parameters required by a JPQL query are not specified
     * by the method parameters
     *
     * @param info    query information for the repository method.
     * @param lacking query named parameters for which no method parameters were
     *                    found.
     * @throws MappingException              for a missing named parameter.
     * @throws UnsupportedOperationException if there is a mispositioned special
     *                                           parameter.
     */
    static MappingException methodLacksNamedParams(QueryInfo info,
                                                   Set<String> lacking) {

        validateParameterPositions(info);

        String first = null;
        StringBuilder all = new StringBuilder();
        for (String name : lacking) {
            if (first == null)
                first = name;
            else
                all.append(", ");
            all.append(':').append(name);
        }

        throw exc(MappingException.class,
                  "CWWKD1084.missing.named.params",
                  info.method.getName(),
                  info.repositoryInterface.getName(),
                  all,
                  info.method.getAnnotation(Query.class).value(),
                  "@Param(\"" + first + "\")",
                  "String " + first);
    }

    /**
     * Create a new UnsupportedOperationException for conflicting Limit or
     * PageRequest parameters supplied to a repository method. The conflict
     * might be with each other, with another of the same type, or with a
     * First keyword in the method name.
     *
     * @param info    query information for the repository method.
     * @param param   method parameter that is an instance of Limit or PageRequest.
     * @param limit   other Limit parameter value. Otherwise null.
     * @param pageReq other PageRequest parameter value. Otherwise null.
     * @throws the UnsupportedOperationException
     */
    static UnsupportedOperationException methodParamIncompat(QueryInfo info,
                                                             Object param,
                                                             Limit limit,
                                                             PageRequest pageReq) {
        Class<?> type = param instanceof Limit ? Limit.class : PageRequest.class;

        if (limit == null && pageReq == null)
            // conflicts with First keyword
            throw exc(UnsupportedOperationException.class,
                      "CWWKD1099.first.keyword.incompat",
                      info.method.getName(),
                      info.repositoryInterface.getName(),
                      type.getSimpleName());
        else if (param instanceof Limit ? limit != null : pageReq != null)
            // conflicts with another parameter of the same type
            throw exc(UnsupportedOperationException.class,
                      "CWWKD1017.dup.special.param",
                      info.method.getName(),
                      info.repositoryInterface.getName(),
                      type.getSimpleName());
        else
            // conflict between Limit and PageRequest parameters
            throw exc(UnsupportedOperationException.class,
                      "CWWKD1018.confl.special.param",
                      info.method.getName(),
                      info.repositoryInterface.getName(),
                      Limit.class.getSimpleName(),
                      PageRequest.class.getSimpleName());
    }

    /**
     * Constructs the MappingException or UnsupportedOperationException for
     * the error where a repository method parameter lacks an annotation that
     * identifies the corresponding entity attribute name.
     *
     * @param info query information for the repository method.
     * @param p    position (1-based) of the repository method parameter.
     * @throws MappingException or UnsupportedOperationException.
     */
    static RuntimeException methodParamLacksAnno(QueryInfo info, int p) {
        DataVersionCompatibility compat = info.producer.compat();

        switch (info.type) {
            case FIND:
            case FIND_AND_DELETE:
                validateParameterPositions(info);
                String specParams = info.type == QueryType.FIND //
                                ? compat.specialParamsForFind() //
                                : compat.specialParamsForFindAndDelete();
                throw exc(MappingException.class,
                          "CWWKD1012.fd.missing.param.anno",
                          p,
                          info.method.getName(),
                          info.repositoryInterface.getName(),
                          specParams);
            case QM_DELETE:
            case COUNT:
            case EXISTS:
                throw exc(MappingException.class,
                          "CWWKD1013.cde.missing.param.anno",
                          p,
                          info.method.getName(),
                          info.repositoryInterface.getName());
            case QM_UPDATE:
                throw exc(UnsupportedOperationException.class,
                          "CWWKD1014.upd.missing.param.anno",
                          info.method.getName(),
                          info.repositoryInterface.getName(),
                          info.method.getParameterCount(),
                          p,
                          compat.paramAnnosForUpdate());
            default: // should be unreachable
                throw new IllegalStateException(info.type.name());
        }
    }

    /**
     * Raise an error because the PageRequest is missing.
     *
     * @param info query information for the repository method.
     * @throws IllegalArgumentException      if the user supplied a null PageRequest
     * @throws UnsupportedOperationException if the repository method signature
     *                                           lacks a parameter for supplying a
     *                                           PageRequest
     */
    static RuntimeException missingPageRequest(QueryInfo info) {
        Class<?>[] paramTypes = info.method.getParameterTypes();

        // Check parameter positions after those used for query parameters
        boolean signatureHasPageReq = false;
        for (int i = 0; i < paramTypes.length; i++)
            signatureHasPageReq |= PageRequest.class.equals(paramTypes[i]);

        if (signatureHasPageReq)
            // NullPointerException is required by BasicRepository.findAll
            throw exc(NullPointerException.class,
                      "CWWKD1087.null.param",
                      PageRequest.class.getName(),
                      info.method.getName(),
                      info.repositoryInterface.getName());
        else
            throw exc(UnsupportedOperationException.class,
                      "CWWKD1041.rtrn.mismatch.pagereq",
                      info.method.getName(),
                      info.repositoryInterface.getName(),
                      info.method.getGenericReturnType().getTypeName());
    }

    /**
     * Raises UnsupportedOperationException for the error where a repository
     * method intermixed named and positional parameters for a query.
     *
     * @param info          query information for the repository method.
     * @param methodNPCount count of repository method parameters that indicate
     *                          names and values of JPQL named parameters.
     * @throws the UnsupportedOperationException.
     */
    static UnsupportedOperationException mixedQLParamTypes(QueryInfo info,
                                                           int methodNPCount) {
        String firstNamedParam = null;
        StringBuilder allNamedParams = new StringBuilder().append('(');
        for (String name : info.jpqlParamNames) {
            if (firstNamedParam == null)
                firstNamedParam = name;
            else
                allNamedParams.append(", ");
            allNamedParams.append(':').append(name);
        }
        allNamedParams.append(')');

        Class<?> firstNamedParamType = String.class;
        for (Parameter p : info.method.getParameters()) {
            Param param = p.getAnnotation(Param.class);
            if (param == null //
                            ? p.isNamePresent() &&
                              firstNamedParam.equals(p.getName()) //
                            : firstNamedParam.equals(param.value()))
                firstNamedParamType = p.getType();
            break;
        }

        throw exc(UnsupportedOperationException.class,
                  "CWWKD1019.mixed.positional.named",
                  info.method.getName(),
                  info.repositoryInterface.getName(),
                  info.jpqlParamCount - methodNPCount,
                  methodNPCount,
                  allNamedParams,
                  info.method.getAnnotation(Query.class).value(),
                  ':' + firstNamedParam,
                  "@Param(\"" + firstNamedParam + "\")",
                  firstNamedParamType.getSimpleName() + ' ' + firstNamedParam);
    }

    /**
     * Raise a new NonUniqueResultException.
     *
     * @param info       query information for the repository method.
     * @param numResults number of results.
     * @throws the NonUniqueResultException.
     */
    static NonUniqueResultException nonUniqueResult(QueryInfo info,
                                                    int numResults) {

        String entityName = info.entityInfo.getType().getSimpleName();
        String returnType = "Optional<" + entityName + ">";

        List<String> recommendations = info.producer.compat().atLeast(1, 1) //
                        ? List.of("@Find @First @OrderBy(...) " +
                                  returnType + " get(...)",
                                  returnType + " findFirstByX(...)") //
                        : List.of(returnType + " findFirstByX(...)",
                                  returnType + " findByX(..., Limit.of(1))");

        throw exc(NonUniqueResultException.class,
                  "CWWKD1054.non.unique.result",
                  info.method.getName(),
                  info.repositoryInterface.getName(),
                  info.method.getGenericReturnType().getTypeName(),
                  numResults,
                  recommendations);
    }

    /**
     * Raises an error for a type conversion failure due to a value being outside of
     * the specified range.
     *
     * @param info  query information for the repository method.
     * @param value the value that fails to convert.
     * @param min   minimum value for range.
     * @param max   maximum value for range.
     * @throws MappingException for the type conversion failure.
     */
    static MappingException outOfRange(QueryInfo info,
                                       @Sensitive Number value,
                                       long min,
                                       long max) {
        throw exc(MappingException.class,
                  "CWWKD1047.result.out.of.range",
                  info.loggableAppend(value.getClass().getName(), " (", value, ")"),
                  info.method.getName(),
                  info.repositoryInterface.getName(),
                  info.method.getGenericReturnType().getTypeName(),
                  min,
                  max);
    }

    /**
     * Raises an UnsupportedOperationException for an error where the
     * repository method return type does not match the query results.
     * On reason this might happen is when EclipseLink returns wrong values
     * when selecting ElementCollection attributes instead of rejecting
     * it as unsupported.
     *
     * @param info    query information for the repository method.
     * @param results list of at least 1 result.
     * @param query   jakarta.persistence.Query, a String, or null.
     * @throws UnsupportedOperationException.
     */
    static UnsupportedOperationException resultIncompatible(QueryInfo info,
                                                            @Sensitive List<?> results,
                                                            Object query) {
        String r = results.getClass().getName() +
                   "<" + results.get(0).getClass().getName() + ">";

        if (query == null)
            throw exc(UnsupportedOperationException.class,
                      "CWWKD1102.incompat.query.result",
                      info.method.getName(),
                      info.repositoryInterface.getName(),
                      info.method.getGenericReturnType().getTypeName(),
                      r);
        else
            throw exc(UnsupportedOperationException.class,
                      "CWWKD1103.incompat.query.result",
                      info.method.getName(),
                      info.repositoryInterface.getName(),
                      info.method.getGenericReturnType().getTypeName(),
                      query instanceof String ? query : query.getClass().getName(),
                      r);
    }

    /**
     * Raises UnsupportedOperationException for the general error where
     * a repository method is unrecognized.
     *
     * @param info query information for the repository method.
     * @throws the UnsupportedOperationException.
     */
    static UnsupportedOperationException unsupportedMethod(QueryInfo info) {
        throw exc(UnsupportedOperationException.class,
                  "CWWKD1011.unknown.method.pattern",
                  info.method.getName(),
                  info.repositoryInterface.getName(),
                  Util.operationAnnoNames(info.producer),
                  Util.resourceAccessorTypeNames(info.producer),
                  Util.methodNamePrefixes(info.producer),
                  info.entityInfo.getExampleMethodNames());
    }

    /**
     * Raises MappingException for the error where the repository method
     * defines extra named parameters that are not used by the JPQL query.
     *
     * @throws MappingException.
     */
    static MappingException unusedNamedParamsOnMethod(QueryInfo info,
                                                      Set<String> extras,
                                                      Set<String> qlRequired) {
        String firstExtraParam = null;
        StringBuilder extraParamNames = new StringBuilder();
        for (String name : extras)
            if (name.length() > 0) {
                if (firstExtraParam == null)
                    firstExtraParam = name;
                else
                    extraParamNames.append(", ");
                extraParamNames.append(name);
            }

        if (firstExtraParam == null && !extras.isEmpty())
            // @Param("") with empty String is not valid
            throw exc(MappingException.class,
                      "CWWKD1104.empty.anno.value",
                      Param.class.getSimpleName(),
                      info.method.getName(),
                      info.repositoryInterface.getName());

        boolean isFirst = true;
        StringBuilder qlParamNames = new StringBuilder();
        for (String name : qlRequired) {
            if (!isFirst)
                qlParamNames.append(", ");
            qlParamNames.append(':').append(name);
            isFirst = false;
        }

        if (qlRequired.isEmpty())
            throw exc(MappingException.class,
                      "CWWKD1086.named.params.unused",
                      info.method.getName(),
                      info.repositoryInterface.getName(),
                      extraParamNames,
                      info.method.getAnnotation(Query.class).value(),
                      ':' + firstExtraParam);
        else
            throw exc(MappingException.class,
                      "CWWKD1085.extra.method.params",
                      info.method.getName(),
                      info.repositoryInterface.getName(),
                      extraParamNames,
                      qlParamNames,
                      info.method.getAnnotation(Query.class).value());
    }

    /**
     * Confirm that special parameters are positioned after all other parameters.
     *
     * @throws UnupportedOperationException if a special parameter is ahead of
     *                                          a query parameter.
     */
    private static void validateParameterPositions(QueryInfo info) {
        DataVersionCompatibility compat = info.entityInfo.builder.provider.compat;

        Class<?>[] paramTypes = info.method.getParameterTypes();
        Set<Class<?>> specParamTypes = compat.specialParamTypes();
        int specParamIndex = Integer.MAX_VALUE, otherParamIndex = -1;
        for (int i = 0; i < paramTypes.length; i++)
            if (specParamTypes.contains(paramTypes[i]))
                specParamIndex = i < specParamIndex ? i : specParamIndex;
            else
                otherParamIndex = i;

        if (specParamIndex < otherParamIndex)
            throw exc(UnsupportedOperationException.class,
                      "CWWKD1098.spec.param.position.err",
                      info.method.getName(),
                      info.repositoryInterface.getName(),
                      paramTypes[specParamIndex].getName(),
                      compat.specialParamsForFind());
    }

}
