/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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
package org.eclipse.odi.cdi.processor.transformers;

import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.Stereotype;

import java.util.List;

/**
 * Transforms CDI stereotypes into Micronaut bean-defining annotations.
 */
public class StereotypeTransformer implements TypedAnnotationTransformer<Stereotype> {

    @Override
    public Class<Stereotype> annotationType() {
        return Stereotype.class;
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Stereotype> annotation, VisitorContext visitorContext) {
        return List.of(
                annotation,
                AnnotationValue.builder(Bean.class).build()
        );
    }
}
