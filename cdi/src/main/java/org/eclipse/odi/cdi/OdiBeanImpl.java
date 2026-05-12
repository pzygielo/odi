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

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;
import io.micronaut.inject.ProxyBeanDefinition;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.IllegalProductException;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.Prioritized;
import jakarta.inject.Named;
import org.eclipse.odi.cdi.annotation.NamedByStereotype;
import org.eclipse.odi.cdi.annotation.OdiBeanType;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the {@link jakarta.enterprise.inject.spi.Bean} interface for ODI.
 *
 * @param <T> The generic type of the bean
 */
@Internal
public class OdiBeanImpl<T> implements OdiBean<T>, Prioritized {

    private final BeanDefinition<T> definition;
    private final BeanContext beanContext;
    private Class<? extends Annotation> scope;

    /**
     * Default constructor.
     *
     * @param beanContext The bean context
     * @param definition  The definition
     */
    public OdiBeanImpl(BeanContext beanContext, BeanDefinition<T> definition) {
        this.beanContext = beanContext;
        this.definition = Objects.requireNonNull(definition, "Bean definition cannot be null");
    }

    @Override
    public boolean isProxy() {
        return definition.isProxy();
    }

    @Override
    public BeanDefinition<T> getBeanDefinition() {
        return definition;
    }

    @Override
    public OdiBean<T> getProxyTargetBean() {
        BeanDefinition<T> targetBeanDefinition = beanContext.getProxyTargetBeanDefinition(
                ((ProxyBeanDefinition) definition).getTargetType(),
                definition.getDeclaredQualifier()
        );
        return new OdiBeanImpl<>(beanContext, targetBeanDefinition);
    }

    @Override
    public Class<?> getBeanClass() {
        return definition.getDeclaringType().orElseGet(() -> {
            if (definition instanceof AdvisedBeanType) {
                return ((AdvisedBeanType<?>) definition).getInterceptedType();
            }
            return definition.getBeanType();
        });
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        @SuppressWarnings("rawtypes")
        Stream<? extends io.micronaut.inject.InjectionPoint> injectionPoints =
                Stream.concat(definition.getInjectedFields().stream(), definition.getInjectedMethods().stream());
        injectionPoints = Stream.concat(
                injectionPoints,
                Stream.of(definition.getConstructor())
        );
        return injectionPoints.flatMap((ip) -> {
            if (ip instanceof FieldInjectionPoint) {
                return Stream.of(new OdiInjectionPoint(beanContext.getClassLoader(), this, ip, ((FieldInjectionPoint<?, ?>) ip).asArgument()));
            } else if (ip instanceof MethodInjectionPoint) {
                MethodInjectionPoint<?, ?> mip = (MethodInjectionPoint) ip;
                return Stream.of(mip.getArguments()).map((arg) -> new OdiInjectionPoint(beanContext.getClassLoader(), this, mip, arg));
            } else if (ip instanceof ConstructorInjectionPoint) {
                ConstructorInjectionPoint<?> cip = (ConstructorInjectionPoint) ip;
                return Stream.of(cip.getArguments()).map((arg) -> new OdiInjectionPoint(beanContext.getClassLoader(), this, cip, arg));
            }
            return Stream.empty();
        }).collect(Collectors.toSet());
    }

    @Override
    public T create(CreationalContext<T> creationalContext) {
        BeanDefinition<T> creationDefinition = getCreationDefinition();
        try {
            BeanRegistration<T> beanRegistration = beanContext.getBeanRegistration(creationDefinition);
            if (creationalContext != null) {
                creationalContext.push(beanRegistration.bean());
                if (creationalContext instanceof OdiCreationalContext) {
                    OdiCreationalContext<T> odiCreationalContext = (OdiCreationalContext<T>) creationalContext;
                    odiCreationalContext.setCreatedBean(beanRegistration);
                }
            }
            return beanRegistration.getBean();
        } catch (DependencyInjectionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NonUniqueBeanException) {
                throw new AmbiguousResolutionException(e.getMessage(), e);
            } else if (cause instanceof NoSuchBeanException) {
                throw new UnsatisfiedResolutionException(e.getMessage(), e);
            } else {
                throw new CreationException(e.getMessage(), e);
            }
        } catch (NonUniqueBeanException e) {
            throw new AmbiguousResolutionException(e.getMessage(), e);
        } catch (NoSuchBeanException e) {
            throw new UnsatisfiedResolutionException(e.getMessage(), e);
        } catch (BeanInstantiationException e) {
            if (isNullProducerResult(creationDefinition, e) || isNullProducerResult(definition, e)) {
                if (getScope() == Dependent.class) {
                    return null;
                }
                throw new IllegalProductException(e.getMessage(), e);
            }
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else {
                throw new CreationException(e.getMessage(), e);
            }
        } catch (CreationException e) {
            if (isNullProducerResult(creationDefinition, e) || isNullProducerResult(definition, e)) {
                throw new IllegalProductException(e.getMessage(), e);
            }
            throw e;
        } catch (Throwable e) {
            if (e instanceof RuntimeException) {
                throw e;
            } else {
                throw new CreationException(e.getMessage(), e);
            }
        }
    }

    private BeanDefinition<T> getCreationDefinition() {
        if (definition instanceof ProxyBeanDefinition && definition.hasAnnotation(Produces.class)) {
            return beanContext.getProxyTargetBeanDefinition(
                    ((ProxyBeanDefinition<T>) definition).getTargetType(),
                    definition.getDeclaredQualifier()
            );
        }
        return definition;
    }

    static boolean isNullProducerResult(BeanDefinition<?> definition, Throwable exception) {
        if (!definition.hasAnnotation(Produces.class)) {
            return false;
        }
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (current instanceof BeanInstantiationException && message != null && message.contains(" returned null")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Override
    public void destroy(T instance, CreationalContext<T> creationalContext) {
        if (!(creationalContext instanceof OdiCreationalContext)) {
            throw new IllegalArgumentException("Not an ODI Creational Context");
        }
        creationalContext.release();
    }

    @Override
    public Set<Type> getTypes() {
        Set<Type> types = new LinkedHashSet<>();
        if (definition.getBeanType().isArray()) {
            types.add(definition.getBeanType());
            types.add(Object.class);
            return types;
        }
        Set<Type> metadataTypes = getMetadataBeanTypes();
        Set<Class<?>> exposedTypes = definition.getExposedTypes();
        if (!metadataTypes.isEmpty()) {
            if (!exposedTypes.isEmpty() && shouldRestrictMetadataTypes(metadataTypes, exposedTypes)) {
                return restrictMetadataTypes(metadataTypes, exposedTypes);
            }
            return metadataTypes;
        }
        if (!exposedTypes.isEmpty()) {
            types.addAll(exposedTypes);
            types.add(Object.class);
            return types;
        }
        if (definition instanceof AdvisedBeanType) {
            collectBeanTypes(definition, ((AdvisedBeanType<?>) definition).getInterceptedType(), types);
        } else {
            collectBeanTypes(definition, definition.getBeanType(), types);
        }
        types.add(Object.class);
        return types;
    }

    private static boolean shouldRestrictMetadataTypes(Set<Type> metadataTypes, Set<Class<?>> exposedTypes) {
        Set<Class<?>> rawMetadataTypes = metadataTypes.stream()
                .map(OdiBeanImpl::rawType)
                .filter(Objects::nonNull)
                .filter(type -> type != Object.class)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return rawMetadataTypes.containsAll(exposedTypes) && !exposedTypes.containsAll(rawMetadataTypes);
    }

    private static Set<Type> restrictMetadataTypes(Set<Type> metadataTypes, Set<Class<?>> exposedTypes) {
        Set<Type> types = new LinkedHashSet<>();
        for (Type metadataType : metadataTypes) {
            Class<?> rawType = rawType(metadataType);
            if (rawType == Object.class || exposedTypes.contains(rawType)) {
                types.add(metadataType);
            }
        }
        types.add(Object.class);
        return types;
    }

    private static Class<?> rawType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class<?>) {
                return (Class<?>) rawType;
            }
        }
        return null;
    }

    private Set<Type> getMetadataBeanTypes() {
        List<AnnotationValue<OdiBeanType>> beanTypes = definition.getAnnotationMetadata().getAnnotationValuesByType(OdiBeanType.class);
        if (beanTypes.isEmpty()) {
            return Set.of();
        }
        Set<Type> types = new LinkedHashSet<>();
        for (AnnotationValue<OdiBeanType> beanType : beanTypes) {
            Class<?> rawType = beanType.classValue().orElse(null);
            if (rawType == null || rawType == Object.class) {
                continue;
            }
            Class<?>[] arguments = beanType.classValues("arguments");
            int[] argumentCounts = beanType.intValues("argumentCounts");
            types.add(toBeanType(rawType, arguments, argumentCounts));
        }
        types.add(Object.class);
        return types;
    }

    private static void collectBeanTypes(BeanDefinition<?> definition, Class<?> type, Set<Type> types) {
        if (type == null || type == Object.class) {
            return;
        }
        types.add(toBeanType(definition, type));
        if (type.isArray()) {
            return;
        }
        for (Class<?> interfaceType : type.getInterfaces()) {
            collectBeanTypes(definition, interfaceType, types);
        }
        collectBeanTypes(definition, type.getSuperclass(), types);
    }

    private static Type toBeanType(BeanDefinition<?> definition, Class<?> type) {
        List<Argument<?>> typeArguments = definition.getTypeArguments(type);
        if (typeArguments.isEmpty()) {
            return type;
        }
        return Argument.of(type, typeArguments.toArray(Argument.ZERO_ARGUMENTS)).asType();
    }

    private static Type toBeanType(Class<?> rawType, Class<?>[] argumentTypes) {
        return toBeanType(rawType, argumentTypes, new int[0]);
    }

    private static Type toBeanType(Class<?> rawType, Class<?>[] argumentTypes, int[] argumentCounts) {
        if (argumentTypes.length == 0) {
            return rawType;
        }
        if (argumentCounts.length == argumentTypes.length) {
            TypeArgumentReader reader = new TypeArgumentReader(argumentTypes, argumentCounts);
            return Argument.of(rawType, reader.readAll()).asType();
        }
        Argument<?>[] arguments = new Argument<?>[argumentTypes.length];
        for (int i = 0; i < argumentTypes.length; i++) {
            arguments[i] = Argument.of(argumentTypes[i]);
        }
        return Argument.of(rawType, arguments).asType();
    }

    private static final class TypeArgumentReader {
        private final Class<?>[] argumentTypes;
        private final int[] argumentCounts;
        private int index;

        private TypeArgumentReader(Class<?>[] argumentTypes, int[] argumentCounts) {
            this.argumentTypes = argumentTypes;
            this.argumentCounts = argumentCounts;
        }

        private Argument<?>[] readAll() {
            List<Argument<?>> arguments = new ArrayList<>(argumentTypes.length);
            while (index < argumentTypes.length) {
                arguments.add(read());
            }
            return arguments.toArray(Argument.ZERO_ARGUMENTS);
        }

        private Argument<?> read() {
            Class<?> type = argumentTypes[index];
            int childCount = argumentCounts[index];
            index++;
            if (childCount == 0) {
                return Argument.of(type);
            }
            Argument<?>[] children = new Argument<?>[childCount];
            for (int i = 0; i < childCount; i++) {
                children[i] = read();
            }
            return Argument.of(type, children);
        }
    }

    @Override
    public Set<Annotation> getQualifiers() {
        Set<Annotation> annotations = AnnotationUtils.synthesizeQualifierAnnotations(definition.getAnnotationMetadata(), beanContext.getClassLoader());
        if (isNamedByStereotype()) {
            annotations.removeIf(Named.class::isInstance);
        }
        Set<Annotation> all = new HashSet<>(annotations);
        all.add(Any.Literal.INSTANCE);
        if (all.size() == 1 || all.stream().allMatch(e -> e instanceof Named || e instanceof Any)) {
            all.add(Default.Literal.INSTANCE);
        }
        return all;
    }

    private boolean isNamedByStereotype() {
        return definition.hasAnnotation(NamedByStereotype.class);
    }

    @Override
    public Class<? extends Annotation> getScope() {
        if (this.scope == null) {
            final AnnotationMetadata annotationMetadata = definition.getAnnotationMetadata();
            this.scope = MetaAnnotationSupport.resolveDeclaredScope(annotationMetadata);
        }
        return this.scope;
    }

    @Override
    public String getName() {
        return definition.getAnnotationMetadata().stringValue(AnnotationUtil.NAMED).orElse(null);
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        final AnnotationMetadata annotationMetadata = definition.getAnnotationMetadata();
        return new HashSet<>(annotationMetadata.getAnnotationTypesByStereotype(Stereotype.class));
    }

    @Override
    public boolean isAlternative() {
        return definition.hasAnnotation(Alternative.class) || definition.hasStereotype(Alternative.class);
    }

    @Override
    public int getPriority() {
        int priority = definition.intValue(Priority.class).orElse(0);
        if (priority != 0) {
            return priority;
        }
        final int i = definition.intValue(Order.class).orElse(0);
        if (i != 0) {
            return -i;
        }
        return 0;
    }

    @Override
    public int hashCode() {
        Argument<?> argument = asArgument();
        final io.micronaut.context.Qualifier<T> qualifier = definition.getDeclaredQualifier();
        return Objects.hash(argument, qualifier);
    }

    private Argument<?> asArgument() {
        Argument<?> argument = definition.asArgument();
        if (definition instanceof AdvisedBeanType) {
            final Class<?> interceptedType = ((AdvisedBeanType<?>) definition).getInterceptedType();
            argument = Argument.of(interceptedType, argument.getTypeParameters());
        }
        return argument;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OdiBeanImpl)) {
            return false;
        }
        OdiBeanImpl<?> odiBean = (OdiBeanImpl<?>) o;
        Argument<?> argument = asArgument();
        return Objects.equals(argument, ((OdiBeanImpl<?>) o).asArgument())
                && Objects.equals(definition.getDeclaredQualifier(), odiBean.definition.getDeclaredQualifier());
    }

}
