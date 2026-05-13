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
package org.eclipse.odi.cdi.annotation;

import io.micronaut.core.annotation.Internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Internal metadata for a CDI bean type computed at build time.
 */
@Internal
@Repeatable(OdiBeanTypes.class)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface OdiBeanType {
    /**
     * @return The raw bean type.
     */
    Class<?> value();

    /**
     * @return Type arguments for the bean type.
     */
    Class<?>[] arguments() default {};

    /**
     * @return Number of nested type arguments for each flattened type argument.
     */
    int[] argumentCounts() default {};

    /**
     * @return Whether each flattened type argument represents a type variable.
     */
    boolean[] typeVariables() default {};

    /**
     * @return Whether each flattened type argument represents a wildcard.
     */
    boolean[] wildcards() default {};

    /**
     * @return Number of lower bounds for each flattened wildcard argument.
     */
    int[] lowerBoundCounts() default {};

    /**
     * @return Type variable names for flattened type variable arguments.
     */
    String[] typeVariableNames() default {};
}
