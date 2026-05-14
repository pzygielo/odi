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
package org.eclipse.odi.cdi.processor;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.inject.Stereotype;
import jakarta.interceptor.Interceptor;

/**
 * Internal annotation utils.
 */
@Internal
public final class AnnotationUtil {

    public static final String ANN_NAME = io.micronaut.core.annotation.AnnotationUtil.NAMED;
    public static final String ANN_BEAN_TYPE = "org.eclipse.odi.cdi.annotation.OdiBeanType";
    public static final String ANN_DISPOSER_METHOD = "org.eclipse.odi.cdi.annotation.DisposerMethod";
    public static final String ANN_ODI_BEAN_DEFINITION = "org.eclipse.odi.cdi.annotation.OdiBeanDefinition";
    public static final String ANN_ODI_UNPROXYABLE_BEAN = "org.eclipse.odi.cdi.annotation.OdiUnproxyableBean";
    public static final String ANN_NAMED_BY_STEREOTYPE = "org.eclipse.odi.cdi.annotation.NamedByStereotype";
    public static final String ANN_OBSERVES_METHOD = "org.eclipse.odi.cdi.annotation.ObservesMethod";
    private static final String ANN_DECORATOR = "jakarta.decorator.Decorator";

    private AnnotationUtil() {
    }

    @SuppressWarnings("unchecked")
    public static boolean hasBeanDefiningAnnotation(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata.hasAnnotation(ANN_DECORATOR)) {
            return false;
        }
        return annotationMetadata.hasStereotype(
                Factory.class,
                Dependent.class,
                NormalScope.class,
                Stereotype.class,
                Interceptor.class,
                Bean.class
        ) || annotationMetadata.hasStereotype(io.micronaut.core.annotation.AnnotationUtil.SCOPE);
    }
}
