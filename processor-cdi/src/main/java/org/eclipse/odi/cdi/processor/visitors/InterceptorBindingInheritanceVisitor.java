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
package org.eclipse.odi.cdi.processor.visitors;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InterceptorBinding;

import java.util.Set;

/**
 * Applies CDI member-level interceptor binding inheritance rules before AOP matching is resolved.
 */
public class InterceptorBindingInheritanceVisitor implements TypeElementVisitor<Object, Object> {

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
    public TypeElementQuery query() {
        return TypeElementQuery.onlyClass();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        element.getEnclosedElements(
                ElementQuery.ALL_METHODS
                        .onlyInstance()
                        .onlyAccessible()
                        .onlyConcrete()
        ).forEach(InterceptorBindingVisitor::removeInheritedMethodInterceptorBindings);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
