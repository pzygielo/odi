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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourcesLocator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.QualifiedBeanType;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.Extension;

/**
 * An implementation of {@link SeContainerInitializer} for ODI.
 */
public class OdiSeContainerInitializer extends SeContainerInitializer implements ApplicationContextBuilder {
    private static final String ADDED_BEAN_CLASSES_PROPERTY = "org.eclipse.odi.cdi.se.added-bean-classes";

    private final ApplicationContextBuilder contextBuilder = new OdiApplicationContextBuilder();
    private final List<String> addedBeanClassNames = new ArrayList<>();

    @Override
    public SeContainerInitializer addBeanClasses(Class<?>... classes) {
        if (ArrayUtils.isNotEmpty(classes)) {
            ClassLoader classLoader = null;
            for (Class<?> aClass : classes) {
                if (!addedBeanClassNames.contains(aClass.getName())) {
                    addedBeanClassNames.add(aClass.getName());
                }
                contextBuilder.packages(aClass.getPackageName());
                if (classLoader == null) {
                    classLoader = aClass.getClassLoader();
                }
            }
            if (classLoader != null) {
                contextBuilder.classLoader(classLoader);
            }
            contextBuilder.properties(Collections.singletonMap(ADDED_BEAN_CLASSES_PROPERTY, String.join(",", addedBeanClassNames)));
        }
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(Class<?>... classes) {
        if (ArrayUtils.isNotEmpty(classes)) {
            for (Class<?> aClass : classes) {
                contextBuilder.packages(aClass.getPackageName());
            }
        }
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(boolean b, Class<?>... classes) {
        // TODO: Support scan recursively?
        return addPackages(classes);
    }

    @Override
    public SeContainerInitializer addPackages(Package... packages) {
        if (ArrayUtils.isNotEmpty(packages)) {
            for (Package aPackage : packages) {
                contextBuilder.packages(aPackage.getName());
            }
        }
        return this;
    }

    @Override
    public SeContainerInitializer addPackages(boolean b, Package... packages) {
        // TODO: Support scan recursively?
        return addPackages(packages);
    }

    @Override
    public SeContainerInitializer addExtensions(Extension... extensions) {
        throw new UnsupportedOperationException("addExtensions is not yet supported");
    }

    @SafeVarargs
    @Override
    public final SeContainerInitializer addExtensions(Class<? extends Extension>... classes) {
        throw new UnsupportedOperationException("addExtensions is not yet supported");
    }

    @Override
    public SeContainerInitializer enableInterceptors(Class<?>... classes) {
        throw new UnsupportedOperationException("enableInterceptors is not yet supported");
    }

    @Override
    public SeContainerInitializer enableDecorators(Class<?>... classes) {
        throw new UnsupportedOperationException("enableDecorators is not yet supported");
    }

    @Override
    public SeContainerInitializer selectAlternatives(Class<?>... classes) {
        throw new UnsupportedOperationException("selectAlternatives is not yet supported");
    }

    @SafeVarargs
    @Override
    public final SeContainerInitializer selectAlternativeStereotypes(Class<? extends Annotation>... classes) {
        throw new UnsupportedOperationException("selectAlternativeStereotypes is not yet supported");
    }

    @Override
    public SeContainerInitializer addProperty(String s, Object o) {
        contextBuilder.properties(Collections.singletonMap(s, o));
        return this;
    }

    @Override
    public SeContainerInitializer setProperties(Map<String, Object> map) {
        contextBuilder.properties(map);
        return this;
    }

    @Override
    public SeContainerInitializer disableDiscovery() {
        return this;
    }

    @Override
    public SeContainerInitializer setClassLoader(ClassLoader classLoader) {
        contextBuilder.classLoader(classLoader);
        return this;
    }

    @Override
    public SeContainer initialize() {
        final ApplicationContext context = contextBuilder.build();
        context.start();
        return new OdiSeContainer(context);
    }

    @SafeVarargs
    @Override
    @NonNull
    public final ApplicationContextBuilder eagerInitAnnotated(Class<? extends Annotation>... annotations) {
        contextBuilder.eagerInitAnnotated(annotations);
        return this;
    }

    @Override
    @NonNull
    public ApplicationContextBuilder overrideConfigLocations(String... configLocations) {
        contextBuilder.overrideConfigLocations(configLocations);
        return this;
    }

    @Override
    @NonNull
    public ApplicationContextBuilder singletons(Object... beans) {
        contextBuilder.singletons(beans);
        return this;
    }

    @Override
    @NonNull
    public ApplicationContextBuilder deduceEnvironment(Boolean deduceEnvironment) {
        contextBuilder.deduceEnvironment(deduceEnvironment);
        return this;
    }

    @Override
    public ApplicationContextBuilder deducePackage(boolean deducePackage) {
        return contextBuilder.deducePackage(deducePackage);
    }

    @Override
    public ApplicationContextBuilder deduceCloudEnvironment(boolean deduceEnvironment) {
        return contextBuilder.deduceCloudEnvironment(deduceEnvironment);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder environments(String... environments) {
        contextBuilder.environments(environments);
        return this;
    }

    @Override
    @NonNull
    public ApplicationContextBuilder defaultEnvironments(String... environments) {
        contextBuilder.defaultEnvironments(environments);
        return this;
    }

    @Override
    @NonNull
    public ApplicationContextBuilder packages(String... packages) {
        return contextBuilder.packages(packages);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder properties(Map<String, Object> properties) {
        return contextBuilder.properties(properties);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder propertySources(PropertySource... propertySources) {
        return contextBuilder.propertySources(propertySources);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder environmentPropertySource(boolean environmentPropertySource) {
        return contextBuilder.environmentPropertySource(environmentPropertySource);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder environmentVariableIncludes(String... environmentVariables) {
        return contextBuilder.environmentVariableIncludes(environmentVariables);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder environmentVariableExcludes(String... environmentVariables) {
        return contextBuilder.environmentVariableExcludes(environmentVariables);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder mainClass(Class mainClass) {
        return contextBuilder.mainClass(mainClass);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder classLoader(ClassLoader classLoader) {
        return contextBuilder.classLoader(classLoader);
    }

    @Override
    @NonNull
    public ApplicationContext build() {
        return contextBuilder.build();
    }

    @Override
    @NonNull
    public ApplicationContextBuilder include(String... configurations) {
        return contextBuilder.include(configurations);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder exclude(String... configurations) {
        return contextBuilder.exclude(configurations);
    }

    @Override
    @NonNull
    public ApplicationContextBuilder banner(boolean isEnabled) {
        return contextBuilder.banner(isEnabled);
    }

    @Override
    public ApplicationContextBuilder allowEmptyProviders(boolean shouldAllow) {
        return contextBuilder.allowEmptyProviders(shouldAllow);
    }

    @Override
    public ApplicationContextBuilder configImport(boolean enabled) {
        return contextBuilder.configImport(enabled);
    }

    @Override
    public ApplicationContextBuilder eagerBeansEnabled(boolean enabled) {
        return contextBuilder.eagerBeansEnabled(enabled);
    }

    @Override
    public ApplicationContextBuilder eventsEnabled(boolean enabled) {
        return contextBuilder.eventsEnabled(enabled);
    }

    @Override
    public ApplicationContextBuilder beansPredicate(Predicate<QualifiedBeanType<?>> predicate) {
        return contextBuilder.beansPredicate(predicate);
    }

    @Override
    public ApplicationContextBuilder beanConfigurationsPredicate(Predicate<BeanConfiguration> predicate) {
        return contextBuilder.beanConfigurationsPredicate(predicate);
    }

    @Override
    public ApplicationContextBuilder resourceResolver(ClassPathResourceLoader resourceResolver) {
        return contextBuilder.resourceResolver(resourceResolver);
    }

    @Override
    public ApplicationContextBuilder propertySourcesLocator(PropertySourcesLocator propertySourcesLocator) {
        return contextBuilder.propertySourcesLocator(propertySourcesLocator);
    }

}
