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
package org.eclipse.odi.cdi;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.BeanDefinition;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.util.function.Supplier;

/**
 * Tracks the CDI injection point that applies while a dependent bean is created through {@code Instance#get()}.
 */
@Internal
final class OdiCurrentInjectionPoint {

    private static final ScopedValue<Entry> CURRENT = ScopedValue.newInstance();

    private OdiCurrentInjectionPoint() {
    }

    static <T> T call(OdiBean<?> targetBean, InjectionPoint injectionPoint, Supplier<T> supplier) {
        Entry entry = new Entry(targetBean.getBeanDefinition(), injectionPoint, currentEntry());
        return ScopedValue.where(CURRENT, entry).call(supplier::get);
    }

    @Nullable
    static InjectionPoint current(BeanResolutionContext resolutionContext) {
        Entry entry = currentEntry();
        if (entry == null) {
            return null;
        }
        BeanResolutionContext.Segment<?, ?> currentSegment = resolutionContext.getPath()
                .currentSegment()
                .orElse(null);
        if (currentSegment == null) {
            return null;
        }
        BeanDefinition<?> declaringType = currentSegment.getDeclaringType();
        while (entry != null) {
            if (entry.matches(declaringType)) {
                return entry.injectionPoint;
            }
            entry = entry.previous;
        }
        return null;
    }

    @Nullable
    private static Entry currentEntry() {
        return CURRENT.isBound() ? CURRENT.get() : null;
    }

    private static final class Entry {

        private final BeanDefinition<?> targetDefinition;
        private final InjectionPoint injectionPoint;
        private final Entry previous;

        private Entry(BeanDefinition<?> targetDefinition, InjectionPoint injectionPoint, @Nullable Entry previous) {
            this.targetDefinition = targetDefinition;
            this.injectionPoint = injectionPoint;
            this.previous = previous;
        }

        private boolean matches(BeanDefinition<?> declaringType) {
            if (declaringType == targetDefinition || declaringType.equals(targetDefinition)) {
                return true;
            }
            return declaringType.getBeanType().isAssignableFrom(targetDefinition.getBeanType());
        }
    }
}
