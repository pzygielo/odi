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
package org.eclipse.odi.cdi.processor.extensions;

import io.micronaut.context.annotation.Executable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.invoke.InvokerBuilder;
import org.eclipse.odi.cdi.OdiExecutableInvokerInfo;

import java.util.Set;

final class InvokerFactoryImpl implements InvokerFactory {
    private final BuildTimeExtensionRegistry registry;

    InvokerFactoryImpl(BuildTimeExtensionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public InvokerBuilder<InvokerInfo> createInvoker(BeanInfo bean, MethodInfo method) {
        if (!(bean instanceof BeanInfoImpl)) {
            throw new IllegalArgumentException("Unsupported bean info implementation: " + bean);
        }
        if (!(method instanceof MethodInfoImpl)) {
            throw new IllegalArgumentException("Unsupported method info implementation: " + method);
        }
        validate(bean, method);

        BeanInfoImpl beanInfo = (BeanInfoImpl) bean;
        MethodInfoImpl methodInfo = (MethodInfoImpl) method;
        MethodElement methodElement = methodInfo.getElement();
        methodElement.annotate(Executable.class);

        ParameterElement[] parameters = methodElement.getParameters();
        String[] parameterTypeNames = new String[parameters.length];
        int[] parameterArrayDimensions = new int[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            parameterTypeNames[i] = parameters[i].getType().getName();
            parameterArrayDimensions[i] = parameters[i].getType().getArrayDimensions();
        }
        OdiExecutableInvokerInfo invokerInfo = new OdiExecutableInvokerInfo(
                beanInfo.declaringClass().name(),
                methodInfo.declaringClass().name(),
                methodInfo.name(),
                parameterTypeNames,
                parameterArrayDimensions,
                methodInfo.isStatic()
        );
        registry.registerInvoker(invokerInfo, methodElement);
        return invokerInfo;
    }

    private void validate(BeanInfo bean, MethodInfo method) {
        if (bean.isInterceptor()) {
            throw new DeploymentException("Cannot create an invoker for an interceptor bean");
        }
        if (bean.isProducerField() || bean.isProducerMethod()) {
            throw new DeploymentException("Cannot create an invoker for a producer bean");
        }
        if (method.isConstructor()) {
            throw new DeploymentException("Cannot create an invoker for a constructor");
        }
        MethodElement methodElement = ((MethodInfoImpl) method).getElement();
        Set<ElementModifier> modifiers = methodElement.getModifiers();
        if (modifiers.contains(ElementModifier.PRIVATE)) {
            throw new DeploymentException("Cannot create an invoker for a private method");
        }
        if (method.declaringClass().name().equals(Object.class.getName()) && !"toString".equals(method.name())) {
            throw new DeploymentException("Cannot create an invoker for Object method: " + method.name());
        }
        ClassElement beanClass = ((BeanInfoImpl) bean).getClassInfo().getElement();
        ClassElement methodDeclaringClass = methodElement.getDeclaringType();
        if (!beanClass.isAssignable(methodDeclaringClass)) {
            throw new DeploymentException("Invoker method is not declared by the bean class hierarchy");
        }
    }
}
