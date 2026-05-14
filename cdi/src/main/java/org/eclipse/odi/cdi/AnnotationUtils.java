/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import org.eclipse.odi.cdi.annotation.reflect.AnnotationReflection;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.annotation.AnnotationMetadataException;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The internal annotation utils class.
 */
@Internal
public final class AnnotationUtils {

    private AnnotationUtils() {
    }

    /**
     * Creates new {@link java.lang.annotation.Annotation} instances from all qualifiers.
     * @param annotationMetadata The annotation metadata, never {@code null}
     * @param classLoader The classloader
     * @return The synthesized annotations
     */
    public static Set<Annotation> synthesizeQualifierAnnotations(AnnotationMetadata annotationMetadata, ClassLoader classLoader) {
        Set<String> stereotypes = new LinkedHashSet<>(annotationMetadata.getAnnotationNamesByStereotype(Stereotype.class.getName()));
        Set<Annotation> resolved = new LinkedHashSet<>();
        for (String name : annotationMetadata.getAnnotationNamesByStereotype(MetaAnnotationSupport.META_ANNOTATION_QUALIFIER)) {
            if (stereotypes.contains(name)) {
                continue;
            }
            if (!isRuntimeQualifier(annotationMetadata, name)) {
                continue;
            }
            Annotation annotation;
            if (name.equals(Any.NAME)) {
                annotation = jakarta.enterprise.inject.Any.Literal.INSTANCE;
            } else if (name.equals(Default.class.getName())) {
                annotation = Default.Literal.INSTANCE;
            } else if (name.equals(MetaAnnotationSupport.META_ANNOTATION_NAMED)) {
                if (!annotationMetadata.hasDeclaredAnnotation(AnnotationUtil.NAMED)) {
                    annotation = null;
                } else {
                    annotation = NamedLiteral.of(annotationMetadata.stringValue(AnnotationUtil.NAMED).get());
                }
            } else {
                final Class<? extends Annotation> annotationClass = annotationMetadata.getAnnotationType(name, classLoader)
                        .orElse(null);
                if (annotationClass != null) {
                    if (annotationMetadata.findRepeatableAnnotation(name).isPresent()) {
                        Annotation[] annotations = annotationMetadata.synthesizeAnnotationsByType(annotationClass);
                        if (annotations.length > 0) {
                            for (Annotation resolvedAnnotation : annotations) {
                                if (resolvedAnnotation != null) {
                                    resolved.add(resolvedAnnotation);
                                }
                            }
                            annotation = null;
                        } else {
                            annotation = synthesizeQualifierAnnotation(annotationMetadata, annotationClass);
                        }
                    } else {
                        annotation = synthesizeQualifierAnnotation(annotationMetadata, annotationClass);
                    }
                } else {
                    annotation = null;
                }
            }
            if (annotation != null) {
                resolved.add(annotation);
            }
        }
        return resolved;
    }

    @Nullable
    private static <T extends Annotation> T synthesizeQualifierAnnotation(AnnotationMetadata annotationMetadata,
                                                                         Class<T> annotationClass) {
        AnnotationValue<T> annotationValue = annotationMetadata.findAnnotation(annotationClass).orElse(null);
        if (annotationValue == null) {
            return annotationMetadata.synthesize(annotationClass);
        }
        return synthesizeQualifierAnnotation(annotationClass, annotationValue);
    }

    private static <T extends Annotation> T synthesizeQualifierAnnotation(Class<T> annotationClass,
                                                                         AnnotationValue<? extends Annotation> annotationValue) {
        MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
        annotationMetadata.addDeclaredAnnotation(
                annotationValue.getAnnotationName(),
                valuesIncludingDefaults(annotationValue),
                annotationValue.getRetentionPolicy()
        );
        return annotationMetadata.synthesize(annotationClass);
    }

    private static Map<CharSequence, Object> valuesIncludingDefaults(AnnotationValue<? extends Annotation> annotationValue) {
        Map<CharSequence, Object> values = annotationValue.getValues();
        Map<CharSequence, Object> defaultValues = annotationValue.getDefaultValues();
        if (defaultValues == null || defaultValues.isEmpty()) {
            return values;
        }
        Map<CharSequence, Object> merged = new LinkedHashMap<>(defaultValues);
        merged.putAll(values);
        return merged;
    }

    /**
     * Creates annotation instances for all CDI interceptor bindings, including interceptor bindings declared as
     * stereotypes on another interceptor binding annotation.
     *
     * @param annotationMetadata The annotation metadata, never {@code null}
     * @return The synthesized interceptor binding annotations
     */
    public static Set<Annotation> synthesizeInterceptorBindingAnnotations(AnnotationMetadata annotationMetadata) {
        List<String> interceptorBindings = annotationMetadata
                .getAnnotationNamesByStereotype(InterceptorBinding.class.getName());
        if (interceptorBindings.isEmpty()) {
            return Set.of();
        }
        Set<Annotation> resolved = new LinkedHashSet<>();
        for (String interceptorBinding : interceptorBindings) {
            AnnotationValue<Annotation> annotationValue = annotationMetadata.getAnnotation(interceptorBinding);
            if (annotationValue == null) {
                continue;
            }
            collectInterceptorBindingAnnotations(annotationMetadata, annotationValue, resolved, new LinkedHashSet<>());
        }
        return Set.copyOf(resolved);
    }

    private static void collectInterceptorBindingAnnotations(AnnotationMetadata annotationMetadata,
                                                            AnnotationValue<?> annotationValue,
                                                            Set<Annotation> resolved,
                                                            Set<String> visiting) {
        if (annotationValue == null) {
            return;
        }
        String annotationName = annotationValue.getAnnotationName();
        if (!visiting.add(annotationName)) {
            return;
        }
        try {
            if (!InterceptorBinding.class.getName().equals(annotationName)) {
                Annotation annotation = synthesizeAnnotationValue(annotationMetadata, annotationValue);
                if (annotation != null) {
                    resolved.add(annotation);
                }
            }
            List<AnnotationValue<?>> stereotypes = annotationValue.getStereotypes();
            if (stereotypes == null) {
                return;
            }
            for (AnnotationValue<?> stereotype : stereotypes) {
                if (!InterceptorBinding.class.getName().equals(stereotype.getAnnotationName())
                        && hasInterceptorBindingStereotype(stereotype, new LinkedHashSet<>())) {
                    collectInterceptorBindingAnnotations(annotationMetadata, stereotype, resolved, visiting);
                }
            }
        } finally {
            visiting.remove(annotationName);
        }
    }

    @Nullable
    private static Annotation synthesizeAnnotationValue(AnnotationMetadata sourceMetadata, AnnotationValue<?> annotationValue) {
        Class<? extends Annotation> annotationClass = sourceMetadata
                .getAnnotationType(annotationValue.getAnnotationName())
                .orElse(null);
        if (annotationClass == null) {
            return null;
        }
        MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
        annotationMetadata.addDeclaredAnnotation(
                annotationValue.getAnnotationName(),
                annotationValue.getValues(),
                annotationValue.getRetentionPolicy()
        );
        try {
            return annotationMetadata.synthesize(annotationClass);
        } catch (AnnotationMetadataException e) {
            return proxyAnnotationValue(annotationClass, annotationValue);
        }
    }

    private static Annotation proxyAnnotationValue(Class<? extends Annotation> annotationClass,
                                                   AnnotationValue<?> annotationValue) {
        Map<CharSequence, Object> values = valuesIncludingDefaults(annotationValue);
        return (Annotation) Proxy.newProxyInstance(
                annotationClass.getClassLoader(),
                new Class<?>[]{annotationClass},
                (proxy, method, args) -> annotationMethodValue(proxy, annotationClass, values, method, args)
        );
    }

    private static Object annotationMethodValue(Object proxy,
                                                Class<? extends Annotation> annotationClass,
                                                Map<CharSequence, Object> values,
                                                Method method,
                                                Object[] args) throws ReflectiveOperationException {
        String name = method.getName();
        if (method.getParameterCount() == 0) {
            if ("annotationType".equals(name)) {
                return annotationClass;
            }
            if ("toString".equals(name)) {
                return "@" + annotationClass.getName() + values;
            }
            if ("hashCode".equals(name)) {
                return annotationHashCode(annotationClass, values);
            }
            Object value = values.getOrDefault(name, method.getDefaultValue());
            if (value != null) {
                return normalizeAnnotationMember(method.getReturnType(), value);
            }
        } else if (method.getParameterCount() == 1 && "equals".equals(name)) {
            return annotationEquals(proxy, annotationClass, values, args[0]);
        }
        return method.invoke(proxy, args);
    }

    private static boolean annotationEquals(Object proxy,
                                            Class<? extends Annotation> annotationClass,
                                            Map<CharSequence, Object> values,
                                            Object other) throws ReflectiveOperationException {
        if (proxy == other) {
            return true;
        }
        if (!annotationClass.isInstance(other)) {
            return false;
        }
        for (Method member : annotationClass.getDeclaredMethods()) {
            member.setAccessible(true);
            Object thisValue = normalizeAnnotationMember(
                    member.getReturnType(),
                    values.getOrDefault(member.getName(), member.getDefaultValue())
            );
            Object thatValue = member.invoke(other);
            if (!memberValueEquals(thisValue, thatValue)) {
                return false;
            }
        }
        return true;
    }

    private static int annotationHashCode(Class<? extends Annotation> annotationClass,
                                          Map<CharSequence, Object> values) {
        int result = 0;
        for (Method member : annotationClass.getDeclaredMethods()) {
            Object value = normalizeAnnotationMember(
                    member.getReturnType(),
                    values.getOrDefault(member.getName(), member.getDefaultValue())
            );
            result += (127 * member.getName().hashCode()) ^ memberValueHashCode(value);
        }
        return result;
    }

    private static Object normalizeAnnotationMember(Class<?> returnType, Object value) {
        if (value == null || returnType.isInstance(value) || !returnType.isArray()) {
            return value;
        }
        Class<?> componentType = returnType.getComponentType();
        if (componentType.isInstance(value)) {
            Object array = Array.newInstance(componentType, 1);
            Array.set(array, 0, value);
            return array;
        }
        return value;
    }

    private static boolean memberValueEquals(Object left, Object right) {
        if (left != null && left.getClass().isArray() && right != null && right.getClass().isArray()) {
            if (left instanceof Object[] && right instanceof Object[]) {
                return Arrays.equals((Object[]) left, (Object[]) right);
            }
            int length = Array.getLength(left);
            if (length != Array.getLength(right)) {
                return false;
            }
            for (int i = 0; i < length; i++) {
                if (!java.util.Objects.equals(Array.get(left, i), Array.get(right, i))) {
                    return false;
                }
            }
            return true;
        }
        return java.util.Objects.equals(left, right);
    }

    private static int memberValueHashCode(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Object[]) {
            return Arrays.hashCode((Object[]) value);
        }
        if (!value.getClass().isArray()) {
            return value.hashCode();
        }
        int result = 1;
        int length = Array.getLength(value);
        for (int i = 0; i < length; i++) {
            result = 31 * result + java.util.Objects.hashCode(Array.get(value, i));
        }
        return result;
    }

    private static boolean hasInterceptorBindingStereotype(AnnotationValue<?> annotationValue, Set<String> visited) {
        String annotationName = annotationValue.getAnnotationName();
        if (!visited.add(annotationName)) {
            return false;
        }
        if (InterceptorBinding.class.getName().equals(annotationName)) {
            return true;
        }
        List<AnnotationValue<?>> stereotypes = annotationValue.getStereotypes();
        if (stereotypes == null) {
            return false;
        }
        for (AnnotationValue<?> stereotype : stereotypes) {
            if (hasInterceptorBindingStereotype(stereotype, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a qualifier from the array of {@link Annotation}.
     * @param annotations The annotations
     * @param <U> The qualifier type
     * @return The qualifier
     */
    @Nullable
    public static <U> Qualifier<U> qualifierFromQualifierAnnotations(@Nullable Annotation[] annotations) {
        if (annotations == null || annotations.length == 0) {
            return null;
        }
        AnnotationMetadata annotationMetadata = annotationMetadataFromQualifierAnnotations(annotations);
        return qualifierFromQualifierAnnotations(annotationMetadata, annotations);
    }

    /**
     * Creates a qualifier from qualifier annotations stored in metadata.
     * @param annotationMetadata The annotation metadata
     * @param <U> The qualifier type
     * @return The qualifier
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <U> Qualifier<U> qualifierFromQualifierMetadata(AnnotationMetadata annotationMetadata) {
        List<String> qualifierNames = annotationMetadata.getAnnotationNamesByStereotype(MetaAnnotationSupport.META_ANNOTATION_QUALIFIER);
        if (qualifierNames.isEmpty()) {
            return null;
        }
        Set<String> stereotypes = new LinkedHashSet<>(annotationMetadata.getAnnotationNamesByStereotype(Stereotype.class.getName()));
        List<Qualifier<U>> qualifiers = new ArrayList<>(qualifierNames.size());
        for (String qualifierName : qualifierNames) {
            if (stereotypes.contains(qualifierName)) {
                continue;
            }
            if (!isRuntimeQualifier(annotationMetadata, qualifierName)) {
                continue;
            }
            if (annotationMetadata.findRepeatableAnnotation(qualifierName).isPresent()) {
                List<AnnotationValue<Annotation>> values = annotationMetadata.getAnnotationValuesByName(qualifierName);
                if (!values.isEmpty()) {
                    for (AnnotationValue<Annotation> value : values) {
                        qualifiers.add((Qualifier<U>) Qualifiers.byAnnotation(annotationMetadata, value));
                    }
                    continue;
                }
            }
            qualifiers.add((Qualifier<U>) byAnnotationName(annotationMetadata, qualifierName));
        }
        if (qualifiers.isEmpty()) {
            return null;
        }
        return qualifiers.size() == 1
                ? qualifiers.get(0)
                : Qualifiers.byQualifiers(qualifiers.toArray(new Qualifier[0]));
    }

    private static boolean isRuntimeQualifier(AnnotationMetadata annotationMetadata, String qualifierName) {
        if (Any.NAME.equals(qualifierName)
                || Default.class.getName().equals(qualifierName)
                || MetaAnnotationSupport.META_ANNOTATION_NAMED.equals(qualifierName)) {
            return true;
        }
        AnnotationValue<Annotation> qualifier = annotationMetadata.getAnnotation(qualifierName);
        return qualifier == null || qualifier.getRetentionPolicy() == RetentionPolicy.RUNTIME;
    }

    /**
     * Convert the annotations array into {@link AnnotationMetadata} instance.
     * @param annotations The annotations
     * @return an instance of {@link AnnotationMetadata}
     */
    public static AnnotationMetadata annotationMetadataFromQualifierAnnotations(Annotation[] annotations) {
        if (annotations.length == 0) {
            return AnnotationMetadata.EMPTY_METADATA;
        }
        MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
        Map<String, Integer> qualifierCounts = qualifierCounts(annotations);
        for (Annotation annotation : annotations) {
            if (isQualifier(annotation)) {
                if (isAny(annotation)) {
                    annotationMetadata.addDeclaredAnnotation(Any.NAME, Collections.emptyMap());
                    annotationMetadata.addDeclaredStereotype(
                            List.of(Any.NAME),
                            MetaAnnotationSupport.META_ANNOTATION_QUALIFIER, Collections.emptyMap()
                    );
                } else {
                    AnnotationValue<Annotation> value = AnnotationReflection.toAnnotationValue(annotation);
                    AnnotationValue<Annotation> valueWithDefaults = annotationValueIncludingDefaults(value);
                    String annotationName = value.getAnnotationName();
                    if (qualifierCounts.getOrDefault(annotationName, 0) > 1) {
                        String repeatableContainer = annotationMetadata.findRepeatableAnnotation(annotationName).orElse(null);
                        if (repeatableContainer != null) {
                            annotationMetadata.addDeclaredRepeatable(repeatableContainer, valueWithDefaults);
                        } else {
                            annotationMetadata.addDeclaredAnnotation(annotationName, valueWithDefaults.getValues());
                        }
                    } else {
                        annotationMetadata.addDeclaredAnnotation(annotationName, valueWithDefaults.getValues());
                    }
                    annotationMetadata.addDeclaredStereotype(
                            List.of(annotationName),
                            MetaAnnotationSupport.META_ANNOTATION_QUALIFIER, valueWithDefaults.getValues()
                    );
                }
            }
        }
        return annotationMetadata;
    }

    /**
     * Creates a qualifier from the array of {@link Annotation}.
     * @param annotationMetadata The annotation metadata
     * @param annotations The annotations
     * @param <U> The qualifier type
     * @return The qualifier
     */
    public static <U> Qualifier<U> qualifierFromQualifierAnnotations(
            AnnotationMetadata annotationMetadata,
            Annotation... annotations) {
        if (annotations.length > 0) {
            List<Qualifier<U>> qualifiers = new ArrayList<>(annotations.length);
            Map<Class<? extends Annotation>, List<Annotation>> groupedAnnotations = groupQualifierAnnotations(annotations);
            for (Map.Entry<Class<? extends Annotation>, List<Annotation>> entry : groupedAnnotations.entrySet()) {
                Class<? extends Annotation> annotationClass = entry.getKey();
                if (entry.getValue().size() == 1) {
                    qualifiers.add((Qualifier<U>) byAnnotation(annotationMetadata, annotationClass));
                } else {
                    annotationMetadata.findRepeatableAnnotation(annotationClass.getName())
                            .orElseThrow(() -> new IllegalArgumentException("Duplicate annotation detected: " + annotationClass));
                    for (Annotation annotation : entry.getValue()) {
                        qualifiers.add((Qualifier<U>) Qualifiers.byAnnotation(
                                annotationMetadata,
                                annotationValueIncludingDefaults(AnnotationReflection.toAnnotationValue(annotation))
                        ));
                    }
                }
            }
            return qualifiers.size() == 1
                    ? qualifiers.get(0)
                    : Qualifiers.byQualifiers(qualifiers.toArray(new Qualifier[0]));
        }
        return null;
    }

    static <T extends Annotation> Qualifier<T> byAnnotation(AnnotationMetadata annotationMetadata, Class<T> annotation) {
        if (isAny(annotation)) {
            //noinspection unchecked
            return AnyQualifier.INSTANCE;
        }
        return Qualifiers.byAnnotation(annotationMetadata, annotation);
    }

    static <T> Qualifier<T> byAnnotationName(AnnotationMetadata annotationMetadata, String annotation) {
        if (annotation.equals(Any.NAME)) {
            //noinspection unchecked
            return AnyQualifier.INSTANCE;
        }
        return Qualifiers.byAnnotation(annotationMetadata, annotation);
    }

    private static AnnotationValue<Annotation> annotationValueIncludingDefaults(AnnotationValue<Annotation> annotationValue) {
        return AnnotationValue.builder(annotationValue, annotationValue.getRetentionPolicy())
                .members(valuesIncludingDefaults(annotationValue))
                .build();
    }

    public static <T extends Annotation> boolean isAny(Class<T> annotation) {
        return annotation == jakarta.enterprise.inject.Any.class;
    }

    static <T extends Annotation> boolean isAny(T annotation) {
        return findAnnotationClass(annotation) == jakarta.enterprise.inject.Any.class;
    }

    private static boolean isQualifier(Annotation annotation) {
        return findAnnotationClass(annotation).isAnnotationPresent(jakarta.inject.Qualifier.class);
    }

    private static Map<String, Integer> qualifierCounts(Annotation[] annotations) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Annotation annotation : annotations) {
            if (isQualifier(annotation)) {
                counts.merge(findAnnotationClass(annotation).getName(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static Map<Class<? extends Annotation>, List<Annotation>> groupQualifierAnnotations(Annotation[] annotations) {
        Map<Class<? extends Annotation>, List<Annotation>> grouped = new LinkedHashMap<>();
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationClass = findAnnotationClass(annotation);
            checkValidAnnotation(annotationClass, annotation);
            grouped.computeIfAbsent(annotationClass, ignored -> new ArrayList<>()).add(annotation);
        }
        return grouped;
    }

    private static void checkValidAnnotation(Class<? extends Annotation> annotationClass, Annotation annotation) {
        if (!annotationClass.isAnnotationPresent(jakarta.inject.Qualifier.class)) {
            throw new IllegalArgumentException("Incorrect annotation: " + annotation.annotationType());
        }
        Retention retention = annotationClass.getAnnotation(Retention.class);
        if (retention.value() != RetentionPolicy.RUNTIME) {
            throw new IllegalArgumentException("Incorrect annotation retention: " + retention.value());
        }
    }

    static Class<? extends Annotation> findAnnotationClass(Annotation annotation) {
        if (annotation.annotationType().isAnnotation()) {
            return annotation.annotationType();
        }
        throw new IllegalArgumentException("Cannot find annotation class for: " + annotation.annotationType());
    }
}
