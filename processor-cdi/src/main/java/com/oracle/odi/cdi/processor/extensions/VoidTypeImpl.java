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
package com.oracle.odi.cdi.processor.extensions;

import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.PrimitiveElement;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.types.VoidType;

final class VoidTypeImpl extends AnnotationTargetImpl implements VoidType {
    static final VoidTypeImpl INSTANCE = new VoidTypeImpl(PrimitiveElement.VOID, null);

    VoidTypeImpl(Element element, Types types) {
        super(element, types);
    }

    @Override
    public String name() {
        return "void";
    }
}
