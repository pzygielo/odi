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
package com.oracle.odi.cdi.processor;

import java.util.Collections;
import java.util.List;

import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.Alternative;

/**
 * Maps {@link jakarta.enterprise.inject.Alternative} annotation to {@link io.micronaut.context.annotation.Bean}.
 */
public class AlternativeMapper implements TypedAnnotationMapper<Alternative> {

    @Override
    public Class<Alternative> annotationType() {
        return Alternative.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Alternative> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(Bean.class).build()
        );
    }
}
