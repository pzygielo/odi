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
package org.eclipse.odi.cdi.processor;

import io.micronaut.annotation.processing.visitor.JavaClassElementHelper;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Stereotype;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Internal CDI utils.
 */
@Internal
public final class CdiUtil {
    public static final String SPEC_LOCATION = "https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html";

    private CdiUtil() {
    }

    public static boolean validateBeanDefinition(VisitorContext context, Class<? extends Annotation> annotationType, ClassElement classElement) {
        if (!org.eclipse.odi.cdi.processor.AnnotationUtil.hasBeanDefiningAnnotation(classElement)) {
            context.fail(
                    "Class with " + annotationType.getSimpleName() + " must specify a bean definition annotation. See "
                            + CdiUtil.SPEC_LOCATION
                            + "#bean_defining_annotations",
                    classElement
            );
            return true;
        }
        return false;
    }

    public static boolean isBeanClass(ClassElement classElement) {
        if (classElement.getDefaultConstructor().isPresent()) {
            return true;
        }
        return classElement.getAccessibleConstructors().stream()
                .anyMatch(constructor -> constructor.hasDeclaredAnnotation(Inject.class));
    }

    public static boolean validateNoInterceptor(VisitorContext context,
                                                Class<? extends Annotation> annotationType,
                                                MethodElement methodElement) {
        if (methodElement.getOwningType().hasDeclaredAnnotation(Interceptor.class)) {
            context.fail("Interceptors cannot have methods annotated with @" + annotationType.getSimpleName(), methodElement);
            return true;
        }
        return false;
    }

    public static boolean validateMethodExtraAnnotations(VisitorContext context,
                                                         Class<? extends Annotation> annotationType,
                                                         MethodElement methodElement) {
        String prefix = methodElement instanceof ConstructorElement ? "Constructors" : "Methods";
        for (Class<? extends Annotation> annotationToCheck : new Class[]{Inject.class, Produces.class}) {
            if (!annotationToCheck.equals(annotationType) && methodElement.hasDeclaredAnnotation(annotationToCheck)) {
                context.fail(prefix + " annotated with @" + annotationType.getSimpleName() + " cannot be annotated with @" + annotationToCheck.getSimpleName(), methodElement);
                return true;
            }
        }
        return false;
    }

    public static boolean validateParameterExtraAnnotations(VisitorContext context,
                                                            Class<? extends Annotation> annotationType,
                                                            MethodElement methodElement,
                                                            ParameterElement parameterElement) {
        String prefix = methodElement instanceof ConstructorElement ? "Constructors" : "Methods";
        for (Class<? extends Annotation> annotationToCheck : new Class[]{Disposes.class, Observes.class, ObservesAsync.class}) {
            if (!annotationToCheck.equals(annotationType) && parameterElement.hasDeclaredAnnotation(annotationToCheck)) {
                context.fail(prefix + " with parameters annotated with @" + annotationType.getSimpleName() + " cannot be annotated with @" + annotationToCheck.getSimpleName(), parameterElement);
                return true;
            }
        }
        return false;
    }

    public static boolean validateMethodNoSpecialParameters(VisitorContext context,
                                                            String annotationSimpleName,
                                                            MethodElement methodElement,
                                                            ParameterElement parameterElement) {
        String prefix = methodElement instanceof ConstructorElement ? "Constructors" : "Methods";
        for (Class<? extends Annotation> annotationToCheck : new Class[]{Disposes.class, Observes.class, ObservesAsync.class}) {
            if (parameterElement.hasDeclaredAnnotation(annotationToCheck)) {
                context.fail(prefix + " annotated with @" + annotationSimpleName + " cannot define parameters annotated with @" + annotationToCheck.getSimpleName(), parameterElement);
                return true;
            }
        }
        return false;
    }

    public static boolean validateField(VisitorContext context,
                                        Class<? extends Annotation> annotationType,
                                        FieldElement fieldElement) {
        if (!io.micronaut.core.annotation.AnnotationUtil.INJECT.equals(annotationType.getName())
                && fieldElement.hasDeclaredAnnotation(io.micronaut.core.annotation.AnnotationUtil.INJECT)) {
            context.fail("Fields annotated with @" + annotationType.getSimpleName() + " cannot be annotated with @Inject", fieldElement);
            return true;
        }
        return false;
    }

    public static String toAnnotationDescription(List<String> annotations) {
        return annotations.stream().map(n -> "@" + NameUtils.getSimpleName(n)).collect(Collectors.joining(" and "));
    }

    public static void visitPriority(VisitorContext context, ClassElement element) {
        if (element.hasDeclaredAnnotation(Order.class)) {
            return;
        }
        OptionalInt priority = resolvePriority(context, element);
        if (priority.isPresent()) {
            element.annotate(Order.class, builder -> builder.value(-priority.getAsInt()));
        }
    }

    private static OptionalInt resolvePriority(VisitorContext context, ClassElement element) {
        OptionalInt directPriority = declaredIntValue(element.getAnnotationMetadata(), Priority.class);
        if (directPriority.isPresent()) {
            return directPriority;
        }
        OptionalInt metadataPriority = resolveStereotypePriority(element.getAnnotationMetadata(), new HashSet<>());
        if (metadataPriority.isPresent()) {
            return metadataPriority;
        }
        return resolveStereotypePriority(
                context,
                element.getAnnotationNames(),
                new HashSet<>()
        );
    }

    private static OptionalInt resolveStereotypePriority(AnnotationMetadata annotationMetadata, Set<String> visited) {
        for (String annotationName : annotationMetadata.getAnnotationNames()) {
            AnnotationValue<Annotation> annotation = annotationMetadata.getAnnotation(annotationName);
            if (annotation == null || annotation.getStereotypes() == null) {
                continue;
            }
            OptionalInt priority = resolveStereotypePriority(annotation.getStereotypes(), visited);
            if (priority.isPresent()) {
                return priority;
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt resolveStereotypePriority(Iterable<AnnotationValue<?>> stereotypes, Set<String> visited) {
        for (AnnotationValue<?> stereotype : stereotypes) {
            String annotationName = stereotype.getAnnotationName();
            if (!visited.add(annotationName)) {
                continue;
            }
            if (annotationName.equals(Priority.class.getName())) {
                OptionalInt priority = stereotype.intValue();
                if (priority.isPresent()) {
                    return priority;
                }
            }
            if (annotationName.equals(Order.class.getName())) {
                int order = stereotype.intValue().orElse(0);
                if (order != 0) {
                    return OptionalInt.of(-order);
                }
            }
            if (stereotype.getStereotypes() != null) {
                OptionalInt priority = resolveStereotypePriority(stereotype.getStereotypes(), visited);
                if (priority.isPresent()) {
                    return priority;
                }
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt resolveStereotypePriority(VisitorContext context, Collection<String> annotationNames, Set<String> visited) {
        for (String annotationName : annotationNames) {
            if (!visited.add(annotationName)) {
                continue;
            }
            OptionalInt priority = context.getClassElement(annotationName)
                    .flatMap(annotation -> {
                        if (!annotation.hasAnnotation(Stereotype.class) && !annotation.hasStereotype(Stereotype.class)) {
                            return java.util.Optional.empty();
                        }
                        OptionalInt directPriority = declaredIntValue(annotation.getAnnotationMetadata(), Priority.class);
                        if (directPriority.isPresent()) {
                            return java.util.Optional.of(directPriority);
                        }
                        int order = declaredIntValue(annotation.getAnnotationMetadata(), Order.class).orElse(0);
                        if (order != 0) {
                            return java.util.Optional.of(OptionalInt.of(-order));
                        }
                        OptionalInt nestedPriority = resolveStereotypePriority(
                                context,
                                annotation.getAnnotationNames(),
                                visited
                        );
                        return nestedPriority.isPresent()
                                ? java.util.Optional.of(nestedPriority)
                                : java.util.Optional.empty();
                    })
                    .orElse(OptionalInt.empty());
            if (priority.isPresent()) {
                return priority;
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt declaredIntValue(AnnotationMetadata annotationMetadata, Class<? extends Annotation> annotationType) {
        AnnotationValue<?> annotation = annotationMetadata.getDeclaredAnnotation(annotationType);
        if (annotation != null) {
            OptionalInt value = annotation.intValue();
            if (value.isPresent()) {
                return value;
            }
        }
        return annotationMetadata.getDeclaredMetadata().intValue(annotationType);
    }

    private static boolean needsDefaultQualifier(AnnotationMetadata annotationMetadata, boolean declaringElementOnly) {
        AnnotationMetadata declaredMetadata;
        if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            declaredMetadata = ((AnnotationMetadataHierarchy) annotationMetadata).getRootMetadata();
        } else {
            declaredMetadata = annotationMetadata.getDeclaredMetadata();
        }
        if (declaringElementOnly) {
            return !declaredMetadata.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)
                    && !declaredMetadata.hasStereotype(AnnotationUtil.QUALIFIER);
        }
        return !declaredMetadata.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)
                && !annotationMetadata.hasStereotype(AnnotationUtil.QUALIFIER);
    }

    private static boolean needsDefaultInjectPointQualifier(AnnotationMetadata annotationMetadata) {
        AnnotationMetadata declaredMetadata = annotationMetadata.getDeclaredMetadata();
        return !declaredMetadata.hasDeclaredStereotype(AnnotationUtil.QUALIFIER);
    }

    public static void visitBeanDefinition(VisitorContext context, Element beanDefinition) {
        boolean declaringElementOnly = beanDefinition instanceof MethodElement || beanDefinition instanceof FieldElement;
        if (needsDefaultQualifier(beanDefinition.getAnnotationMetadata(), declaringElementOnly)) {
            beanDefinition.annotate(Default.class);
        }
        visitBeanTypes(beanDefinition);
    }

    private static void visitBeanTypes(Element beanDefinition) {
        ClassElement beanType = resolveBeanType(beanDefinition);
        if (beanType == null) {
            return;
        }
        boolean collapseDeclaredTypeVariables = !(beanDefinition instanceof ClassElement);
        Set<ClassElement> beanTypes = new LinkedHashSet<>();
        collectBeanTypes(beanType, beanTypes);
        boolean hasMetadataTypes = beanTypes.stream().anyMatch(type -> hasTypeArguments(type, collapseDeclaredTypeVariables));
        if (!hasMetadataTypes && collapseDeclaredTypeVariables) {
            hasMetadataTypes = beanTypes.stream().anyMatch(CdiUtil::hasRawTypeArguments);
        }
        if (!hasMetadataTypes) {
            return;
        }
        for (ClassElement type : beanTypes) {
            BeanTypeArguments typeArguments = toTypeArguments(type, collapseDeclaredTypeVariables);
            beanDefinition.annotate(AnnotationValue.builder(org.eclipse.odi.cdi.processor.AnnotationUtil.ANN_BEAN_TYPE)
                    .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(type.getName()))
                    .member("arguments", typeArguments.values.toArray(new AnnotationClassValue<?>[0]))
                    .member("argumentCounts", typeArguments.counts.stream().mapToInt(Integer::intValue).toArray())
                    .member("typeVariables", toBooleanArray(typeArguments.typeVariables))
                    .member("typeVariableNames", typeArguments.typeVariableNames.toArray(String[]::new))
                    .build());
        }
    }

    private static ClassElement resolveBeanType(Element beanDefinition) {
        if (beanDefinition instanceof MethodElement) {
            return ((MethodElement) beanDefinition).getGenericReturnType();
        }
        if (beanDefinition instanceof FieldElement) {
            return ((FieldElement) beanDefinition).getGenericField();
        }
        if (beanDefinition instanceof ClassElement) {
            return (ClassElement) beanDefinition;
        }
        return null;
    }

    private static void collectBeanTypes(ClassElement beanType, Set<ClassElement> beanTypes) {
        if (beanType == null || beanType.getName().equals(Object.class.getName())) {
            return;
        }
        beanTypes.add(beanType);
        if (beanType.isArray()) {
            return;
        }
        for (ClassElement interfaceType : beanType.getInterfaces()) {
            collectBeanTypes(interfaceType, beanTypes);
        }
        beanType.getSuperType().ifPresent(superType -> collectBeanTypes(superType, beanTypes));
    }

    private static boolean hasTypeArguments(ClassElement beanType, boolean collapseDeclaredTypeVariables) {
        return !resolveTypeArguments(beanType, collapseDeclaredTypeVariables).isEmpty();
    }

    private static boolean hasRawTypeArguments(ClassElement beanType) {
        return JavaClassElementHelper.isRawClassElement(beanType) && !beanType.getTypeArguments().isEmpty();
    }

    private static BeanTypeArguments toTypeArguments(ClassElement beanType, boolean collapseDeclaredTypeVariables) {
        BeanTypeArguments metadata = new BeanTypeArguments();
        collectTypeArguments(resolveTypeArguments(beanType, collapseDeclaredTypeVariables), metadata, collapseDeclaredTypeVariables);
        return metadata;
    }

    private static void collectTypeArguments(List<ClassElement> typeArguments,
                                             BeanTypeArguments metadata,
                                             boolean collapseDeclaredTypeVariables) {
        for (ClassElement typeArgument : typeArguments) {
            boolean typeVariable = typeArgument instanceof GenericPlaceholderElement;
            ClassElement storedTypeArgument = typeArgument;
            List<? extends ClassElement> typeVariableBounds = List.of();
            if (typeVariable) {
                GenericPlaceholderElement placeholder = (GenericPlaceholderElement) typeArgument;
                ClassElement resolvedTypeArgument = placeholder.getResolved().orElse(null);
                if (resolvedTypeArgument != null) {
                    storedTypeArgument = resolvedTypeArgument;
                    typeVariable = false;
                } else {
                    typeVariableBounds = placeholder.getBounds();
                    if (!typeVariableBounds.isEmpty()) {
                        storedTypeArgument = typeVariableBounds.get(0);
                    }
                }
            }
            List<ClassElement> nestedTypeArguments = typeVariable
                    ? new ArrayList<>(typeVariableBounds)
                    : resolveTypeArguments(storedTypeArgument, collapseDeclaredTypeVariables);
            metadata.values.add(new AnnotationClassValue<>(storedTypeArgument.getName()));
            metadata.counts.add(nestedTypeArguments.size());
            metadata.typeVariables.add(typeVariable);
            metadata.typeVariableNames.add(typeVariable ? ((GenericPlaceholderElement) typeArgument).getVariableName() : "");
            collectTypeArguments(nestedTypeArguments, metadata, collapseDeclaredTypeVariables);
        }
    }

    private static List<ClassElement> resolveTypeArguments(ClassElement beanType, boolean collapseDeclaredTypeVariables) {
        List<ClassElement> typeArguments = new ArrayList<>(beanType.getBoundGenericTypes());
        if (typeArguments.isEmpty() && !beanType.getTypeArguments().isEmpty()) {
            typeArguments.addAll(beanType.getTypeArguments().values());
        }
        if (collapseDeclaredTypeVariables
                && !typeArguments.isEmpty()
                && typeArguments.stream().allMatch(typeArgument -> isDeclaredTypeVariable(beanType, typeArgument))) {
            return List.of();
        }
        if (!typeArguments.isEmpty() && typeArguments.stream().allMatch(CdiUtil::isRawTypeVariable)) {
            return List.of();
        }
        if (JavaClassElementHelper.isRawClassElement(beanType)
                && (typeArguments.isEmpty() || typeArguments.stream().allMatch(typeArgument -> isDeclaredTypeVariable(beanType, typeArgument)))) {
            return List.of();
        }
        if (typeArguments.isEmpty() && !beanType.getDeclaredGenericPlaceholders().isEmpty()) {
            for (GenericPlaceholderElement placeholder : beanType.getDeclaredGenericPlaceholders()) {
                typeArguments.add(placeholder.getBounds().get(0));
            }
        }
        if (typeArguments.isEmpty()) {
            return List.of();
        }
        return typeArguments;
    }

    private static boolean isRawTypeVariable(ClassElement typeArgument) {
        return typeArgument instanceof GenericPlaceholderElement && typeArgument.isRawType();
    }

    private static boolean isDeclaredTypeVariable(ClassElement beanType, ClassElement typeArgument) {
        if (!(typeArgument instanceof GenericPlaceholderElement)) {
            return false;
        }
        GenericPlaceholderElement placeholder = (GenericPlaceholderElement) typeArgument;
        Map<String, ClassElement> typeArguments = beanType.getTypeArguments();
        ClassElement namedTypeArgument = typeArguments.get(placeholder.getVariableName());
        if (namedTypeArgument instanceof GenericPlaceholderElement
                && ((GenericPlaceholderElement) namedTypeArgument).getVariableName().equals(placeholder.getVariableName())) {
            return true;
        }
        return beanType.getDeclaredGenericPlaceholders().stream()
                .anyMatch(declaredPlaceholder -> declaredPlaceholder.getVariableName().equals(placeholder.getVariableName()));
    }

    private static boolean[] toBooleanArray(List<Boolean> values) {
        boolean[] array = new boolean[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    private static final class BeanTypeArguments {
        private final List<AnnotationClassValue<?>> values = new ArrayList<>();
        private final List<Integer> counts = new ArrayList<>();
        private final List<Boolean> typeVariables = new ArrayList<>();
        private final List<String> typeVariableNames = new ArrayList<>();
    }

    public static boolean hasDependentScope(Element beanDefinition, VisitorContext context) {
        for (String annotationName : beanDefinition.getDeclaredAnnotationNames()) {
            if (annotationName.equals(jakarta.enterprise.context.Dependent.class.getName())) {
                continue;
            }
            boolean nonDependentScope = context.getClassElement(annotationName)
                    .map(annotation -> annotation.hasAnnotation(jakarta.enterprise.context.NormalScope.class.getName())
                            || annotation.hasAnnotation(jakarta.inject.Scope.class.getName())
                            || annotation.hasStereotype(jakarta.enterprise.context.NormalScope.class.getName())
                            || annotation.hasStereotype(AnnotationUtil.SCOPE)
                            || annotation.hasStereotype(jakarta.inject.Scope.class.getName()))
                    .orElse(false);
            if (nonDependentScope) {
                return false;
            }
        }
        return true;
    }

    public static boolean visitInjectPoint(VisitorContext context, TypedElement injectPoint) {
        if (needsDefaultInjectPointQualifier(injectPoint.getAnnotationMetadata())) {
            injectPoint.annotate(Default.class);
        }
        return CdiUtil.validateInjectedType(context, injectPoint.getGenericType(), injectPoint);
    }

    public static boolean validateInjectedType(VisitorContext context, ClassElement classElement, Element owningElement) {
        if (classElement.getName().equals(Instance.class.getName()) && isNoGenericType(classElement)) {
            context.fail("jakarta.enterprise.inject.Instance must have a required type parameter specified", owningElement);
            return true;
        }
        if (classElement.getName().equals(Event.class.getName()) && isNoGenericType(classElement)) {
            context.fail("jakarta.enterprise.event.Event must have a required type parameter specified", owningElement);
            return true;
        }
        return false;
    }

    private static boolean isNoGenericType(ClassElement classElement) {
        return classElement.getTypeArguments().isEmpty();
    }
}
