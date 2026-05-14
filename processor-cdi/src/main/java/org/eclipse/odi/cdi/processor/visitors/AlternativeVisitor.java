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

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import org.eclipse.odi.cdi.processor.CdiUtil;

import java.util.OptionalInt;
import java.util.Set;

/**
 * Processes {@link jakarta.enterprise.inject.Alternative} elements and adds required Micronaut annotations.
 */
public class AlternativeVisitor implements TypeElementVisitor<Alternative, Object> {
    private static final AnnotationClassValue<Object> SELECTED_ALTERNATIVE_CONDITION =
            new AnnotationClassValue<>("org.eclipse.odi.cdi.condition.SelectedAlternativeCondition");

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return CollectionUtils.setOf(jakarta.enterprise.inject.Alternative.class.getName());
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.hasAnnotation(Bean.class)) {
            element.annotate(Bean.class);
        }
        CdiUtil.visitPriority(context, element);
        disableUnselectedAlternative(element, context);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (!prepareMemberAlternative(element)) {
            return;
        }
        disableUnselectedAlternative(element, context);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        if (!prepareMemberAlternative(element)) {
            return;
        }
        disableUnselectedAlternative(element, context);
    }

    private boolean prepareMemberAlternative(MemberElement element) {
        if (element.hasDeclaredAnnotation(Alternative.class)) {
            return true;
        }
        if (!element.hasDeclaredAnnotation(Produces.class)) {
            return false;
        }
        ClassElement declaringType = element.getDeclaringType();
        if (!declaringType.hasAnnotation(Alternative.class) && !declaringType.hasStereotype(Alternative.class)) {
            return false;
        }
        element.annotate(Alternative.class);
        OptionalInt priority = declaringType.intValue(Priority.class);
        if (priority.isPresent()) {
            int value = priority.getAsInt();
            element.annotate(Priority.class, builder -> builder.value(value));
            element.annotate(Order.class, builder -> builder.value(-value));
        }
        return true;
    }

    private void disableUnselectedAlternative(Element element, VisitorContext context) {
        if (!element.hasDeclaredAnnotation(Order.class)
                && !element.hasDeclaredAnnotation(Priority.class)
                && !element.hasStereotype(Priority.class)) {
            // no priority specified so disable by default
            element.annotate(Order.class, (builder) ->
                    builder.value(Ordered.HIGHEST_PRECEDENCE)
            );
            element.annotate(Requires.class, (builder) ->
                    builder.member("condition", SELECTED_ALTERNATIVE_CONDITION)
            );
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
