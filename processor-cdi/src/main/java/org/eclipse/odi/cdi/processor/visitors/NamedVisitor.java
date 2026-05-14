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
package org.eclipse.odi.cdi.processor.visitors;

import org.eclipse.odi.cdi.processor.AnnotationUtil;
import org.eclipse.odi.cdi.processor.CdiUtil;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.Stereotype;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Validates elements annotated with {@link jakarta.inject.Named}.
 */
public class NamedVisitor implements TypeElementVisitor<Object, Object> {
    private static final String DEPLOYMENT_EXCEPTION_MARKER = "[ODI_DEPLOYMENT_EXCEPTION] ";

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.singleton(io.micronaut.core.annotation.AnnotationUtil.NAMED);
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        applyCdiDefaultName(element);
        validateElement(element, context);
        validateAmbiguousBeanName(element, context);
    }

    private void validateElement(Element element, VisitorContext context) {
        if (element.hasAnnotation(AnnotationUtil.ANN_NAME) || element.hasStereotype(AnnotationUtil.ANN_NAME)) {
            if (element instanceof ParameterElement && element.stringValue(AnnotationUtil.ANN_NAME).isEmpty()) {
                context.fail("@Named injection points that are not fields must specify a value", element);
                return;
            }
            element.stringValue(AnnotationUtil.ANN_NAME).ifPresent((name) -> validateIdentifier(name, element, context));
            // now validate stereotypes are correct
            // stereotypes can only have an empty @Named qualifier
            // see https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#named_stereotype
            final List<String> stereotypes = element.getAnnotationNamesByStereotype(Stereotype.class);

            // if @Named is inherited via a stereotype
            if (!element.hasDeclaredAnnotation(AnnotationUtil.ANN_NAME)) {
                final List<String> namedStereotypes = element.getAnnotationNamesByStereotype(AnnotationUtil.ANN_NAME);
                if (stereotypes.containsAll(namedStereotypes)) {
                    if (element.stringValue(AnnotationUtil.ANN_NAME).isPresent()) {
                        context.fail("Stereotypes [" + CdiUtil
                                             .toAnnotationDescription(stereotypes) + "] cannot define a @Named qualifier with a"
                                             + " value. See " + CdiUtil.SPEC_LOCATION + "#named_stereotype",
                                     element);
                    }
                }
            }
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        applyCdiDefaultName(element);
        validatedNamedIfPresent(element, context);
        for (ParameterElement parameter : element.getParameters()) {
            validateElement(parameter, context);
        }
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        for (ParameterElement parameter : element.getParameters()) {
            validateElement(parameter, context);
        }
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        applyCdiDefaultName(element);
        validatedNamedIfPresent(element, context);
        validateElement(element, context);
    }

    private static void applyCdiDefaultName(Element element) {
        boolean namedByStereotype = isNamedByStereotype(element);
        if (namedByStereotype) {
            element.annotate(AnnotationUtil.ANN_NAMED_BY_STEREOTYPE);
            if (element.stringValue(AnnotationUtil.ANN_NAME).isEmpty() && isBeanNamingElement(element)) {
                element.annotate(AnnotationUtil.ANN_NAME, builder -> builder.value(cdiDefaultBeanName(element)));
            }
        }
        if (element.hasDeclaredAnnotation(AnnotationUtil.ANN_NAME)
                && element.stringValue(AnnotationUtil.ANN_NAME).isEmpty()
                && isBeanNamingElement(element)) {
            element.annotate(AnnotationUtil.ANN_NAME, builder -> builder.value(cdiDefaultBeanName(element)));
        }
    }

    private static boolean isNamedByStereotype(Element element) {
        if (element.hasDeclaredAnnotation(AnnotationUtil.ANN_NAME)) {
            return false;
        }
        List<String> stereotypes = element.getAnnotationNamesByStereotype(Stereotype.class);
        if (stereotypes.isEmpty()) {
            return false;
        }
        List<String> namedStereotypes = element.getAnnotationNamesByStereotype(AnnotationUtil.ANN_NAME);
        return !namedStereotypes.isEmpty() && stereotypes.containsAll(namedStereotypes);
    }

    private static boolean isBeanNamingElement(Element element) {
        if (element instanceof ClassElement) {
            return !((ClassElement) element).isInterface();
        }
        return true;
    }

    private static String cdiDefaultBeanName(Element element) {
        String name;
        if (element instanceof ClassElement) {
            name = element.getSimpleName();
        } else if (element instanceof MethodElement) {
            String methodName = element.getName();
            if (NameUtils.isGetterName(methodName)) {
                name = NameUtils.getPropertyNameForGetter(methodName);
            } else {
                name = methodName;
            }
        } else {
            name = element.getName();
        }
        return lowerFirstCodePoint(name);
    }

    private static String lowerFirstCodePoint(String name) {
        if (name.isEmpty()) {
            return name;
        }
        int firstCodePoint = name.codePointAt(0);
        int lowerFirstCodePoint = Character.toLowerCase(firstCodePoint);
        if (firstCodePoint == lowerFirstCodePoint) {
            return name;
        }
        int firstCodePointLength = Character.charCount(firstCodePoint);
        return new StringBuilder(name.length())
                .appendCodePoint(lowerFirstCodePoint)
                .append(name.substring(firstCodePointLength))
                .toString();
    }

    private void validatedNamedIfPresent(Element element, VisitorContext context) {
        if (element.hasAnnotation(AnnotationUtil.ANN_NAME) || element.hasStereotype(AnnotationUtil.ANN_NAME)) {
            element.stringValue(AnnotationUtil.ANN_NAME).ifPresent((name) -> validateIdentifier(name, element, context));
        }
    }

    private static void validateAmbiguousBeanName(ClassElement element, VisitorContext context) {
        Set<String> configuredBeanClasses = configuredBeanClasses(context);
        if (configuredBeanClasses.isEmpty()
                || !isNamedBeanClass(element)) {
            return;
        }
        Optional<String> beanName = resolveBeanName(element);
        if (beanName.isEmpty()) {
            return;
        }
        if (!isNameResolutionCandidate(context, element)) {
            return;
        }
        for (String configuredBeanClass : configuredBeanClasses) {
            if (configuredBeanClass.equals(element.getName())) {
                continue;
            }
            Optional<ClassElement> candidate = context.getClassElement(configuredBeanClass);
            if (candidate.isEmpty() || !isNamedBeanClass(candidate.get())) {
                continue;
            }
            if (!isNameResolutionCandidate(context, candidate.get())) {
                continue;
            }
            Optional<String> candidateName = resolveBeanName(candidate.get());
            if (candidateName.isPresent()
                    && isAmbiguousBeanName(beanName.get(), candidateName.get())
                    && !hasResolvableAmbiguity(context, element, candidate.get())) {
                context.fail(
                        DEPLOYMENT_EXCEPTION_MARKER
                                + "Ambiguous bean name '" + beanName.get()
                                + "' conflicts with bean name '" + candidateName.get() + "'",
                        element
                );
                return;
            }
        }
    }

    private static boolean isNamedBeanClass(ClassElement element) {
        return !element.isInterface()
                && AnnotationUtil.hasBeanDefiningAnnotation(element)
                && CdiUtil.isBeanClass(element)
                && (element.hasAnnotation(AnnotationUtil.ANN_NAME) || element.hasStereotype(AnnotationUtil.ANN_NAME));
    }

    private static Optional<String> resolveBeanName(ClassElement element) {
        Optional<String> explicitName = element.stringValue(AnnotationUtil.ANN_NAME);
        if (explicitName.isPresent() && StringUtils.isNotEmpty(explicitName.get())) {
            return explicitName;
        }
        if (isBeanNamingElement(element)) {
            return Optional.of(cdiDefaultBeanName(element));
        }
        return Optional.empty();
    }

    private static boolean isNameResolutionCandidate(VisitorContext context, ClassElement element) {
        return !isAlternative(element) || hasPriority(element) || isSelectedAlternative(context, element);
    }

    private static boolean hasResolvableAmbiguity(VisitorContext context, ClassElement element, ClassElement candidate) {
        return isResolvingAlternative(context, element) || isResolvingAlternative(context, candidate);
    }

    private static boolean isResolvingAlternative(VisitorContext context, ClassElement element) {
        return isAlternative(element) && (hasPriority(element) || isSelectedAlternative(context, element));
    }

    private static boolean isAlternative(ClassElement element) {
        return element.hasAnnotation(jakarta.enterprise.inject.Alternative.class)
                || element.hasStereotype(jakarta.enterprise.inject.Alternative.class);
    }

    private static boolean hasPriority(ClassElement element) {
        return element.hasAnnotation(jakarta.annotation.Priority.class)
                || element.hasStereotype(jakarta.annotation.Priority.class);
    }

    private static boolean isSelectedAlternative(VisitorContext context, ClassElement element) {
        String selectedAlternatives = context.getOptions().get("odi.selected-alternatives");
        if (selectedAlternatives == null || selectedAlternatives.isBlank()) {
            selectedAlternatives = System.getProperty("odi.selected-alternatives");
        }
        if (selectedAlternatives == null || selectedAlternatives.isBlank()) {
            return false;
        }
        for (String selectedAlternative : selectedAlternatives.split(",")) {
            if (selectedAlternative.trim().equals(element.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAmbiguousBeanName(String beanName, String candidateName) {
        return beanName.equals(candidateName)
                || beanName.startsWith(candidateName + ".")
                || candidateName.startsWith(beanName + ".");
    }

    private static Set<String> configuredBeanClasses(VisitorContext context) {
        String classNames = context.getOptions().get(CdiUtil.BEAN_CLASSES_OPTION);
        if (classNames == null || classNames.isBlank()) {
            classNames = System.getProperty(CdiUtil.BEAN_CLASSES_OPTION);
        }
        if (classNames == null || classNames.isBlank()) {
            return Set.of();
        }
        Set<String> beanClasses = new LinkedHashSet<>();
        for (String className : classNames.split(",")) {
            String trimmedClassName = className.trim();
            if (!trimmedClassName.isEmpty()) {
                beanClasses.add(trimmedClassName);
            }
        }
        return beanClasses;
    }

    private static boolean isJavaIdentifier(String name) {
        int start = name.codePointAt(0);
        if (!Character.isJavaIdentifierStart(start)) {
            return false;
        }
        int charCount = Character.charCount(start);
        for (int i = charCount; i < name.length(); i += charCount) {
            int codePoint = name.codePointAt(i);
            if (!Character.isJavaIdentifierPart(codePoint)) {
                return false;
            }
        }
        return true;
    }

    private void validateIdentifier(String name, Element element, VisitorContext visitorContext) {
        if (StringUtils.isNotEmpty(name)) {

            final String[] parts = name.split("\\.");
            for (String part : parts) {
                if (!isJavaIdentifier(part)) {
                    visitorContext.fail(
                            "@Named annotation specifies an invalid name. See " + CdiUtil.SPEC_LOCATION + "#names",
                            element
                    );
                    break;
                }
            }
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
