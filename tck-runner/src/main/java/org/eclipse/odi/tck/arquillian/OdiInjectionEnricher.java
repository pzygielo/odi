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
package org.eclipse.odi.tck.arquillian;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanContainer;
import org.eclipse.odi.cdi.OdiBeanContainer;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.test.spi.TestEnricher;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * ODI test enricher.
 */
public class OdiInjectionEnricher implements TestEnricher {

    @Inject
    private Instance<ApplicationContext> runningApplicationContext;

    static void enrich(Object testCase, ApplicationContext applicationContext) {
        Optional<? extends BeanDefinition<?>> beanDefinition = applicationContext.findBeanDefinition(testCase.getClass());
        if (beanDefinition.isPresent()) {
            applicationContext.inject(testCase);
        }
        enrichUsingReflection(testCase, applicationContext);
    }

    static void enrichUsingReflection(Object testCase, ApplicationContext applicationContext) {
        BeanContainer beanContainer = applicationContext.getBean(OdiBeanContainer.class);
        Class<?> testClass = testCase.getClass();
        while (!Object.class.equals(testClass)) {
            for (Field field : testClass.getDeclaredFields()) {
                if (hasInjectAnnotation(field)) {
                    try {
                        field.setAccessible(true);
                        Object value = getContextualReference(field, beanContainer, applicationContext);

                        field.set(testCase, value);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            testClass = testClass.getSuperclass();
        }
    }

    private static Object getContextualReference(Field field,
                                                 BeanContainer beanContainer,
                                                 ApplicationContext applicationContext) {
        Type fieldType = field.getGenericType();
        if (field.getType() == Event.class || field.getType() == jakarta.enterprise.inject.Instance.class) {
            return applicationContext.getBean(Argument.of(fieldType));
        }
        Set<Annotation> qualifiers = new LinkedHashSet<>();
        for (Annotation annotation : field.getAnnotations()) {
            if (beanContainer.isQualifier(annotation.annotationType())) {
                qualifiers.add(annotation);
            }
        }
        Set<Bean<?>> beans = beanContainer.getBeans(fieldType, qualifiers.toArray(Annotation[]::new));
        Bean<?> bean = beanContainer.resolve(beans);
        if (bean == null) {
            return applicationContext.getBean(Argument.of(fieldType));
        }
        CreationalContext<?> creationalContext = beanContainer.createCreationalContext((Contextual<?>) bean);
        return beanContainer.getReference(bean, fieldType, creationalContext);
    }

    @Override
    public void enrich(Object testCase) {
        // not needed, we do that manually in OdiDeployableContainer.deploy,
        // and Arquillian won't invoke this anyway (because we don't use the "local" protocol)
    }

    @Override
    public Object[] resolve(Method method) {
        ApplicationContext applicationContext = runningApplicationContext.get();
        try {
            ClassLoader classLoader = applicationContext.getClassLoader();
            Class<?> declaringClass = classLoader.loadClass(method.getDeclaringClass().getName());
            Class[] params = Arrays.stream(method.getParameterTypes())
                    .map(clazz -> ClassUtils.forName(clazz.getName(), classLoader).get())
                    .toArray(Class[]::new);
            Optional<? extends ExecutableMethod<?, Object>> optionalExecutableMethod = applicationContext.findExecutableMethod(declaringClass, method.getName(), params);

            if (optionalExecutableMethod.isPresent()) {
                boolean hasNonArquillianDataProvider = false;
                for (Annotation annotation : method.getAnnotations()) {
                    if (annotation.annotationType().getName().equals("org.testng.annotations.Test")) {
                        try {
                            Method dataProviderMember = annotation.annotationType().getDeclaredMethod("dataProvider");
                            String value = dataProviderMember.invoke(annotation).toString();
                            hasNonArquillianDataProvider = !value.equals("") && !value.equals("ARQUILLIAN_DATA_PROVIDER");
                            break;
                        } catch (ReflectiveOperationException ignored) {
                        }
                    }
                }
                if (hasNonArquillianDataProvider) {
                    return new Object[params.length];
                }

                OdiBeanContainer beanContainer = applicationContext.getBean(OdiBeanContainer.class);
                return Arrays.stream(optionalExecutableMethod.get().getArguments())
                        .map(argument -> resolveArgument(argument, beanContainer, applicationContext))
                        .toArray();
            } else {
                return resolveUsingReflection(method);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object resolveArgument(Argument<?> argument,
                                          OdiBeanContainer beanContainer,
                                          ApplicationContext applicationContext) {
        if (argument.getType() == Event.class || argument.getType() == jakarta.enterprise.inject.Instance.class) {
            return applicationContext.getBean((Argument) argument);
        }
        Qualifier<?> qualifier = Qualifiers.forArgument(argument);
        Bean<?> bean = beanContainer.getBean((Argument) argument, (Qualifier) qualifier);
        CreationalContext<?> creationalContext = beanContainer.createCreationalContext((Contextual<?>) bean);
        return beanContainer.getReference(bean, argument.asType(), creationalContext);
    }

    private static boolean hasInjectAnnotation(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            if ("jakarta.inject.Inject".equals(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    private Object[] resolveUsingReflection(Method method) throws Exception {
        ApplicationContext applicationContext = runningApplicationContext.get();
        ClassLoader classLoader = applicationContext.getClassLoader();
        Class<?> declaringClass = classLoader.loadClass(method.getDeclaringClass().getName());
        Class[] params = Arrays.stream(method.getParameterTypes())
                .map(clazz -> ClassUtils.forName(clazz.getName(), classLoader).get())
                .toArray(Class[]::new);
        method = declaringClass.getMethod(method.getName(), params);

        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] result = new Object[parameterTypes.length];

        boolean hasNonArquillianDataProvider = false;
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation.annotationType().getName().equals("org.testng.annotations.Test")) {
                try {
                    Method dataProviderMember = annotation.annotationType().getDeclaredMethod("dataProvider");
                    String value = dataProviderMember.invoke(annotation).toString();
                    hasNonArquillianDataProvider = !value.equals("") && !value.equals("ARQUILLIAN_DATA_PROVIDER");
                    break;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        if (hasNonArquillianDataProvider) {
            return result;
        }

        Type[] genericParameterTypes = method.getGenericParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            // TODO qualifiers?
            result[i] = applicationContext.getBean(Argument.of(genericParameterTypes[i]));
        }
        return result;
    }
}
