/*******************************************************************************
 * Copyright (c) 2024,2026 IBM Corporation and others.
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
package io.openliberty.data.internal.v1_1;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.websphere.ras.annotation.Trivial;

import io.openliberty.data.internal.AttributeConstraint;
import io.openliberty.data.internal.QueryType;
import io.openliberty.data.internal.version.DataVersionCompatibility;
import io.openliberty.data.repository.Count;
import io.openliberty.data.repository.Exists;
import io.openliberty.data.repository.IgnoreCase;
import io.openliberty.data.repository.function.AbsoluteValue;
import io.openliberty.data.repository.function.CharCount;
import io.openliberty.data.repository.function.ElementCount;
import io.openliberty.data.repository.function.Extract;
import io.openliberty.data.repository.function.Rounded;
import io.openliberty.data.repository.function.Trimmed;
import io.openliberty.data.repository.update.Add;
import io.openliberty.data.repository.update.Assign;
import io.openliberty.data.repository.update.Divide;
import io.openliberty.data.repository.update.Multiply;
import io.openliberty.data.repository.update.SubtractFrom;
import jakarta.data.Limit;
import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.data.constraint.AtLeast;
import jakarta.data.constraint.AtMost;
import jakarta.data.constraint.Between;
import jakarta.data.constraint.Constraint;
import jakarta.data.constraint.EqualTo;
import jakarta.data.constraint.GreaterThan;
import jakarta.data.constraint.In;
import jakarta.data.constraint.LessThan;
import jakarta.data.constraint.Like;
import jakarta.data.constraint.NotBetween;
import jakarta.data.constraint.NotEqualTo;
import jakarta.data.constraint.NotIn;
import jakarta.data.constraint.NotLike;
import jakarta.data.constraint.NotNull;
import jakarta.data.constraint.Null;
import jakarta.data.expression.Expression;
import jakarta.data.metamodel.Attribute;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.By;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Is;
import jakarta.data.repository.Query;
import jakarta.data.repository.Save;
import jakarta.data.repository.Select;
import jakarta.data.repository.Update;
import jakarta.data.restrict.BasicRestriction;
import jakarta.data.restrict.CompositeRestriction;
import jakarta.data.restrict.Restriction;
import jakarta.data.spi.expression.literal.Literal;
import jakarta.persistence.EntityManager;

/**
 * Capability that is specific to the version of Jakarta Data.
 */
@Component(configurationPid = "io.openliberty.data.internal.version.1.1",
           configurationPolicy = ConfigurationPolicy.IGNORE,
           service = DataVersionCompatibility.class)
public class Data_1_1 implements DataVersionCompatibility {
    private static final TraceComponent tc = Tr.register(Data_1_1.class);

    private static final String FUNCTION_ANNO_PACKAGE = Rounded.class.getPackageName();

    private static final Map<String, String> FUNCTION_CALLS = new HashMap<>();
    static {
        FUNCTION_CALLS.put(AbsoluteValue.class.getSimpleName(), "ABS(");
        FUNCTION_CALLS.put(CharCount.class.getSimpleName(), "LENGTH(");
        FUNCTION_CALLS.put(ElementCount.class.getSimpleName(), "SIZE(");
        FUNCTION_CALLS.put(Rounded.Direction.DOWN.name(), "FLOOR(");
        FUNCTION_CALLS.put(Rounded.Direction.NEAREST.name(), "ROUND(");
        FUNCTION_CALLS.put(Rounded.Direction.UP.name(), "CEILING(");
        FUNCTION_CALLS.put(Trimmed.class.getSimpleName(), "TRIM(");
        FUNCTION_CALLS.put(Extract.Field.DAY.name(), "EXTRACT (DAY FROM ");
        FUNCTION_CALLS.put(Extract.Field.HOUR.name(), "EXTRACT (HOUR FROM ");
        FUNCTION_CALLS.put(Extract.Field.MINUTE.name(), "EXTRACT (MINUTE FROM ");
        FUNCTION_CALLS.put(Extract.Field.MONTH.name(), "EXTRACT (MONTH FROM ");
        FUNCTION_CALLS.put(Extract.Field.QUARTER.name(), "EXTRACT (QUARTER FROM ");
        FUNCTION_CALLS.put(Extract.Field.SECOND.name(), "EXTRACT (SECOND FROM ");
        FUNCTION_CALLS.put(Extract.Field.WEEK.name(), "EXTRACT (WEEK FROM ");
        FUNCTION_CALLS.put(Extract.Field.YEAR.name(), "EXTRACT (YEAR FROM ");
    }

    /**
     * Annotations that represent lifecycle operations that are allowed for
     * methods of a stateful repository.
     */
    private static final Set<Class<? extends Annotation>> LIFECYCLE_ANNOS_STATEFUL = //
                    Set.of(); // TODO 1.1 Detach, Merge, Persist, Refresh, Remove

    /**
     * Annotations that represent lifecycle operations that are allowed for
     * methods of a stateless repository.
     */
    private static final Set<Class<? extends Annotation>> LIFECYCLE_ANNOS_STATELESS = //
                    Set.of(Delete.class,
                           Insert.class,
                           Update.class,
                           Save.class);

    /**
     * Annotations that represent operations that are allowed for methods of a
     * stateful repository.
     */
    private static final Set<Class<? extends Annotation>> OP_ANNOS_STATEFUL = //
                    Set.of(Find.class,
                           // TODO 1.1 Merge, Persist, ...
                           Query.class);

    /**
     * Annotations that represent operations that are allowed for methods of a
     * stateless repository.
     */
    private static final Set<Class<? extends Annotation>> OP_ANNOS_STATELESS = //
                    Set.of(Delete.class,
                           Find.class,
                           Insert.class,
                           Query.class,
                           Save.class,
                           Update.class);

    /**
     * Classes that are valid as return types of resource accessor methods for a
     * stateful repository.
     */
    private static final Set<Class<?>> RESOURCE_ACCESSOR_CLASSES_STATEFUL = //
                    Set.of(Connection.class,
                           DataSource.class,
                           EntityManager.class);

    /**
     * Classes that are valid as return types of resource accessor methods for a
     * stateless repository.
     */
    private static final Set<Class<?>> RESOURCE_ACCESSOR_CLASSES_STATELESS = //
                    RESOURCE_ACCESSOR_CLASSES_STATEFUL; // TODO 1.1 entity agent

    /**
     * Types that are valid as repository method special parameters.
     */
    private static final Set<Class<?>> SPECIAL_PARAM_TYPES = //
                    Set.of(Limit.class, Order.class,
                           Sort.class, Sort[].class,
                           PageRequest.class,
                           Restriction.class);

    /**
     * Generate the name of a named parameter that supplies a Restriction value.
     *
     * @param jpqlParamCount parameter number to include in the generated name.
     * @param jpqlParamNames list of named parameter names to which to add the
     *                           generated name, which must not already be in the list.
     * @return
     */
    @Trivial
    private String addRestrictParam(int jpqlParamCount, Set<String> jpqlParamNames) {
        String paramName = "restrict" + jpqlParamCount;
        while (!jpqlParamNames.add(paramName))
            paramName += '_';
        return paramName;
    }

    @Override
    @Trivial
    public StringBuilder appendConstraint(StringBuilder q,
                                          String o_,
                                          String attrName,
                                          AttributeConstraint constraint,
                                          int qp,
                                          boolean isCollection,
                                          Annotation[] annos) {
        StringBuilder attributeExpr = new StringBuilder();

        List<Annotation> functionAnnos = new ArrayList<>();
        boolean ignoreCase = false;
        for (int a = annos.length - 1; a >= 0; a--) {
            if (annos[a] instanceof IgnoreCase) {
                ignoreCase = true;
            } else {
                String annoPackage = annos[a].annotationType().getPackageName();
                if (FUNCTION_ANNO_PACKAGE.equals(annoPackage)) {
                    functionAnnos.add(annos[a]);
                    String functionType = annos[a] instanceof Extract //
                                    ? ((Extract) annos[a]).value().name() //
                                    : annos[a] instanceof Rounded //
                                                    ? ((Rounded) annos[a]).value().name() //
                                                    : annos[a].annotationType().getSimpleName();
                    String functionCall = FUNCTION_CALLS.get(functionType);
                    attributeExpr.append(functionCall);
                }
            }
        }

        boolean negated = constraint.isNegative();
        AttributeConstraint baseConstraint = negated //
                        ? constraint.negate() //
                        : constraint;

        if (ignoreCase)
            attributeExpr.append("LOWER(");

        if (attrName.charAt(attrName.length() - 1) != ')')
            attributeExpr.append(o_);

        attributeExpr.append(attrName);

        if (ignoreCase)
            attributeExpr.append(')');

        for (Annotation anno : functionAnnos) {
            if (anno instanceof Rounded && ((Rounded) anno).value() == Rounded.Direction.NEAREST)
                attributeExpr.append(", 0)"); // round to zero digits beyond the decimal
            else
                attributeExpr.append(')');
        }

        if (isCollection)
            if (ignoreCase ||
                baseConstraint != AttributeConstraint.Equal) // TODO also have an operation for collection containing?
                throw new UnsupportedOperationException("The " + constraint.constraintName() +
                                                        " constraint that is applied to entity attribute " +
                                                        attrName +
                                                        " is not supported for collection attributes."); // TODO NLS (future)

        switch (baseConstraint) {
            case Equal:
            case GreaterThan:
            case GreaterThanEqual:
            case LessThan:
            case LessThanEqual:
                q.append(attributeExpr).append(constraint.operator());
                appendParam(q, ignoreCase, qp);
                break;
            case Between:
                q.append(attributeExpr).append(constraint.operator());
                appendParam(q, ignoreCase, qp);
                q.append(" AND ");
                appendParam(q, ignoreCase, qp + 1);
                break;
            case In:
                if (ignoreCase)
                    throw new UnsupportedOperationException(); // should be unreachable
                q.append(attributeExpr).append(constraint.operator());
                appendParam(q, ignoreCase, qp);
                break;
            // TODO 1.1: escape characters and custom wildcards
            case Like:
                q.append(attributeExpr).append(constraint.operator());
                appendParam(q, ignoreCase, qp);
                break;
            case LikeEscaped:
                q.append(attributeExpr).append(constraint.operator());
                appendParam(q, ignoreCase, qp);
                q.append(" ESCAPE ");
                appendParam(q, false, qp + 1);
                break;
            case Null:
                q.append(attributeExpr).append(constraint.operator());
                break;
            case Contains:
                q.append(attributeExpr) //
                                .append(negated ? " NOT" : "") //
                                .append(" LIKE CONCAT('%', ");
                appendParam(q, ignoreCase, qp).append(", '%')");
                break;
            case EndsWith:
                q.append(attributeExpr) //
                                .append(negated ? " NOT" : "") //
                                .append(" LIKE CONCAT('%', ");
                appendParam(q, ignoreCase, qp).append(')');
                break;
            case StartsWith:
                q.append(attributeExpr) //
                                .append(negated ? " NOT" : "") //
                                .append(" LIKE CONCAT(");
                appendParam(q, ignoreCase, qp).append(", '%')");
                break;
            // TODO operation for collection containing?
            //case ???:
            //    q.append(" ?").append(qp) //
            //                    .append(negated ? " NOT" : "") //
            //                    .append(" MEMBER OF ").append(attributeExpr);
            //    break;
            default:
                throw new UnsupportedOperationException(constraint.constraintName());
        }

        return q;
    }

    /**
     * Appends JQPL for a repository method parameter. Either of the form ?1 or LOWER(?1)
     *
     * @param q     builder for the JPQL query.
     * @param lower indicates if the query parameter should be compared in lower case.
     * @param num   parameter number.
     * @return the same builder for the JPQL query.
     */
    @Trivial
    private static StringBuilder appendParam(StringBuilder q, boolean lower, int num) {
        q.append(lower ? "LOWER(?" : '?').append(num);
        return lower ? q.append(')') : q;
    }

    @Override
    @Trivial
    public boolean atLeast(int major, int minor) {
        return major == 1 && minor <= 1;
    }

    /**
     * Appends JPQL to the partially built query to implement a Restriction
     * parameter of a repository method.
     *
     * @param q              partially built query ending with the WHERE clause.
     * @param entityVar_     entity identifier variable name and . character.
     * @param restriction    value of Restriction parameter. Otherwise null.
     * @param jpqlParamCount number of named or positional parameters in the
     *                           partially built query.
     * @param jpqlParamNames names of named parameters in the partially bulit
     *                           query. Empty if the query uses positional
     *                           parameeters or has none. If using named parameters,
     *                           this method should add any that are generated for
     *                           the restriction part of the query.
     * @param qrParams       initially empty list for this method to populate
     *                           with the name of named parameters or index of
     *                           positional parameters, mapped to value, for each
     *                           value obtained from the processed Restriction(s).
     * @return the new count of named or positional parameters, including any that
     *         were generated for the Restriction(s).
     */
    @Override
    // TODO @Trivial // avoid tracing values found in Restriction.toString()
    public int generateRestrictions(StringBuilder q,
                                    String entityVar_,
                                    Object restriction,
                                    int jpqlParamCount,
                                    Set<String> jpqlParamNames,
                                    Map<Object, Object> qrParams) {

        if (restriction instanceof BasicRestriction<?, ?> r) {
            Expression<?, ?> exp = r.expression();
            Constraint<?> constraint = r.constraint();
            AttributeConstraint c;
            // TODO replace the following shortcut:
            if (constraint instanceof Like)
                c = AttributeConstraint.LikeEscaped;
            else if (constraint instanceof NotLike)
                c = AttributeConstraint.NotLikeEscaped;
            else
                c = toAttributeConstraint(null, constraint.getClass().getInterfaces()[0]);

            int numQueryParamsToAdd = c.numMethodParams();
            boolean positionalParams = jpqlParamNames.isEmpty();

            if (exp instanceof Attribute attr)
                q.append(entityVar_).append(attr.name());
            else
                throw new UnsupportedOperationException(exp.getClass().getName()); // TODO other types of Expression

            q.append(c.operator());

            // TODO first determine if we should even write a value here, or if it is an expression
            if (numQueryParamsToAdd == 1) {
                jpqlParamCount++;
                if (positionalParams) {
                    q.append('?').append(jpqlParamCount);
                    qrParams.put(jpqlParamCount, toConstraintValues(constraint)[0]);
                } else {
                    String paramName = addRestrictParam(jpqlParamCount, jpqlParamNames);
                    q.append(':').append(paramName);
                    qrParams.put(paramName, toConstraintValues(constraint)[0]);
                }
            } else if (numQueryParamsToAdd == 2) {
                Object[] values = toConstraintValues(constraint);

                jpqlParamCount++;
                if (positionalParams) {
                    q.append('?').append(jpqlParamCount);
                    qrParams.put(jpqlParamCount, values[0]);
                } else {
                    String paramName = addRestrictParam(jpqlParamCount, jpqlParamNames);
                    q.append(':').append(paramName);
                    qrParams.put(paramName, values[0]);
                }

                if (c == AttributeConstraint.LikeEscaped ||
                    c == AttributeConstraint.NotLikeEscaped)
                    q.append(" ESCAPE "); // [NOT] LIKE ?1 ESCAPE ?2
                else
                    q.append(" AND "); // [NOT] BETWEEN ?1 AND ?2

                jpqlParamCount++;
                if (positionalParams) {
                    q.append('?').append(jpqlParamCount);
                    qrParams.put(jpqlParamCount, values[1]);
                } else {
                    String paramName = addRestrictParam(jpqlParamCount, jpqlParamNames);
                    q.append(':').append(paramName);
                    qrParams.put(paramName, values[1]);
                }
            }
        } else if (restriction instanceof CompositeRestriction<?> r) {
            q.append(r.isNegated() ? "NOT (" : "(");
            boolean all = r.type() == CompositeRestriction.Type.ALL;
            List<?> rr = r.restrictions();
            int count = rr.size();
            if (count == 0)
                q.append(all ? "TRUE" : "FALSE");
            else // one or more
                for (int i = 0; i < count; i++) {
                    if (i > 0)
                        q.append(all ? " AND " : " OR ");

                    jpqlParamCount = generateRestrictions(q,
                                                          entityVar_,
                                                          rr.get(i),
                                                          jpqlParamCount,
                                                          jpqlParamNames,
                                                          qrParams);
                }
            q.append(')');
        } else {
            throw new IllegalArgumentException("Unsupported Restriction type: " +
                                               restriction.getClass().getName());
        }

        return jpqlParamCount;
    }

    @Override
    @Trivial
    public Annotation getCountAnnotation(Method method) {
        return method.getAnnotation(Count.class);
    }

    @Override
    @Trivial
    public Class<?> getEntityClass(Find find) {
        return find.value();
    }

    @Override
    @Trivial
    public Annotation getExistsAnnotation(Method method) {
        return method.getAnnotation(Exists.class);
    }

    @Override
    @Trivial
    public String[] getSelections(AnnotatedElement element) {
        Select[] selects = element.getAnnotationsByType(Select.class);
        if (selects.length == 0)
            return NO_SELECTIONS;
        String[] values = new String[selects.length];
        for (int i = 0; i < selects.length; i++)
            values[i] = selects[i].value();
        return values;
    }

    @Override
    @Trivial
    public String[] getUpdateAttributeAndOperation(Annotation[] annos) {
        final boolean trace = TraceComponent.isAnyTracingEnabled();

        String[] returnValue = null;
        for (Annotation anno : annos)
            if (anno instanceof Assign) {
                returnValue = new String[] { ((Assign) anno).value(), "=" };
                break;
            } else if (anno instanceof Add) {
                returnValue = new String[] { ((Add) anno).value(), "+" };
                break;
            } else if (anno instanceof Multiply) {
                returnValue = new String[] { ((Multiply) anno).value(), "*" };
                break;
            } else if (anno instanceof Divide) {
                returnValue = new String[] { ((Divide) anno).value(), "/" };
                break;
            } else if (anno instanceof SubtractFrom) {
                returnValue = new String[] { ((SubtractFrom) anno).value(), "-" };
                break;
            }

        if (trace && tc.isDebugEnabled()) {
            Object[] aa = new Object[annos.length];
            for (int a = 0; a < annos.length; a++)
                aa[a] = annos[a] == null ? null : annos[a].annotationType().getName();
            Tr.debug(this, tc, "getUpdateAttributeAndOperation", aa, returnValue);
        }
        return returnValue;
    }

    @Override
    public int inspectMethodParam(int p,
                                  Class<?> paramType,
                                  Annotation[] paramAnnos,
                                  String[] attrNames,
                                  AttributeConstraint[] constraints,
                                  char[] updateOps,
                                  int qpNext) {
        int qpOriginal = qpNext;

        for (Annotation anno : paramAnnos)
            if (anno instanceof Is) {
                constraints[p] = toAttributeConstraint(((Is) anno).value(), paramType);
            } else if (anno instanceof Assign) {
                attrNames[p] = ((Assign) anno).value();
                updateOps[p] = '=';
                qpNext++;
            } else if (anno instanceof Add) {
                attrNames[p] = ((Add) anno).value();
                updateOps[p] = '+';
                qpNext++;
            } else if (anno instanceof Multiply) {
                attrNames[p] = ((Multiply) anno).value();
                updateOps[p] = '*';
                qpNext++;
            } else if (anno instanceof Divide) {
                attrNames[p] = ((Divide) anno).value();
                updateOps[p] = '/';
                qpNext++;
            } else if (anno instanceof SubtractFrom) {
                attrNames[p] = ((SubtractFrom) anno).value();
                updateOps[p] = '-';
                qpNext++;
            }

        if (constraints[p] == null && Constraint.class.isAssignableFrom(paramType)) {
            constraints[p] = toAttributeConstraint(null, paramType);
        }

        if (qpNext == qpOriginal) {
            if (constraints[p] == null)
                constraints[p] = AttributeConstraint.Equal;

            // no annotation indicating a constraint or update
            if (false) { // TODO 1.1 check if paramType is a Constraint
                // qpNext increment will vary by Constraint subtype
                // TODO 1.1: if Constraint.class and generated upfront,
                // qpNext = PARAM_CONSTRAINT_DEFERRED;
            } else {
                qpNext += constraints[p].numMethodParams();
            }
        } else if (qpNext - qpOriginal > 1) {
            // TODO possibly allow a redundant Constraint that matches the Is annotation.
            qpNext = PARAM_ANNOS_CONFLICT;
        } else if (false) { // TODO 1.1 check if paramType is a Constraint
            qpNext = PARAM_ANNO_CONFLICTS_WITH_CONSTRAINT;
        }

        return qpNext;
    }

    @Override
    @Trivial
    public boolean isRestriction(Object param) {
        return param instanceof Restriction;
    }

    @Override
    @Trivial
    public boolean isSpecialParamValid(Class<?> paramType,
                                       QueryType queryType) {
        return switch (queryType) {
            case FIND -> true;
            case FIND_AND_DELETE -> !PageRequest.class.equals(paramType);
            case COUNT, EXISTS -> Order.class.equals(paramType) ||
                                  Restriction.class.equals(paramType) ||
                                  Sort.class.equals(paramType) ||
                                  Sort[].class.equals(paramType);
            case QM_DELETE -> Restriction.class.equals(paramType);
            case QM_UPDATE -> false; // TODO FUTURE same as QM_DELETE
            default -> false;
        };
    }

    @Override
    @Trivial
    public Set<Class<? extends Annotation>> lifeCycleAnnoTypes(boolean stateful) {
        return stateful ? LIFECYCLE_ANNOS_STATEFUL : LIFECYCLE_ANNOS_STATELESS;
    }

    @Override
    @Trivial
    public Set<Class<? extends Annotation>> operationAnnoTypes(boolean stateful) {
        return stateful ? OP_ANNOS_STATEFUL : OP_ANNOS_STATELESS;
    }

    @Override
    @Trivial
    public String paramAnnosForUpdate() {
        // TODO 1.1
        return By.class.getSimpleName() + ", " +
               Add.class.getSimpleName() + ", " +
               Assign.class.getSimpleName() + ", " +
               Divide.class.getSimpleName() + ", " +
               Multiply.class.getSimpleName() + ", " +
               SubtractFrom.class.getSimpleName();
    }

    @Override
    @Trivial
    public String persistenceFeatureName() {
        return "persistence-4.0";
    }

    @Override
    @Trivial
    public Set<Class<?>> resourceAccessorTypes(boolean stateful) {
        return stateful ? RESOURCE_ACCESSOR_CLASSES_STATEFUL //
                        : RESOURCE_ACCESSOR_CLASSES_STATELESS;
    }

    @Override
    @Trivial
    public String specialParamsForFind() {
        return "Limit, Order, Sort, Sort[], PageRequest, Restriction";
    }

    @Override
    @Trivial
    public String specialParamsForFindAndDelete() {
        return "Limit, Order, Sort, Sort[], Restriction";
    }

    @Override
    @Trivial
    public Set<Class<?>> specialParamTypes() {
        return SPECIAL_PARAM_TYPES;
    }

    /**
     * Convert a constraint subtype to its AttributeConstraint representation.
     *
     * @param isAnnoConstraintType subtype of Constraint indicated by Is anno.
     *                                 Otherwise null.
     * @param methodParamType      repository method parameter type.
     * @return AttributeConstraint representation.
     */
    private static AttributeConstraint toAttributeConstraint(Class<?> isAnnoConstraintType,
                                                             Class<?> methodParamType) {
        Class<?> type = isAnnoConstraintType == null ||
                        Constraint.class.isAssignableFrom(methodParamType) //
                                        ? methodParamType //
                                        : isAnnoConstraintType;

        if (isAnnoConstraintType != null && type != isAnnoConstraintType)
            ; // TODO 1.1 error for collisions

        AttributeConstraint constraint;
        if (AtLeast.class.equals(type))
            constraint = AttributeConstraint.GreaterThanEqual;
        else if (AtMost.class.equals(type))
            constraint = AttributeConstraint.LessThanEqual;
        else if (Between.class.equals(type))
            constraint = AttributeConstraint.Between;
        else if (EqualTo.class.equals(type))
            constraint = AttributeConstraint.Equal;
        else if (GreaterThan.class.equals(type))
            constraint = AttributeConstraint.GreaterThan;
        else if (In.class.equals(type))
            constraint = AttributeConstraint.In;
        else if (LessThan.class.equals(type))
            constraint = AttributeConstraint.LessThan;
        else if (Like.class.equals(type))
            constraint = Like.class.equals(methodParamType) //
                            ? AttributeConstraint.LikeEscaped //
                            : AttributeConstraint.Like;
        else if (NotBetween.class.equals(type))
            constraint = AttributeConstraint.NotBetween;
        else if (NotEqualTo.class.equals(type))
            constraint = AttributeConstraint.Not;
        else if (NotIn.class.equals(type))
            constraint = AttributeConstraint.NotIn;
        else if (NotLike.class.equals(type))
            constraint = NotLike.class.equals(methodParamType) //
                            ? AttributeConstraint.NotLikeEscaped //
                            : AttributeConstraint.NotLike;
        else if (NotNull.class.equals(type))
            constraint = AttributeConstraint.NotNull;
        else if (Null.class.equals(type))
            constraint = AttributeConstraint.Null;
        else
            // TODO 1.1 if isAnnoConstraintType == null handle generic Constraint else
            throw new UnsupportedOperationException("Constraint: " + type.getName()); // TODO NLS

        // TODO 1.1: errors for types the Is annotation cannot support

        return constraint;
    }

    @Override
    @Trivial // avoid logging customer data
    public Object[] toConstraintValues(Object constraintOrValue) {
        // TODO 1.1 this is not the correct implementation (doesn't account for
        // other types of expressions than literals) and is only here temporarily
        // so that we can complete remove some experimental code elsewhere without
        // breaking tests.
        boolean isList = false;
        Object[] values;
        if (constraintOrValue instanceof AtLeast c)
            values = new Object[] { c.bound() };
        else if (constraintOrValue instanceof AtMost c)
            values = new Object[] { c.bound() };
        else if (constraintOrValue instanceof Between c)
            values = new Object[] { c.lowerBound(), c.upperBound() };
        else if (constraintOrValue instanceof EqualTo c)
            values = new Object[] { c.expression() };
        else if (constraintOrValue instanceof GreaterThan c)
            values = new Object[] { c.bound() };
        else if (isList = constraintOrValue instanceof In)
            values = ((In) constraintOrValue).expressions().toArray();
        else if (constraintOrValue instanceof LessThan c)
            values = new Object[] { c.bound() };
        else if (constraintOrValue instanceof Like c)
            values = new Object[] { c.pattern(), c.escape() };
        else if (constraintOrValue instanceof NotBetween c)
            values = new Object[] { c.lowerBound(), c.upperBound() };
        else if (constraintOrValue instanceof NotEqualTo c)
            values = new Object[] { c.expression() };
        else if (isList = constraintOrValue instanceof NotIn)
            values = ((NotIn) constraintOrValue).expressions().toArray();
        else if (constraintOrValue instanceof NotLike c)
            values = new Object[] { c.pattern(), c.escape() };
        else if (constraintOrValue instanceof NotNull ||
                 constraintOrValue instanceof Null)
            values = new Object[0];
        else if (constraintOrValue instanceof Constraint)
            throw new UnsupportedOperationException("Constraint: " +
                                                    constraintOrValue.getClass().getName());
        else
            return null;

        for (int i = 0; i < values.length; i++)
            if (values[i] instanceof Literal)
                values[i] = ((Literal) values[i]).value();
            else if (values[i] instanceof Character)
                ; // the escape character for Like and NotLike
            else
                throw new UnsupportedOperationException(values[i].getClass().getName());

        if (isList)
            values = new Object[] { List.of(values) };

        return values;
    }
}