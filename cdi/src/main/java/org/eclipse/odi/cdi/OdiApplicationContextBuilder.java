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

import io.micronaut.context.BeanResolutionCustomizer;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultApplicationContextBuilder;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.QualifiedBeanType;
import jakarta.enterprise.inject.TransientReference;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.Set;

/**
 * ODI specific {@link DefaultApplicationContextBuilder}.
 */
public final class OdiApplicationContextBuilder extends DefaultApplicationContextBuilder {
    public OdiApplicationContextBuilder() {
        deduceEnvironment(false);
        banner(false);
        allowEmptyProviders(true);
        customScopeRegistry(OdiCustomScopeRegistry::new);
        beanResolutionCustomizer(new BeanResolutionCustomizer() {
            @Override
            public boolean shouldResolveArrayAsBean(Argument<?> injectionPoint) {
                return true;
            }

            @Override
            public Argument<?> resolveBeanLookupArgument(Argument<?> beanType) {
                Class<?> type = beanType.getType();
                if (type.isPrimitive()) {
                    Class<?> wrapperType = ReflectionUtils.getWrapperType(type);
                    return Argument.of(wrapperType, beanType.getName(), beanType.getAnnotationMetadata(), beanType.getTypeParameters());
                }
                return beanType;
            }

            @Override
            public Optional<?> resolveNullBean(Argument<?> requestedBeanType, Argument<?> resolvedBeanType, BeanDefinition<?> beanDefinition) {
                Class<?> requestedType = requestedBeanType.getType();
                if (requestedType.isPrimitive() && resolvedBeanType.getType() == ReflectionUtils.getWrapperType(requestedType)) {
                    return Optional.of(primitiveDefaultValue(requestedType));
                }
                return Optional.empty();
            }

            @Override
            public boolean shouldDestroyDependentBeanAfterResolution(BeanResolutionContext resolutionContext, BeanRegistration<?> beanRegistration) {
                return resolutionContext.getPath().currentSegment()
                        .map(segment -> segment.getArgument().getAnnotationMetadata().hasAnnotation(TransientReference.class))
                        .orElse(false);
            }

            @Override
            public boolean isCandidateBean(Argument<?> beanType, QualifiedBeanType<?> candidate) {
                Type requiredType = OdiTypeUtils.getRequiredType(beanType);
                if (requiredType != null) {
                    Set<Type> beanTypes = OdiTypeUtils.getBeanTypes(candidate.getAnnotationMetadata(), candidate.getBeanType());
                    if (!beanTypes.isEmpty()) {
                        return OdiTypeUtils.matchesBeanType(requiredType, beanTypes);
                    }
                }
                return candidate.isCandidateBean(beanType);
            }
        });
    }

    private static Object primitiveDefaultValue(Class<?> type) {
        return switch (type.getName()) {
            case "boolean" -> false;
            case "byte" -> (byte) 0;
            case "short" -> (short) 0;
            case "int" -> 0;
            case "long" -> 0L;
            case "float" -> 0.0f;
            case "double" -> 0.0d;
            case "char" -> '\0';
            default -> throw new IllegalArgumentException("Not a primitive type: " + type.getName());
        };
    }
}
