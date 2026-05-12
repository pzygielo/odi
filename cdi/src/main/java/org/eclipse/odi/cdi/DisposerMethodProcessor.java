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
package org.eclipse.odi.cdi;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Disposes;
import jakarta.inject.Singleton;
import org.eclipse.odi.cdi.annotation.DisposerMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Collects CDI disposer methods and invokes them from Micronaut pre-destroy events.
 */
@Singleton
@Any
final class DisposerMethodProcessor implements ExecutableMethodProcessor<DisposerMethod>, BeanPreDestroyEventListener<Object> {
    private static final Logger LOG = LoggerFactory.getLogger(DisposerMethodProcessor.class);

    private final BeanProvider<OdiBeanContainer> beanContainer;
    private final Map<DisposerKey, DisposerDef> disposerMethods = new HashMap<>(20);
    private final Map<DisposerKey, DisposerDef> anyDisposerMethods = new HashMap<>(20);

    DisposerMethodProcessor(BeanProvider<OdiBeanContainer> beanContainer) {
        this.beanContainer = beanContainer;
    }

    @Override
    public <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
        final Argument<?>[] arguments = method.getArguments();
        for (Argument<?> argument : arguments) {
            if (argument.getAnnotationMetadata().isAnnotationPresent(Disposes.class)) {
                Qualifier<Object> qualifier = Qualifiers.forArgument(argument);
                if (qualifier == null) {
                    qualifier = Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, Default.class);
                }
                boolean staticMethod = method.booleanValue(DisposerMethod.class, "staticMethod").orElse(false);
                if (qualifier.contains(AnyQualifier.INSTANCE)) {
                    anyDisposerMethods.put(new DisposerKey(argument, null), new DisposerDef(beanDefinition, method, staticMethod));
                } else {
                    disposerMethods.put(new DisposerKey(argument, qualifier), new DisposerDef(beanDefinition, method, staticMethod));
                }
                break;
            }
        }
    }

    @Override
    public Object onPreDestroy(BeanPreDestroyEvent<Object> event) {
        final Object bean = event.getBean();
        final BeanDefinition<?> beanDefinition = event.getBeanDefinition();
        try {
            Argument<?> type = beanDefinition.asArgument();
            Qualifier<?> qualifier = beanDefinition.getDeclaredQualifier();
            if (qualifier == null) {
                qualifier = Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, Default.class);
            }
            DisposerDef<Object> disposerDef = disposerMethods.get(new DisposerKey(type, qualifier));
            if (disposerDef == null) {
                disposerDef = anyDisposerMethods.get(new DisposerKey(type, null));
            }
            if (disposerDef != null) {
                Optional<Class<?>> producedDeclaringType = beanDefinition.getDeclaringType();
                Optional<Class<?>> disposeDeclaringType = disposerDef.definition.getDeclaringType();
                if (disposeDeclaringType.isPresent()
                        && producedDeclaringType.isPresent()
                        && !producedDeclaringType.get().equals(disposeDeclaringType.get())) {
                    return bean;
                }
                beanContainer.get().fulfillAndExecuteMethod(disposerDef.definition, disposerDef.executableMethod, argument -> {
                    if (argument.getAnnotationMetadata().hasAnnotation(Disposes.class)) {
                        return bean;
                    }
                    return null;
                }, disposerDef.staticMethod);
            }
        } catch (Throwable e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error invoking Disposer method for bean [" + beanDefinition.getBeanType() + "]: " + e.getMessage(), e);
            }
        }
        return bean;
    }

    private static final class DisposerKey {
        private final Argument<?> argument;
        private final Qualifier<?> qualifier;
        private final int typeHashCode;

        DisposerKey(Argument<?> argument, @Nullable Qualifier<?> qualifier) {
            this.argument = argument;
            this.qualifier = qualifier;
            this.typeHashCode = argument.typeHashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DisposerKey that = (DisposerKey) o;
            return argument.equalsType(that.argument) && Objects.equals(qualifier, that.qualifier);
        }

        @Override
        public int hashCode() {
            return typeHashCode;
        }
    }

    private static final class DisposerDef<B> {
        private final BeanDefinition<B> definition;
        private final ExecutableMethod<B, Object> executableMethod;
        private final boolean staticMethod;

        DisposerDef(BeanDefinition<B> definition, ExecutableMethod<B, Object> executableMethod, boolean staticMethod) {
            this.definition = definition;
            this.executableMethod = executableMethod;
            this.staticMethod = staticMethod;
        }
    }
}
