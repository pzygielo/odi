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

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import org.eclipse.odi.cdi.annotation.OdiConstructorTarget;
import org.eclipse.odi.cdi.processor.CdiUtil;

import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Processes {@link InterceptorBinding} elements to correctly handle it using Micronaut.
 */
public class InterceptorBindingVisitor implements TypeElementVisitor<Object, InterceptorBinding> {

    private static final int ADDITIONAL_PROXY_CONSTRUCTOR_PARAMETERS = 5;

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(
                InterceptorBinding.class.getName(),
                AroundInvoke.class.getName(),
                AnnotationUtil.ANN_INTERCEPTOR_BINDING,
                AnnotationUtil.ANN_INTERCEPTOR_BINDINGS
        );
    }

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        addNestedInterceptorBindings(element, element.getAnnotationMetadata(), context);
        if (element.isAnnotationPresent(Interceptor.class)) {
            // We are only interested in intercepted classes
            return;
        }

        List<MethodElement> allMethods = element.getEnclosedElements(
                ElementQuery.ALL_METHODS
                        .onlyInstance()
                        .onlyAccessible()
                        .onlyConcrete()
        );
        allMethods.forEach(InterceptorBindingVisitor::removeInheritedMethodInterceptorBindings);
        allMethods.forEach(methodElement -> addNestedInterceptorBindings(
                methodElement,
                methodElement.getMethodAnnotationMetadata().getAnnotationMetadata(),
                context
        ));

        List<MethodElement> selfInterceptorMethods = element.getEnclosedElements(
                ElementQuery.ALL_METHODS
                        .onlyInstance()
                        .onlyConcrete()
                        .onlyDeclared()
                        .filter(methodElement -> methodElement.hasAnnotation(AroundInvoke.class))
        );

        boolean hasClassInterceptorBinding = !element.getAnnotationNamesByStereotype(InterceptorBinding.class).isEmpty();
        boolean hasBusinessMethods = allMethods.stream().anyMatch(this::isBusinessMethod);
        boolean hasBoundBusinessMethod = allMethods.stream()
                .filter(this::isBusinessMethod)
                .anyMatch(this::hasInterceptorBinding);
        boolean hasClassOrMethodInterceptorBinding = (hasClassInterceptorBinding && hasBusinessMethods)
                || hasBoundBusinessMethod
                || !selfInterceptorMethods.isEmpty();

        if (hasClassInterceptorBinding
                || hasBoundBusinessMethod
                || element.getPrimaryConstructor().map(this::hasInterceptorBinding).orElse(false)
                || !selfInterceptorMethods.isEmpty()) {
            element.getPrimaryConstructor().ifPresent(this::annotateConstructorTarget);
        }

        if (hasClassOrMethodInterceptorBinding) {
            if (CdiUtil.validateInterceptedBeanConstructor(context, element)) {
                return;
            }
            element.annotate(Around.class, builder -> {
                builder
                        .member("proxyTarget", true)
                        .member("cacheableLazyTarget", true).build();
            });
        }

        selfInterceptorMethods.forEach(methodElement -> {
            if (methodElement.isPrivate()) {
                methodElement.annotate(Executable.class);
                methodElement.annotate(ReflectiveAccess.class);
            }
        });

        if (!selfInterceptorMethods.isEmpty()) {
            InterceptorVisitor.addInterceptor(element, context, element, true);
        }
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        addNestedInterceptorBindings(element, element.getMethodAnnotationMetadata().getAnnotationMetadata(), context);
        annotateConstructorTarget(element);
    }

    private void annotateConstructorTarget(MethodElement constructor) {
        constructor.annotate(OdiConstructorTarget.class, builder -> builder
                .member("value", new AnnotationClassValue<>(constructor.getDeclaringType().getName()))
                .member("additionalProxyParameters", ADDITIONAL_PROXY_CONSTRUCTOR_PARAMETERS));
    }

    static void removeInheritedMethodInterceptorBindings(MethodElement methodElement) {
        List<String> methodInterceptorBindings = methodElement.getMethodAnnotationMetadata()
                .getAnnotationMetadata()
                .getAnnotationNamesByStereotype(InterceptorBinding.class);
        Set<String> declaredAnnotationNames = methodElement.getDeclaredAnnotationNames();
        for (String annotationName : methodInterceptorBindings) {
            if (!declaredAnnotationNames.contains(annotationName)) {
                methodElement.removeAnnotation(annotationName);
            }
        }
    }

    static void addNestedInterceptorBindings(Element target, AnnotationMetadata sourceMetadata, VisitorContext context) {
        Set<String> interceptorBindings = new LinkedHashSet<>(
                sourceMetadata.getAnnotationNamesByStereotype(InterceptorBinding.class)
        );
        for (String interceptorBinding : interceptorBindings) {
            addNestedInterceptorBindings(target, sourceMetadata, context, interceptorBinding, new LinkedHashSet<>());
        }
    }

    private static void addNestedInterceptorBindings(Element target,
                                                     AnnotationMetadata sourceMetadata,
                                                     VisitorContext context,
                                                     String interceptorBinding,
                                                     Set<String> visiting) {
        if (!visiting.add(interceptorBinding)) {
            return;
        }
        try {
            context.getClassElement(interceptorBinding).ifPresent(bindingElement -> {
                List<String> nestedBindings = bindingElement.getAnnotationNamesByStereotype(InterceptorBinding.class);
                for (String nestedBinding : nestedBindings) {
                    if (isNestedInterceptorBinding(interceptorBinding, nestedBinding)
                            && !sourceMetadata.hasAnnotation(nestedBinding)) {
                        AnnotationValue<Annotation> nestedAnnotation = bindingElement.getAnnotation(nestedBinding);
                        if (nestedAnnotation != null) {
                            target.annotate(nestedAnnotation);
                            addNestedInterceptorBindings(
                                    target,
                                    sourceMetadata,
                                    context,
                                    nestedBinding,
                                    visiting
                            );
                        }
                    }
                }
            });
        } finally {
            visiting.remove(interceptorBinding);
        }
    }

    private static boolean isNestedInterceptorBinding(String interceptorBinding, String nestedBinding) {
        return !nestedBinding.equals(interceptorBinding)
                && !nestedBinding.equals(InterceptorBinding.class.getName());
    }

    private boolean hasInterceptorBinding(MethodElement methodElement) {
        return !methodElement.getMethodAnnotationMetadata()
                .getAnnotationMetadata()
                .getAnnotationNamesByStereotype(InterceptorBinding.class)
                .isEmpty();
    }

    private boolean isBusinessMethod(MethodElement methodElement) {
        return !isObjectMethod(methodElement)
                && !methodElement.hasAnnotation(AroundInvoke.class);
    }

    private boolean isObjectMethod(MethodElement methodElement) {
        return switch (methodElement.getName()) {
            case "toString", "hashCode", "getClass", "clone", "notify", "notifyAll" ->
                    methodElement.getParameters().length == 0;
            case "equals" -> methodElement.getParameters().length == 1
                    && Object.class.getName().equals(methodElement.getParameters()[0].getType().getName());
            case "wait" -> {
                int parameters = methodElement.getParameters().length;
                yield parameters >= 0 && parameters <= 2;
            }
            default -> Object.class.getName().equals(methodElement.getDeclaringType().getName());
        };
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
