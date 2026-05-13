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
package org.eclipse.odi.cdi.events;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import org.eclipse.odi.cdi.annotation.ObservesMethod;

/**
 * Bridges Micronaut 5 generated metadata that exposes the direct {@link Executable}
 * processing key instead of the ODI {@link ObservesMethod} key.
 */
@Singleton
@Internal
final class ExecutableObservesMethodProcessor implements ExecutableMethodProcessor<Executable> {

    private final ObservesMethodProcessor observesMethodProcessor;

    ExecutableObservesMethodProcessor(ObservesMethodProcessor observesMethodProcessor) {
        this.observesMethodProcessor = observesMethodProcessor;
    }

    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        if (!method.hasAnnotation(ObservesMethod.class)
                || method.getAnnotationMetadata().getAnnotationTypesByStereotype(Executable.class).contains(ObservesMethod.class)) {
            return;
        }
        observesMethodProcessor.processObservedMethod(beanDefinition, method);
    }
}
