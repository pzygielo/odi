/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.odi.cdi;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import org.eclipse.odi.cdi.annotation.OdiBeanType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Internal
public final class OdiTypeUtils {

    private OdiTypeUtils() {
    }

    static Type getRequiredType(Argument<?> argument) {
        List<AnnotationValue<OdiBeanType>> metadataTypes = argument.getAnnotationMetadata().getAnnotationValuesByType(OdiBeanType.class);
        Type resolvedArgumentType = toType(argument);
        if (metadataTypes.isEmpty()) {
            return null;
        }
        for (AnnotationValue<OdiBeanType> metadataType : metadataTypes) {
            Class<?> rawType = metadataType.classValue().orElse(null);
            if (rawType == argument.getType()) {
                return selectRequiredType(toType(metadataType), resolvedArgumentType);
            }
        }
        return selectRequiredType(toType(metadataTypes.get(0)), resolvedArgumentType);
    }

    public static Type getArgumentType(Argument<?> argument) {
        Type requiredType = getRequiredType(argument);
        return requiredType == null ? toType(argument) : requiredType;
    }

    public static Type getEventType(Argument<?> argument) {
        Type metadataType = getMetadataType(argument);
        if (metadataType != null) {
            return metadataType;
        }
        if (hasOnlyRawTypeVariables(argument)) {
            return argument.getType();
        }
        return toType(argument);
    }

    private static Type getMetadataType(Argument<?> argument) {
        List<AnnotationValue<OdiBeanType>> metadataTypes = argument.getAnnotationMetadata().getAnnotationValuesByType(OdiBeanType.class);
        if (metadataTypes.isEmpty()) {
            return null;
        }
        for (AnnotationValue<OdiBeanType> metadataType : metadataTypes) {
            Class<?> rawType = metadataType.classValue().orElse(null);
            if (rawType == argument.getType()) {
                return toType(metadataType);
            }
        }
        return toType(metadataTypes.get(0));
    }

    private static Type selectRequiredType(Type metadataType, Type resolvedArgumentType) {
        if (containsTypeVariable(metadataType)
                && resolvedArgumentType != null
                && !containsTypeVariable(resolvedArgumentType)
                && isSameRawType(metadataType, resolvedArgumentType)
                && hasOnlyUnboundedTypeVariables(metadataType)) {
            return resolvedArgumentType;
        }
        return metadataType;
    }

    private static Type toType(Argument<?> argument) {
        Argument<?>[] typeParameters = argument.getTypeParameters();
        Type[] arguments = new Type[typeParameters.length];
        for (int i = 0; i < typeParameters.length; i++) {
            arguments[i] = toType(typeParameters[i]);
        }
        if (argument.isTypeVariable()) {
            Type bound = arguments.length == 0 ? argument.getType() : new OdiParameterizedType(argument.getType(), arguments);
            return new OdiTypeVariable(argument.getName(), new Type[]{bound});
        }
        if (typeParameters.length == 0) {
            return argument.getType();
        }
        return new OdiParameterizedType(argument.getType(), arguments);
    }

    static Set<Type> getBeanTypes(AnnotationMetadata annotationMetadata) {
        List<AnnotationValue<OdiBeanType>> metadataTypes = annotationMetadata.getAnnotationValuesByType(OdiBeanType.class);
        if (metadataTypes.isEmpty()) {
            return Set.of();
        }
        Set<Type> types = new LinkedHashSet<>();
        for (AnnotationValue<OdiBeanType> metadataType : metadataTypes) {
            Type type = toType(metadataType);
            if (type != Object.class) {
                types.add(type);
            }
        }
        types.add(Object.class);
        return types;
    }

    static Set<Type> getBeanTypes(AnnotationMetadata annotationMetadata, Class<?> beanType) {
        Set<Type> types = getBeanTypes(annotationMetadata);
        if (types.isEmpty()) {
            return types;
        }
        return types.stream()
                .filter(type -> isBeanTypeForBean(type, beanType))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean isBeanTypeForBean(Type type, Class<?> beanType) {
        Class<?> rawType = rawType(type);
        return rawType == null || rawType == Object.class || rawType.isAssignableFrom(beanType);
    }

    private static Type toType(AnnotationValue<OdiBeanType> beanType) {
        Class<?> rawType = beanType.classValue().orElse(null);
        if (rawType == null) {
            return Object.class;
        }
        Class<?>[] arguments = beanType.classValues("arguments");
        int[] argumentCounts = beanType.intValues("argumentCounts");
        boolean[] typeVariables = beanType.booleanValues("typeVariables");
        boolean[] wildcards = beanType.booleanValues("wildcards");
        int[] lowerBoundCounts = beanType.intValues("lowerBoundCounts");
        String[] typeVariableNames = beanType.stringValues("typeVariableNames");
        return toType(rawType, arguments, argumentCounts, typeVariables, wildcards, lowerBoundCounts, typeVariableNames);
    }

    private static Type toType(Class<?> rawType,
                               Class<?>[] argumentTypes,
                               int[] argumentCounts,
                               boolean[] typeVariables,
                               boolean[] wildcards,
                               int[] lowerBoundCounts,
                               String[] typeVariableNames) {
        if (argumentTypes.length == 0) {
            return rawType;
        }
        if (argumentCounts.length == argumentTypes.length) {
            TypeArgumentReader reader = new TypeArgumentReader(argumentTypes, argumentCounts, typeVariables, wildcards, lowerBoundCounts, typeVariableNames);
            return new OdiParameterizedType(rawType, reader.readAll());
        }
        Type[] arguments = new Type[argumentTypes.length];
        System.arraycopy(argumentTypes, 0, arguments, 0, argumentTypes.length);
        return new OdiParameterizedType(rawType, arguments);
    }

    static boolean matchesBeanType(Type requiredType, Set<Type> beanTypes) {
        if (requiredType == Object.class) {
            return true;
        }
        for (Type beanType : beanTypes) {
            if (isLegalBeanType(beanType) && isBeanTypeAssignable(requiredType, beanType)) {
                return true;
            }
        }
        return false;
    }

    static boolean isBeanTypeAssignable(Type requiredType, Type beanType) {
        if (isSameType(requiredType, beanType)) {
            return true;
        }
        if (requiredType instanceof Class<?>) {
            Class<?> requiredClass = (Class<?>) requiredType;
            if (beanType instanceof Class<?>) {
                return requiredClass.equals(beanType);
            }
            if (beanType instanceof ParameterizedType) {
                ParameterizedType beanParameterized = (ParameterizedType) beanType;
                if (!requiredClass.equals(rawType(beanParameterized))) {
                    return false;
                }
                for (Type beanArgument : beanParameterized.getActualTypeArguments()) {
                    if (!isParameterizedBeanTypeAssignableToRaw(beanArgument)) {
                        return false;
                    }
                }
                return true;
            }
        }
        if (requiredType instanceof ParameterizedType) {
            ParameterizedType requiredParameterized = (ParameterizedType) requiredType;
            Class<?> requiredRawType = rawType(requiredParameterized);
            if (requiredRawType == null) {
                return false;
            }
            if (beanType instanceof Class<?>) {
                if (!requiredRawType.equals(beanType)) {
                    return false;
                }
                for (Type requiredArgument : requiredParameterized.getActualTypeArguments()) {
                    if (!isRawBeanTypeAssignableTo(requiredArgument)) {
                        return false;
                    }
                }
                return true;
            }
            if (beanType instanceof ParameterizedType) {
                ParameterizedType beanParameterized = (ParameterizedType) beanType;
                if (!requiredRawType.equals(rawType(beanParameterized))) {
                    return false;
                }
                Type[] requiredArguments = requiredParameterized.getActualTypeArguments();
                Type[] beanArguments = beanParameterized.getActualTypeArguments();
                if (requiredArguments.length != beanArguments.length) {
                    return false;
                }
                for (int i = 0; i < requiredArguments.length; i++) {
                    if (!isBeanTypeArgumentAssignable(requiredArguments[i], beanArguments[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public static boolean isEventAssignable(Argument<?> observedArgument, Argument<?> eventArgument) {
        return isEventAssignable(getEventType(observedArgument), getArgumentType(eventArgument));
    }

    public static Type resolveSuperType(Argument<?> argument, Class<?> superType) {
        Type eventType = getArgumentType(argument);
        Class<?> rawType = rawType(eventType);
        if (rawType == null || rawType.isArray() || !superType.isAssignableFrom(rawType)) {
            return null;
        }
        Map<TypeVariable<?>, Type> substitutions = new HashMap<>();
        collectTypeSubstitutions(rawType, eventType, substitutions);
        return resolveSuperType(rawType, superType, eventType, substitutions);
    }

    public static Type resolveTypeVariables(Type type, Map<String, Type> substitutions) {
        if (substitutions.isEmpty()) {
            return type;
        }
        return substituteTypeVariablesByName(type, substitutions);
    }

    public static boolean isEventAssignable(Type observedType, Type eventType) {
        if (isSameType(observedType, eventType)) {
            return true;
        }
        if (isTypeVariable(observedType)) {
            return satisfiesBounds(eventType, typeVariableBounds(observedType));
        }
        if (observedType instanceof Class<?>) {
            Class<?> eventClass = rawType(eventType);
            return eventClass != null && ((Class<?>) observedType).isAssignableFrom(eventClass);
        }
        if (observedType instanceof ParameterizedType) {
            ParameterizedType observedParameterized = (ParameterizedType) observedType;
            Class<?> observedRaw = rawType(observedParameterized);
            Class<?> eventRaw = rawType(eventType);
            if (observedRaw == null || eventRaw == null || !observedRaw.isAssignableFrom(eventRaw)) {
                return false;
            }
            if (!(eventType instanceof ParameterizedType)) {
                return false;
            }
            ParameterizedType eventParameterized = (ParameterizedType) eventType;
            if (!observedRaw.equals(rawType(eventParameterized))) {
                return false;
            }
            Type[] observedArguments = observedParameterized.getActualTypeArguments();
            Type[] eventArguments = eventParameterized.getActualTypeArguments();
            if (observedArguments.length != eventArguments.length) {
                return false;
            }
            for (int i = 0; i < observedArguments.length; i++) {
                if (!isEventArgumentAssignable(observedArguments[i], eventArguments[i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean isEventArgumentAssignable(Type observedArgument, Type eventArgument) {
        if (isSameType(observedArgument, eventArgument)) {
            return true;
        }
        if (observedArgument instanceof WildcardType) {
            return isEventArgumentAssignableToWildcard((WildcardType) observedArgument, eventArgument);
        }
        if (isTypeVariable(observedArgument)) {
            return satisfiesBounds(eventArgument, typeVariableBounds(observedArgument));
        }
        if (observedArgument instanceof ParameterizedType && eventArgument instanceof ParameterizedType) {
            return isEventAssignable(observedArgument, eventArgument);
        }
        return false;
    }

    private static boolean isEventArgumentAssignableToWildcard(WildcardType wildcard, Type eventArgument) {
        for (Type upperBound : wildcard.getUpperBounds()) {
            if (upperBound == Object.class) {
                continue;
            }
            if (!isTypeAssignable(eventArgument, upperBound)) {
                return false;
            }
        }
        for (Type lowerBound : wildcard.getLowerBounds()) {
            if (!isTypeAssignable(lowerBound, eventArgument)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isParameterizedBeanTypeAssignableToRaw(Type beanArgument) {
        if (beanArgument == Object.class) {
            return true;
        }
        return isTypeVariable(beanArgument) && hasOnlyObjectUpperBound(beanArgument);
    }

    private static boolean isRawBeanTypeAssignableTo(Type requiredArgument) {
        if (requiredArgument == Object.class) {
            return true;
        }
        if (isTypeVariable(requiredArgument)) {
            return hasOnlyObjectUpperBound(requiredArgument);
        }
        if (requiredArgument instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) requiredArgument;
            return wildcard.getLowerBounds().length == 0 && hasOnlyObjectUpperBounds(wildcard.getUpperBounds());
        }
        return false;
    }

    private static boolean isBeanTypeArgumentAssignable(Type requiredArgument, Type beanArgument) {
        if (isSameType(requiredArgument, beanArgument)) {
            return true;
        }
        if (requiredArgument instanceof WildcardType) {
            return isBeanArgumentAssignableToWildcard((WildcardType) requiredArgument, beanArgument);
        }
        if (isTypeVariable(beanArgument)) {
            return satisfiesBounds(requiredArgument, typeVariableBounds(beanArgument));
        }
        if (isTypeVariable(requiredArgument)) {
            return satisfiesBounds(beanArgument, typeVariableBounds(requiredArgument));
        }
        if (requiredArgument instanceof ParameterizedType && beanArgument instanceof ParameterizedType) {
            return isBeanTypeAssignable(requiredArgument, beanArgument);
        }
        return false;
    }

    private static boolean isBeanArgumentAssignableToWildcard(WildcardType wildcard, Type beanArgument) {
        for (Type upperBound : wildcard.getUpperBounds()) {
            if (upperBound == Object.class) {
                continue;
            }
            if (isTypeVariable(beanArgument)) {
                if (!typeVariableBoundsOverlap(upperBound, beanArgument)) {
                    return false;
                }
            } else if (!isTypeAssignable(beanArgument, upperBound)) {
                return false;
            }
        }
        for (Type lowerBound : wildcard.getLowerBounds()) {
            if (isTypeVariable(beanArgument)) {
                if (!satisfiesBounds(lowerBound, typeVariableBounds(beanArgument))) {
                    return false;
                }
            } else if (!isTypeAssignable(lowerBound, beanArgument)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTypeAssignable(Type candidate, Type requiredBound) {
        if (isSameType(candidate, requiredBound) || requiredBound == Object.class) {
            return true;
        }
        if (isTypeVariable(candidate)) {
            for (Type candidateBound : typeVariableBounds(candidate)) {
                if (isTypeAssignable(candidateBound, requiredBound)) {
                    return true;
                }
            }
            return false;
        }
        if (isTypeVariable(requiredBound)) {
            return satisfiesBounds(candidate, typeVariableBounds(requiredBound));
        }
        if (candidate instanceof ParameterizedType && requiredBound instanceof ParameterizedType) {
            return isBeanTypeAssignable(requiredBound, candidate);
        }
        Class<?> candidateClass = rawType(candidate);
        Class<?> requiredClass = rawType(requiredBound);
        return candidateClass != null && requiredClass != null && requiredClass.isAssignableFrom(candidateClass);
    }

    private static boolean satisfiesBounds(Type type, Type[] bounds) {
        for (Type bound : bounds) {
            if (!isTypeAssignable(type, bound)) {
                return false;
            }
        }
        return true;
    }

    private static boolean typeVariableBoundsOverlap(Type requiredBound, Type variable) {
        Type[] variableBounds = typeVariableBounds(variable);
        for (Type variableBound : variableBounds) {
            if (isTypeAssignable(variableBound, requiredBound)) {
                return true;
            }
        }
        return satisfiesBounds(requiredBound, variableBounds);
    }

    private static boolean hasOnlyObjectUpperBound(Type variable) {
        return hasOnlyObjectUpperBounds(typeVariableBounds(variable));
    }

    private static boolean hasOnlyObjectUpperBounds(Type[] bounds) {
        return bounds.length == 0 || (bounds.length == 1 && bounds[0] == Object.class);
    }

    private static boolean isTypeVariable(Type type) {
        return type instanceof TypeVariable<?>
                || (type instanceof Argument<?> && ((Argument<?>) type).isTypeVariable());
    }

    private static Type[] typeVariableBounds(Type type) {
        if (type instanceof TypeVariable<?>) {
            return ((TypeVariable<?>) type).getBounds();
        }
        if (type instanceof Argument<?> && ((Argument<?>) type).isTypeVariable()) {
            return new Type[]{((Argument<?>) type).getType()};
        }
        return new Type[0];
    }

    private static Type resolveSuperType(Class<?> rawType,
                                         Class<?> superType,
                                         Type currentType,
                                         Map<TypeVariable<?>, Type> substitutions) {
        if (rawType == superType) {
            return currentType;
        }
        for (Type candidate : getGenericSupertypes(rawType)) {
            Type substitutedCandidate = substituteTypeVariables(candidate, substitutions);
            Class<?> candidateRawType = rawType(substitutedCandidate);
            if (candidateRawType == null || !superType.isAssignableFrom(candidateRawType)) {
                continue;
            }
            if (candidateRawType == superType) {
                return substitutedCandidate;
            }
            Map<TypeVariable<?>, Type> nestedSubstitutions = new HashMap<>(substitutions);
            collectTypeSubstitutions(candidateRawType, substitutedCandidate, nestedSubstitutions);
            Type resolved = resolveSuperType(candidateRawType, superType, substitutedCandidate, nestedSubstitutions);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private static List<Type> getGenericSupertypes(Class<?> rawType) {
        List<Type> supertypes = new ArrayList<>();
        supertypes.addAll(Arrays.asList(rawType.getGenericInterfaces()));
        Type genericSuperclass = rawType.getGenericSuperclass();
        if (genericSuperclass != null && genericSuperclass != Object.class) {
            supertypes.add(genericSuperclass);
        }
        return supertypes;
    }

    private static void collectTypeSubstitutions(Class<?> rawType,
                                                 Type type,
                                                 Map<TypeVariable<?>, Type> substitutions) {
        if (!(type instanceof ParameterizedType)) {
            return;
        }
        ParameterizedType parameterizedType = (ParameterizedType) type;
        if (!rawType.equals(rawType(parameterizedType))) {
            return;
        }
        TypeVariable<? extends Class<?>>[] variables = rawType.getTypeParameters();
        Type[] arguments = parameterizedType.getActualTypeArguments();
        if (variables.length != arguments.length) {
            return;
        }
        for (int i = 0; i < variables.length; i++) {
            substitutions.put(variables[i], substituteTypeVariables(arguments[i], substitutions));
        }
    }

    private static Type substituteTypeVariables(Type type, Map<TypeVariable<?>, Type> substitutions) {
        if (type instanceof TypeVariable<?>) {
            Type replacement = substitutions.get(type);
            return replacement == null || replacement == type ? type : substituteTypeVariables(replacement, substitutions);
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Class<?> rawType = rawType(parameterizedType);
            if (rawType == null) {
                return type;
            }
            Type[] arguments = parameterizedType.getActualTypeArguments();
            Type[] substitutedArguments = new Type[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                substitutedArguments[i] = substituteTypeVariables(arguments[i], substitutions);
            }
            return new OdiParameterizedType(rawType, substitutedArguments);
        }
        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            Type[] upperBounds = wildcardType.getUpperBounds();
            Type[] lowerBounds = wildcardType.getLowerBounds();
            for (int i = 0; i < upperBounds.length; i++) {
                upperBounds[i] = substituteTypeVariables(upperBounds[i], substitutions);
            }
            for (int i = 0; i < lowerBounds.length; i++) {
                lowerBounds[i] = substituteTypeVariables(lowerBounds[i], substitutions);
            }
            return new OdiWildcardType(upperBounds, lowerBounds);
        }
        if (type instanceof GenericArrayType) {
            Type componentType = substituteTypeVariables(((GenericArrayType) type).getGenericComponentType(), substitutions);
            if (componentType instanceof Class<?>) {
                return Array.newInstance((Class<?>) componentType, 0).getClass();
            }
            return new OdiGenericArrayType(componentType);
        }
        return type;
    }

    private static boolean hasOnlyRawTypeVariables(Argument<?> argument) {
        Argument<?>[] typeParameters = argument.getTypeParameters();
        TypeVariable<? extends Class<?>>[] declaredTypeParameters = argument.getType().getTypeParameters();
        if (typeParameters.length == 0 || typeParameters.length != declaredTypeParameters.length) {
            return false;
        }
        for (int i = 0; i < typeParameters.length; i++) {
            Argument<?> typeParameter = typeParameters[i];
            if (!typeParameter.isTypeVariable()
                    || typeParameter.getType() != Object.class
                    || !typeParameter.getName().equals(declaredTypeParameters[i].getName())
                    || typeParameter.getTypeParameters().length != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSameType(Type left, Type right) {
        if (Objects.equals(left, right)) {
            return true;
        }
        if (left instanceof Class<?> && right instanceof Class<?>) {
            return arePrimitiveWrapperEquivalent((Class<?>) left, (Class<?>) right);
        }
        if (left instanceof ParameterizedType && right instanceof ParameterizedType) {
            ParameterizedType leftParameterized = (ParameterizedType) left;
            ParameterizedType rightParameterized = (ParameterizedType) right;
            if (!Objects.equals(leftParameterized.getRawType(), rightParameterized.getRawType())) {
                return false;
            }
            Type[] leftArguments = leftParameterized.getActualTypeArguments();
            Type[] rightArguments = rightParameterized.getActualTypeArguments();
            if (leftArguments.length != rightArguments.length) {
                return false;
            }
            for (int i = 0; i < leftArguments.length; i++) {
                if (!isSameType(leftArguments[i], rightArguments[i])) {
                    return false;
                }
            }
            return true;
        }
        if (left instanceof GenericArrayType && right instanceof GenericArrayType) {
            return isSameType(
                    ((GenericArrayType) left).getGenericComponentType(),
                    ((GenericArrayType) right).getGenericComponentType()
            );
        }
        if (left instanceof WildcardType && right instanceof WildcardType) {
            WildcardType leftWildcard = (WildcardType) left;
            WildcardType rightWildcard = (WildcardType) right;
            return areSameTypes(leftWildcard.getUpperBounds(), rightWildcard.getUpperBounds())
                    && areSameTypes(leftWildcard.getLowerBounds(), rightWildcard.getLowerBounds());
        }
        if (isTypeVariable(left) && isTypeVariable(right)) {
            return Objects.equals(typeVariableName(left), typeVariableName(right))
                    && areSameTypes(typeVariableBounds(left), typeVariableBounds(right));
        }
        return false;
    }

    private static boolean areSameTypes(Type[] left, Type[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (!isSameType(left[i], right[i])) {
                return false;
            }
        }
        return true;
    }

    static boolean isLegalBeanType(Type type) {
        return type != null && !containsWildcard(type);
    }

    private static boolean containsTypeVariable(Type type) {
        if (isTypeVariable(type)) {
            return true;
        }
        if (type instanceof ParameterizedType) {
            for (Type argument : ((ParameterizedType) type).getActualTypeArguments()) {
                if (containsTypeVariable(argument)) {
                    return true;
                }
            }
        }
        if (type instanceof GenericArrayType) {
            return containsTypeVariable(((GenericArrayType) type).getGenericComponentType());
        }
        if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            for (Type upperBound : wildcard.getUpperBounds()) {
                if (containsTypeVariable(upperBound)) {
                    return true;
                }
            }
            for (Type lowerBound : wildcard.getLowerBounds()) {
                if (containsTypeVariable(lowerBound)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasOnlyUnboundedTypeVariables(Type type) {
        if (isTypeVariable(type)) {
            return hasOnlyObjectUpperBound(type);
        }
        if (type instanceof ParameterizedType) {
            for (Type argument : ((ParameterizedType) type).getActualTypeArguments()) {
                if (!hasOnlyUnboundedTypeVariables(argument)) {
                    return false;
                }
            }
        }
        if (type instanceof GenericArrayType) {
            return hasOnlyUnboundedTypeVariables(((GenericArrayType) type).getGenericComponentType());
        }
        if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            for (Type upperBound : wildcard.getUpperBounds()) {
                if (!hasOnlyUnboundedTypeVariables(upperBound)) {
                    return false;
                }
            }
            for (Type lowerBound : wildcard.getLowerBounds()) {
                if (!hasOnlyUnboundedTypeVariables(lowerBound)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean containsWildcard(Type type) {
        if (type instanceof WildcardType) {
            return true;
        }
        if (type instanceof ParameterizedType) {
            for (Type argument : ((ParameterizedType) type).getActualTypeArguments()) {
                if (containsWildcard(argument)) {
                    return true;
                }
            }
        }
        if (type instanceof GenericArrayType) {
            return containsWildcard(((GenericArrayType) type).getGenericComponentType());
        }
        return false;
    }

    private static boolean isSameRawType(Type left, Type right) {
        Class<?> leftRawType = rawType(left);
        Class<?> rightRawType = rawType(right);
        return leftRawType != null && leftRawType.equals(rightRawType);
    }

    private static Class<?> rawType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class<?>) {
                return (Class<?>) rawType;
            }
        }
        if (type instanceof Argument<?>) {
            return ((Argument<?>) type).getType();
        }
        if (type instanceof GenericArrayType) {
            Class<?> componentType = rawType(((GenericArrayType) type).getGenericComponentType());
            if (componentType != null) {
                return Array.newInstance(componentType, 0).getClass();
            }
        }
        return null;
    }

    private static String typeVariableName(Type type) {
        if (type instanceof TypeVariable<?>) {
            return ((TypeVariable<?>) type).getName();
        }
        if (type instanceof Argument<?> && ((Argument<?>) type).isTypeVariable()) {
            return ((Argument<?>) type).getName();
        }
        return null;
    }

    private static Type substituteTypeVariablesByName(Type type, Map<String, Type> substitutions) {
        if (type instanceof TypeVariable<?>) {
            Type replacement = substitutions.get(((TypeVariable<?>) type).getName());
            return replacement == null || replacement == type ? type : substituteTypeVariablesByName(replacement, substitutions);
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Class<?> rawType = rawType(parameterizedType);
            if (rawType == null) {
                return type;
            }
            Type[] arguments = parameterizedType.getActualTypeArguments();
            Type[] substitutedArguments = new Type[arguments.length];
            boolean changed = false;
            for (int i = 0; i < arguments.length; i++) {
                substitutedArguments[i] = substituteTypeVariablesByName(arguments[i], substitutions);
                changed |= substitutedArguments[i] != arguments[i];
            }
            return changed ? new OdiParameterizedType(rawType, substitutedArguments) : type;
        }
        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            Type[] upperBounds = wildcardType.getUpperBounds();
            Type[] lowerBounds = wildcardType.getLowerBounds();
            boolean changed = false;
            for (int i = 0; i < upperBounds.length; i++) {
                Type substituted = substituteTypeVariablesByName(upperBounds[i], substitutions);
                changed |= substituted != upperBounds[i];
                upperBounds[i] = substituted;
            }
            for (int i = 0; i < lowerBounds.length; i++) {
                Type substituted = substituteTypeVariablesByName(lowerBounds[i], substitutions);
                changed |= substituted != lowerBounds[i];
                lowerBounds[i] = substituted;
            }
            return changed ? new OdiWildcardType(upperBounds, lowerBounds) : type;
        }
        if (type instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) type).getGenericComponentType();
            Type substitutedComponentType = substituteTypeVariablesByName(componentType, substitutions);
            if (substitutedComponentType == componentType) {
                return type;
            }
            if (substitutedComponentType instanceof Class<?>) {
                return Array.newInstance((Class<?>) substitutedComponentType, 0).getClass();
            }
            return new OdiGenericArrayType(substitutedComponentType);
        }
        return type;
    }

    private static boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() || ReflectionUtils.getPrimitiveType(type) != type;
    }

    private static boolean arePrimitiveWrapperEquivalent(Class<?> left, Class<?> right) {
        if (!isPrimitiveOrWrapper(left) && !isPrimitiveOrWrapper(right)) {
            return false;
        }
        return ReflectionUtils.getPrimitiveType(left).equals(ReflectionUtils.getPrimitiveType(right));
    }

    private static final class TypeArgumentReader {
        private final Class<?>[] argumentTypes;
        private final int[] argumentCounts;
        private final boolean[] typeVariables;
        private final boolean[] wildcards;
        private final int[] lowerBoundCounts;
        private final String[] typeVariableNames;
        private int index;

        private TypeArgumentReader(Class<?>[] argumentTypes,
                                   int[] argumentCounts,
                                   boolean[] typeVariables,
                                   boolean[] wildcards,
                                   int[] lowerBoundCounts,
                                   String[] typeVariableNames) {
            this.argumentTypes = argumentTypes;
            this.argumentCounts = argumentCounts;
            this.typeVariables = typeVariables;
            this.wildcards = wildcards;
            this.lowerBoundCounts = lowerBoundCounts;
            this.typeVariableNames = typeVariableNames;
        }

        private Type[] readAll() {
            List<Type> arguments = new ArrayList<>(argumentTypes.length);
            while (index < argumentTypes.length) {
                arguments.add(read());
            }
            return arguments.toArray(Type[]::new);
        }

        private Type read() {
            Class<?> type = argumentTypes[index];
            int childCount = argumentCounts[index];
            boolean typeVariable = index < typeVariables.length && typeVariables[index];
            boolean wildcard = index < wildcards.length && wildcards[index];
            int lowerBoundCount = index < lowerBoundCounts.length ? lowerBoundCounts[index] : 0;
            String typeVariableName = index < typeVariableNames.length ? typeVariableNames[index] : type.getSimpleName();
            index++;
            if (wildcard) {
                int upperBoundCount = childCount - lowerBoundCount;
                Type[] upperBounds = readBounds(upperBoundCount);
                Type[] lowerBounds = readBounds(lowerBoundCount);
                return new OdiWildcardType(upperBounds, lowerBounds);
            }
            if (typeVariable) {
                Type[] bounds = readBounds(childCount);
                return new OdiTypeVariable(typeVariableName, bounds.length == 0 ? new Type[]{type} : bounds);
            }
            if (childCount == 0) {
                return type;
            }
            return new OdiParameterizedType(type, readBounds(childCount));
        }

        private Type[] readBounds(int count) {
            Type[] bounds = new Type[count];
            for (int i = 0; i < count; i++) {
                bounds[i] = read();
            }
            return bounds;
        }
    }

    private static final class OdiParameterizedType implements ParameterizedType {
        private final Class<?> rawType;
        private final Type[] arguments;

        private OdiParameterizedType(Class<?> rawType, Type[] arguments) {
            this.rawType = rawType;
            this.arguments = arguments.clone();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return arguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        @Override
        public String getTypeName() {
            return rawType.getTypeName() + Arrays.stream(arguments)
                    .map(Type::getTypeName)
                    .collect(Collectors.joining(", ", "<", ">"));
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ParameterizedType)) {
                return false;
            }
            ParameterizedType that = (ParameterizedType) other;
            return Objects.equals(rawType, that.getRawType())
                    && Objects.equals(getOwnerType(), that.getOwnerType())
                    && Arrays.equals(arguments, that.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(arguments) ^ Objects.hashCode(rawType) ^ Objects.hashCode(getOwnerType());
        }

        @Override
        public String toString() {
            return getTypeName();
        }
    }

    private static final class OdiWildcardType implements WildcardType {
        private final Type[] upperBounds;
        private final Type[] lowerBounds;

        private OdiWildcardType(Type[] upperBounds, Type[] lowerBounds) {
            this.upperBounds = upperBounds.length == 0 ? new Type[]{Object.class} : upperBounds.clone();
            this.lowerBounds = lowerBounds.clone();
        }

        @Override
        public Type[] getUpperBounds() {
            return upperBounds.clone();
        }

        @Override
        public Type[] getLowerBounds() {
            return lowerBounds.clone();
        }

        @Override
        public String getTypeName() {
            if (lowerBounds.length > 0) {
                return Arrays.stream(lowerBounds)
                        .map(Type::getTypeName)
                        .collect(Collectors.joining(" & ", "? super ", ""));
            }
            if (upperBounds.length == 1 && upperBounds[0] == Object.class) {
                return "?";
            }
            return Arrays.stream(upperBounds)
                    .map(Type::getTypeName)
                    .collect(Collectors.joining(" & ", "? extends ", ""));
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof WildcardType)) {
                return false;
            }
            WildcardType that = (WildcardType) other;
            return Arrays.equals(upperBounds, that.getUpperBounds())
                    && Arrays.equals(lowerBounds, that.getLowerBounds());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(upperBounds) ^ Arrays.hashCode(lowerBounds);
        }

        @Override
        public String toString() {
            return getTypeName();
        }
    }

    private static final class OdiGenericArrayType implements GenericArrayType {
        private final Type componentType;

        private OdiGenericArrayType(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }

        @Override
        public String getTypeName() {
            return componentType.getTypeName() + "[]";
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof GenericArrayType
                    && Objects.equals(componentType, ((GenericArrayType) other).getGenericComponentType());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(componentType);
        }

        @Override
        public String toString() {
            return getTypeName();
        }
    }

    private static final class OdiTypeVariable implements TypeVariable<GenericDeclaration> {
        private final String name;
        private final Type[] bounds;
        private final GenericDeclaration declaration;

        private OdiTypeVariable(String name, Type[] bounds) {
            this.name = name;
            this.bounds = bounds.length == 0 ? new Type[]{Object.class} : bounds.clone();
            this.declaration = new OdiGenericDeclaration(name);
        }

        @Override
        public Type[] getBounds() {
            return bounds.clone();
        }

        @Override
        public GenericDeclaration getGenericDeclaration() {
            return declaration;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public java.lang.reflect.AnnotatedType[] getAnnotatedBounds() {
            return new java.lang.reflect.AnnotatedType[0];
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return new Annotation[0];
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return new Annotation[0];
        }

        @Override
        public String getTypeName() {
            return name;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof TypeVariable<?>)) {
                return false;
            }
            TypeVariable<?> that = (TypeVariable<?>) other;
            return name.equals(that.getName()) && Arrays.equals(bounds, that.getBounds());
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ Arrays.hashCode(bounds);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class OdiGenericDeclaration implements GenericDeclaration {
        private final String name;

        private OdiGenericDeclaration(String name) {
            this.name = name;
        }

        @Override
        public TypeVariable<?>[] getTypeParameters() {
            return new TypeVariable<?>[0];
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return new Annotation[0];
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return new Annotation[0];
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
