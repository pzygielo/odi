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
package org.eclipse.odi.cdi;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.InjectionPoint;
import jakarta.enterprise.context.Dependent;

import java.util.ArrayList;

/**
 * Creates instances of {@link jakarta.enterprise.inject.spi.InjectionPoint}.
 */
@Factory
public class OdiInjectPointFactory {

    private static final String INTRODUCTION_TYPE = "io.micronaut.aop.Introduction";

    /**
     * Builds an instance.
     *
     * @param resolutionContext      The resolution context
     * @param beanContainer          The bean container
     * @param <T>                    The generic type
     * @return The instance
     */
    @Any
    @Dependent
    @Nullable
    public <T> jakarta.enterprise.inject.spi.InjectionPoint build(BeanResolutionContext resolutionContext,
                                                                  OdiBeanContainer beanContainer) {
        jakarta.enterprise.inject.spi.InjectionPoint currentInjectionPoint = OdiCurrentInjectionPoint.current(resolutionContext);
        if (currentInjectionPoint != null) {
            return currentInjectionPoint;
        }
        ArgumentInjectionPoint<T, ?> injectionPoint = (ArgumentInjectionPoint<T, ?>) provideInjectionPoint(resolutionContext);
        if (injectionPoint == null) {
            return null;
        }
        OdiBean<T> bean = beanContainer.getBean(injectionPoint.getDeclaringBean());

        return new OdiInjectionPoint(resolutionContext.getContext().getClassLoader(), bean, injectionPoint, injectionPoint.asArgument());
    }

    public static <T> InjectionPoint<T> provideInjectionPoint(BeanResolutionContext resolutionContext) {
        ArrayList<BeanResolutionContext.Segment<?, ?>> paths = new ArrayList<>(resolutionContext.getPath());
        if (paths.isEmpty()) {
            return null;
        }
        BeanResolutionContext.Segment<?, ?> current = paths.remove(0);
        Class<?> currentDeclaringType = current.getDeclaringType().getBeanType();
        for (BeanResolutionContext.Segment<?, ?> segment : paths) {
            if (segment.getDeclaringType().hasStereotype(INTRODUCTION_TYPE)) {
                continue;
            }
            if (segment.getDeclaringType().getBeanType().equals(currentDeclaringType)) {
                continue;
            }
            InjectionPoint<T> ip = (InjectionPoint<T>) segment.getInjectionPoint();
            if (ip != null) {
                return ip;
            }
        }
        return null;
    }
}
