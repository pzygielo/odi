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
package org.eclipse.odi.cdi.processor.extensions;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Order;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.annotation.Priority;
import jakarta.interceptor.InterceptorBinding;
import org.eclipse.odi.cdi.processor.transformers.InterceptorBindingTransformer;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedHashMap;
import java.util.Map;

final class AnnotationConfigSupport {

    private AnnotationConfigSupport() {
    }

    static void annotate(Element element, AnnotationValue<?> annotationValue, VisitorContext visitorContext) {
        if (annotationValue != null) {
            element.annotate(annotationValue);
            if (isInterceptorBinding(annotationValue, visitorContext)) {
                annotateInterceptorBindings(element, annotationValue);
            }
            if (annotationValue.getAnnotationName().equals(Priority.class.getName())) {
                element.removeAnnotation(Order.class);
                element.annotate(Order.class, (builder) ->
                    builder.value(-annotationValue.intValue().orElse(0))
                );
            }
        }
    }

    private static boolean isInterceptorBinding(AnnotationValue<?> annotationValue, VisitorContext visitorContext) {
        return visitorContext.getClassElement(annotationValue.getAnnotationName())
                .map(classElement -> classElement.hasDeclaredAnnotation(InterceptorBinding.class))
                .orElse(false);
    }

    private static void annotateInterceptorBindings(Element element, AnnotationValue<?> annotationValue) {
        AnnotationValue<Annotation> bindingValues = bindingValues(annotationValue);
        for (AnnotationValue<?> interceptorBinding : InterceptorBindingTransformer.INTERCEPTOR_BINDING_VALUES) {
            element.annotate(AnnotationValue.builder(interceptorBinding, RetentionPolicy.RUNTIME)
                    .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(annotationValue.getAnnotationName()))
                    .member(InterceptorBindingQualifier.META_BINDING_VALUES, bindingValues)
                    .build());
        }
    }

    private static AnnotationValue<Annotation> bindingValues(AnnotationValue<?> annotationValue) {
        Map<CharSequence, Object> values = new LinkedHashMap<>(annotationValue.getValues());
        values.remove(AnnotationUtil.NON_BINDING_ATTRIBUTE);
        for (String nonBinding : annotationValue.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE)) {
            values.remove(nonBinding);
        }
        return AnnotationValue.<Annotation>builder(annotationValue.getAnnotationName())
                .members(values)
                .build();
    }
}
