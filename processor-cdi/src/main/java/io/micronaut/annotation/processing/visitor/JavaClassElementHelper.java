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
package io.micronaut.annotation.processing.visitor;

import java.util.Map;

import javax.lang.model.type.TypeMirror;

/**
 * Needs to be addressed in the core.
 */
public final class JavaClassElementHelper {
    private JavaClassElementHelper() {
    }

    /**
     * Method to get the generic info.
     * @param classElement The class element
     * @return A map of generics
     */
    // TODO: Replace with new generics API
    public static Map<String, Map<String, TypeMirror>> getGenericTypeInfo(JavaClassElement classElement) {
        return classElement.getGenericTypeInfo();
    }

}
