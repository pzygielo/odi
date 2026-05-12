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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanResolutionContext;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.inject.spi.Prioritized;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.odi.cdi.annotation.reflect.AnnotationReflection;
import org.eclipse.odi.cdi.context.DependentContext;
import org.eclipse.odi.cdi.context.SingletonContext;
import org.eclipse.odi.cdi.events.OdiObserverMethodRegistry;

import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class OdiBeanContainerImpl implements OdiBeanContainer {

    private final ApplicationContext applicationContext;
    private final OdiSeContainer container;

    private final OdiAnnotations odiAnnotations;
    private OdiObserverMethodRegistry observerMethodRegistry;
    private Event<Object> objectEvent;

    OdiBeanContainerImpl(OdiSeContainer container, OdiAnnotations odiAnnotations, ApplicationContext applicationContext) {
        this.container = container;
        this.odiAnnotations = odiAnnotations;
        this.applicationContext = applicationContext;
    }

    @Override
    public OdiAnnotations getOdiAnnotations() {
        return odiAnnotations;
    }

    @Override
    public <B, R> Object fulfillAndExecuteMethod(BeanDefinition<B> beanDefinition,
                                                 ExecutableMethod<B, R> executableMethod,
                                                 Function<Argument<?>, Object> valueSupplier) {
        return fulfillAndExecuteMethod(beanDefinition, executableMethod, valueSupplier, false);
    }

    @Override
    public <B, R> Object fulfillAndExecuteMethod(BeanDefinition<B> beanDefinition,
                                                 ExecutableMethod<B, R> executableMethod,
                                                 Function<Argument<?>, Object> valueSupplier,
                                                 boolean staticMethod) {
        Argument<?>[] arguments = executableMethod.getArguments();
        Object[] values = new Object[arguments.length];
        try (BeanResolutionContext resolutionContext = new DefaultBeanResolutionContext(getBeanContext(), beanDefinition)) {
            DependentContext dependentContext = new DependentContext(resolutionContext);
            for (int i = 0; i < arguments.length; i++) {
                Argument<?> argument = arguments[i];
                Object value = valueSupplier.apply(argument);
                if (value != null) {
                    values[i] = value;
                } else {
                    try (BeanResolutionContext.Path ignore = resolutionContext.getPath().pushMethodArgumentResolve(
                            beanDefinition,
                            executableMethod.getMethodName(),
                            argument,
                            arguments
                    )) {
                        if (argument.getType() == Instance.class) {
                            Instance<?> instance = createInstance(dependentContext).select(argument.getFirstTypeVariable()
                                    .orElseThrow(() -> new IllegalArgumentException("Expected the type of Instance!")));
                            values[i] = instance;
                        } else {
                            Instance<?> instance = createInstance(dependentContext).select(argument);
                            values[i] = instance.get();
                        }
                    }
                }
            }
            B beanInstance = null;
            if (!staticMethod) {
                OdiBean<B> bean = getBean(beanDefinition);
                CreationalContext<B> creationalContext = createCreationalContext(bean);
                Context beanContext = odiAnnotations.isDependent(bean.getScope()) ? dependentContext : getContext(bean.getScope());
                beanInstance = beanContext.get(bean, creationalContext);
            }
            Object result = executableMethod.invoke(beanInstance, values);
            dependentContext.destroy();
            return result;
        }
    }

    @Override
    public <T> OdiBeanImpl<T> getBean(BeanDefinition<T> beanDefinition) {
        return new OdiBeanImpl<>(applicationContext, beanDefinition);
    }

    @Override
    public <T> OdiBeanImpl<T> getBean(Argument<T> argument, io.micronaut.context.Qualifier<T> qualifier) {
        Collection<BeanDefinition<T>> beanDefinitions = resolveBeanDefinitions(getBeanDefinitions(argument, qualifier));
        if (beanDefinitions.isEmpty()) {
            throw new UnsatisfiedResolutionException("No bean found for argument: " + argument + " and qualifier: " + qualifier);
        }
        if (beanDefinitions.size() > 1) {
            throw new AmbiguousResolutionException("Multiple beans found for argument: " + argument + " and qualifier: " + qualifier);
        }
        return new OdiBeanImpl<>(applicationContext, beanDefinitions.iterator().next());
    }

    @Override
    public <T> Collection<OdiBean<T>> getBeans(Argument<T> argument, io.micronaut.context.Qualifier<T> qualifier) {
        return getBeanDefinitions(argument, qualifier).stream()
                .map(bd -> new OdiBeanImpl<>(applicationContext, bd))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public <T> Collection<BeanDefinition<T>> getBeanDefinitions(Argument<T> argument, io.micronaut.context.Qualifier<T> qualifier) {
        if (qualifier == null) {
            qualifier = DefaultQualifier.instance();
        }
        Collection<BeanDefinition<T>> beanDefinitions = applicationContext.getBeanDefinitions(argument, qualifier);
        if (qualifier instanceof DefaultQualifier) {
            beanDefinitions = beanDefinitions.stream()
                    .filter(DefaultQualifier::hasDefaultQualifier)
                    .collect(Collectors.toList());
        }
        Class<?> primitiveType = toPrimitiveType(argument.getType());
        if (beanDefinitions.isEmpty() && primitiveType != argument.getType()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Collection<BeanDefinition<T>> primitiveBeanDefinitions = (Collection) applicationContext.getBeanDefinitions(
                    (Argument) Argument.of(primitiveType),
                    (io.micronaut.context.Qualifier) qualifier
            );
            if (qualifier instanceof DefaultQualifier) {
                primitiveBeanDefinitions = primitiveBeanDefinitions.stream()
                        .filter(DefaultQualifier::hasDefaultQualifier)
                        .collect(Collectors.toList());
            }
            return primitiveBeanDefinitions;
        }
        return beanDefinitions;
    }

    private <T> Collection<BeanDefinition<T>> resolveBeanDefinitions(Collection<BeanDefinition<T>> beanDefinitions) {
        if (beanDefinitions.isEmpty() || beanDefinitions.size() == 1) {
            return beanDefinitions;
        }
        List<BeanDefinition<T>> alternatives = beanDefinitions
                .stream()
                .filter(bd -> bd.hasStereotype(Alternative.class))
                .filter(bd -> getPriority(bd) > 0)
                .collect(Collectors.toList());
        if (!alternatives.isEmpty()) {
            return alternatives.stream()
                    .sorted(Comparator.<BeanDefinition<T>>comparingInt(OdiBeanContainerImpl::getPriority).reversed())
                    .limit(1)
                    .collect(Collectors.toList());
        }
        return beanDefinitions;
    }

    private static int getPriority(BeanDefinition<?> beanDefinition) {
        OptionalInt priority = beanDefinition.intValue(Priority.class);
        if (priority.isPresent()) {
            return priority.getAsInt();
        }
        int order = beanDefinition.intValue(Order.class).orElse(0);
        return order == 0 ? 0 : -order;
    }

    @Override
    public Object getReference(Bean<?> bean, Type beanType, CreationalContext<?> ctx) {
        if (bean instanceof OdiBean) {
            if (beanType instanceof ParameterizedType) {
                beanType = ((ParameterizedType) beanType).getRawType();
            }
            if (!(beanType instanceof Class)) {
                throw new IllegalStateException("Not implemented");
            }
            OdiBean<Object> odiBean = (OdiBean<Object>) bean;
            CreationalContext creationalContext = ctx;
            if (creationalContext == null) {
                creationalContext = createCreationalContext(odiBean);
            }
            Class<? extends Annotation> scope = odiBean.getScope();
            Object instance;
            if (odiAnnotations.isDependent(scope)) {
                instance = odiBean.create(creationalContext);
            } else if (odiBean.isProxy()) {
                BeanRegistration<Object> beanRegistration = getBeanContext().getBeanRegistration(odiBean.getBeanDefinition());
                instance = beanRegistration.getBean();
                if (creationalContext instanceof OdiCreationalContext) {
                    OdiCreationalContext<Object> odiCreationalContext = (OdiCreationalContext<Object>) creationalContext;
                    odiCreationalContext.push(instance);
                    odiCreationalContext.setCreatedBean(beanRegistration);
                }
            } else {
                Context context = getContext(scope);
                instance = context.get(odiBean, creationalContext);
            }
            if (instance == null) {
                return null;
            }
            if (!((Class<?>) beanType).isInstance(instance)) {
                throw new IllegalArgumentException("Invalid instance!");
            }
            return instance;
        } else {
            throw new IllegalArgumentException("Unsupported by bean type: " + bean.getClass());
        }
    }

    @Override
    public <T> CreationalContext<T> createCreationalContext(Contextual<T> contextual) {
        return new OdiCreationalContext<>(getBeanContext(), contextual);
    }

    @Override
    public Set<Bean<?>> getBeans(Type beanType, Annotation... qualifiers) {
        return getBeans(Argument.of(beanType), odiAnnotations.resolveQualifier(qualifiers)).stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<Bean<?>> getBeans(String name) {
        String beanName = Objects.requireNonNull(name, "Name cannot be null");
        return getBeans(Argument.OBJECT_ARGUMENT, Qualifiers.byName(beanName)).stream()
                .filter(bean -> beanName.equals(bean.getName()))
                .map(bean -> (Bean<?>) bean)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public <X> Bean<? extends X> resolve(Set<Bean<? extends X>> beans) {
        if (beans == null || beans.isEmpty()) {
            return null;
        }
        if (beans.size() == 1) {
            return beans.iterator().next();
        }
        List<Bean<? extends X>> alternatives = beans.stream()
                .filter(Bean::isAlternative)
                .filter(bean -> getPriority(bean) > 0)
                .collect(Collectors.toList());
        if (!alternatives.isEmpty()) {
            return alternatives.stream()
                    .sorted(Comparator.<Bean<? extends X>>comparingInt(OdiBeanContainerImpl::getPriority).reversed())
                    .findFirst()
                    .orElse(null);
        }
        throw new AmbiguousResolutionException("Multiple beans are eligible for injection: " + beans);
    }

    private static int getPriority(Bean<?> bean) {
        if (bean instanceof Prioritized) {
            return ((Prioritized) bean).getPriority();
        }
        return 0;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public <T> Set<ObserverMethod<? super T>> resolveObserverMethods(T event, Annotation... qualifiers) {
        if (observerMethodRegistry == null) {
            observerMethodRegistry = applicationContext.getBean(OdiObserverMethodRegistry.class);
        }
        if (event.getClass().getTypeParameters().length > 0) {
            throw new IllegalArgumentException("Type variable in event type");
        }
        Argument<?> argument = Argument.of(event.getClass());
        final io.micronaut.context.Qualifier qualifierInstances =
                odiAnnotations.resolveQualifier(qualifiers);
        return observerMethodRegistry
                .findSetOfObserverMethods(argument, qualifierInstances);
    }

    @Override
    public List resolveInterceptors(InterceptionType type, Annotation... interceptorBindings) {
        validateInterceptorBindings(interceptorBindings);
        return applicationContext.streamOfType(Interceptor.class)
                .filter(interceptor -> getPriority(interceptor) > 0)
                .filter(interceptor -> interceptor.intercepts(type))
                .filter(interceptor -> interceptorBindingsMatch(interceptor, interceptorBindings))
                .sorted((left, right) -> {
                    int result = Integer.compare(getPriority(left), getPriority(right));
                    if (result != 0) {
                        return result;
                    }
                    return left.getBeanClass().getName().compareTo(right.getBeanClass().getName());
                })
                .collect(Collectors.toList());
    }

    private void validateInterceptorBindings(Annotation... interceptorBindings) {
        if (interceptorBindings == null || interceptorBindings.length == 0) {
            throw new IllegalArgumentException("At least one interceptor binding is required");
        }
        Set<Class<? extends Annotation>> bindingTypes = new LinkedHashSet<>(interceptorBindings.length);
        for (Annotation interceptorBinding : interceptorBindings) {
            if (interceptorBinding == null) {
                throw new IllegalArgumentException("Interceptor binding cannot be null");
            }
            Class<? extends Annotation> bindingType = AnnotationUtils.findAnnotationClass(interceptorBinding);
            if (!bindingTypes.add(bindingType)) {
                throw new IllegalArgumentException("Interceptor binding cannot be duplicated for type: " + bindingType.getName());
            }
            if (!odiAnnotations.isInterceptorBinding(bindingType)) {
                throw new IllegalArgumentException("Not a valid interceptor binding annotation type: " + bindingType.getName());
            }
        }
    }

    private static boolean interceptorBindingsMatch(Interceptor<?> interceptor, Annotation... requiredBindings) {
        Set<Annotation> interceptorBindings = interceptor.getInterceptorBindings();
        if (interceptorBindings.isEmpty()) {
            return false;
        }
        for (Annotation interceptorBinding : interceptorBindings) {
            if (!containsInterceptorBinding(requiredBindings, interceptorBinding)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsInterceptorBinding(Annotation[] requiredBindings, Annotation interceptorBinding) {
        Class<? extends Annotation> interceptorBindingType = AnnotationUtils.findAnnotationClass(interceptorBinding);
        for (Annotation requiredBinding : requiredBindings) {
            if (AnnotationUtils.findAnnotationClass(requiredBinding).equals(interceptorBindingType)
                    && interceptorBindingValuesMatch(requiredBinding, interceptorBinding)) {
                return true;
            }
        }
        return false;
    }

    private static boolean interceptorBindingValuesMatch(Annotation requiredBinding, Annotation interceptorBinding) {
        if (requiredBinding.equals(interceptorBinding) || interceptorBinding.equals(requiredBinding)) {
            return true;
        }
        AnnotationValue<?> requiredBindingValues = bindingValues(requiredBinding);
        AnnotationValue<?> interceptorBindingValues = bindingValues(interceptorBinding);
        return requiredBindingValues.equals(interceptorBindingValues);
    }

    private static AnnotationValue<?> bindingValues(Annotation annotation) {
        AnnotationValue<?> annotationValue = AnnotationReflection.toAnnotationValue(annotation);
        String[] nonBindingMembers = annotationValue.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE);
        Map<CharSequence, Object> values = new LinkedHashMap<>(annotationValue.getValues());
        values.remove(AnnotationUtil.NON_BINDING_ATTRIBUTE);
        for (String nonBindingMember : nonBindingMembers) {
            values.remove(nonBindingMember);
        }
        return AnnotationValue.builder(annotationValue.getAnnotationName())
                .members(values)
                .build();
    }

    @Override
    public boolean isScope(Class<? extends Annotation> annotationType) {
        return odiAnnotations.isScope(annotationType);
    }

    @Override
    public boolean isNormalScope(Class<? extends Annotation> annotationType) {
        return odiAnnotations.isNormalScope(annotationType);
    }

    @Override
    public boolean isQualifier(Class<? extends Annotation> annotationType) {
        return odiAnnotations.isQualifier(annotationType);
    }

    @Override
    public boolean isStereotype(Class<? extends Annotation> annotationType) {
        return odiAnnotations.isStereotype(annotationType);
    }

    @Override
    public boolean isInterceptorBinding(Class<? extends Annotation> annotationType) {
        return odiAnnotations.isInterceptorBinding(annotationType);
    }

    @Override
    public Context getContext(Class<? extends Annotation> scopeType) {
        Collection<Context> contexts = getContexts(scopeType);
        List<Context> activeContexts = contexts.stream()
                .filter(Context::isActive)
                .collect(Collectors.toList());
        if (activeContexts.isEmpty()) {
            throw new ContextNotActiveException("No context active for scope: " + scopeType.getSimpleName());
        } else if (activeContexts.size() > 1) {
            throw new IllegalArgumentException("More than one active context for scope: " + scopeType.getSimpleName());
        } else {
            return activeContexts.iterator().next();
        }
    }

    @Override
    public Collection<Context> getContexts(Class<? extends Annotation> scopeType) {
        if (scopeType == Dependent.class || scopeType == null) {
            return Collections.singletonList(new DependentContext(null));
        }
        if (scopeType == Singleton.class) {
            return Collections.singletonList(SingletonContext.INSTANCE);
        }
        final List<Context> contexts = applicationContext.streamOfType(Context.class)
                .filter(c -> c.getScope() == scopeType)
                .collect(Collectors.toList());
        return Collections.unmodifiableList(contexts);
    }

    @Override
    public Event<Object> getEvent() {
        if (objectEvent == null) {
            objectEvent = applicationContext.getBean(Event.class);
        }
        return objectEvent;
    }

    @Override
    public OdiInstance<Object> createInstance() {
        return new OdiInstanceImpl<>(this, null, Argument.OBJECT_ARGUMENT);
    }

    @Override
    public OdiInstance<Object> createInstance(Context context) {
        return container.select(context);
    }

    @Override
    public BeanContext getBeanContext() {
        return applicationContext;
    }

    @Override
    public boolean isMatchingBean(Set<Type> beanTypes,
                                  Set<Annotation> beanQualifiers,
                                  Type requiredType,
                                  Set<Annotation> requiredQualifiers) {
        requireNonNull(beanTypes, "Null bean type");
        requireNonNull(beanQualifiers, "Null bean qualifiers");
        requireNonNull(requiredType, "Null required type");
        requireNonNull(requiredQualifiers, "Null required qualifiers");
        validateQualifiers("beanQualifiers annotation not a qualifier", beanQualifiers);
        validateQualifiers("requiredQualifiers annotation not a qualifier", requiredQualifiers);
        return matchesBeanType(requiredType, beanTypes) && matchesBeanQualifiers(beanQualifiers, requiredQualifiers);
    }

    @Override
    public boolean isMatchingEvent(Type eventType,
                                   Set<Annotation> eventQualifiers,
                                   Type observedEventType,
                                   Set<Annotation> observedEventQualifiers) {
        requireNonNull(eventType, "Null event type");
        requireNonNull(eventQualifiers, "Null event qualifiers");
        requireNonNull(observedEventType, "Null required type");
        requireNonNull(observedEventQualifiers, "Null required qualifiers");
        if (containsTypeVariable(eventType)) {
            throw new IllegalArgumentException("Type variable in event type");
        }
        validateQualifiers("A specifiedQualifiers annotation not a qualifier", eventQualifiers);
        validateQualifiers("An observedEventQualfiers annotation not a qualifier", observedEventQualifiers);
        return isEventAssignable(observedEventType, eventType) && matchesEventQualifiers(eventQualifiers, observedEventQualifiers);
    }

    private static boolean matchesBeanType(Type requiredType, Set<Type> beanTypes) {
        if (requiredType == Object.class) {
            return true;
        }
        if (!isLegalBeanType(requiredType)) {
            return false;
        }
        for (Type beanType : beanTypes) {
            if (isLegalBeanType(beanType) && isSameType(requiredType, beanType)) {
                return true;
            }
        }
        return false;
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean isEventAssignable(Type observedType, Type eventType) {
        if (isSameType(observedType, eventType)) {
            return true;
        }
        if (observedType instanceof Class<?>) {
            Class<?> eventClass = rawType(eventType);
            return eventClass != null && ((Class<?>) observedType).isAssignableFrom(eventClass);
        }
        if (observedType instanceof ParameterizedType && eventType instanceof ParameterizedType) {
            ParameterizedType observedParameterized = (ParameterizedType) observedType;
            ParameterizedType eventParameterized = (ParameterizedType) eventType;
            Class<?> observedRaw = rawType(observedParameterized);
            Class<?> eventRaw = rawType(eventParameterized);
            if (observedRaw == null || eventRaw == null || !observedRaw.isAssignableFrom(eventRaw)) {
                return false;
            }
            Type[] observedArguments = observedParameterized.getActualTypeArguments();
            Type[] eventArguments = eventParameterized.getActualTypeArguments();
            if (observedArguments.length != eventArguments.length) {
                return false;
            }
            for (int i = 0; i < observedArguments.length; i++) {
                if (!matchesTypeArgument(observedArguments[i], eventArguments[i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean matchesTypeArgument(Type observedArgument, Type eventArgument) {
        if (isSameType(observedArgument, eventArgument)) {
            return true;
        }
        if (observedArgument instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) observedArgument;
            for (Type upperBound : wildcard.getUpperBounds()) {
                if (!isBoundAssignable(upperBound, eventArgument)) {
                    return false;
                }
            }
            for (Type lowerBound : wildcard.getLowerBounds()) {
                if (!isBoundAssignable(eventArgument, lowerBound)) {
                    return false;
                }
            }
            return true;
        }
        if (observedArgument instanceof ParameterizedType && eventArgument instanceof ParameterizedType) {
            return isEventAssignable(observedArgument, eventArgument);
        }
        return false;
    }

    private static boolean isBoundAssignable(Type requiredBound, Type candidate) {
        Class<?> requiredClass = rawType(requiredBound);
        Class<?> candidateClass = rawType(candidate);
        return requiredClass != null && candidateClass != null && requiredClass.isAssignableFrom(candidateClass);
    }

    private static boolean isSameType(Type left, Type right) {
        if (Objects.equals(left, right)) {
            return true;
        }
        if (left instanceof ParameterizedType && right instanceof ParameterizedType) {
            ParameterizedType leftParameterized = (ParameterizedType) left;
            ParameterizedType rightParameterized = (ParameterizedType) right;
            if (!Objects.equals(leftParameterized.getRawType(), rightParameterized.getRawType())) {
                return false;
            }
            Type[] leftArguments = leftParameterized.getActualTypeArguments();
            Type[] rightArguments = rightParameterized.getActualTypeArguments();
            if (leftArguments.length != rightArguments.length) {
                return false;
            }
            for (int i = 0; i < leftArguments.length; i++) {
                if (!isSameType(leftArguments[i], rightArguments[i])) {
                    return false;
                }
            }
            return true;
        }
        if (left instanceof GenericArrayType && right instanceof GenericArrayType) {
            return isSameType(
                    ((GenericArrayType) left).getGenericComponentType(),
                    ((GenericArrayType) right).getGenericComponentType()
            );
        }
        return false;
    }

    private static boolean isLegalBeanType(Type type) {
        return type != null && !containsWildcard(type) && !containsTypeVariable(type);
    }

    private static boolean containsWildcard(Type type) {
        if (type instanceof WildcardType) {
            return true;
        }
        if (type instanceof ParameterizedType) {
            for (Type argument : ((ParameterizedType) type).getActualTypeArguments()) {
                if (containsWildcard(argument)) {
                    return true;
                }
            }
        }
        if (type instanceof GenericArrayType) {
            return containsWildcard(((GenericArrayType) type).getGenericComponentType());
        }
        return false;
    }

    private static boolean containsTypeVariable(Type type) {
        if (type instanceof TypeVariable<?>) {
            return true;
        }
        if (type instanceof ParameterizedType) {
            for (Type argument : ((ParameterizedType) type).getActualTypeArguments()) {
                if (containsTypeVariable(argument)) {
                    return true;
                }
            }
        }
        if (type instanceof GenericArrayType) {
            return containsTypeVariable(((GenericArrayType) type).getGenericComponentType());
        }
        if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            for (Type bound : wildcard.getUpperBounds()) {
                if (containsTypeVariable(bound)) {
                    return true;
                }
            }
            for (Type bound : wildcard.getLowerBounds()) {
                if (containsTypeVariable(bound)) {
                    return true;
                }
            }
        }
        return false;
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

    private static Class<?> toPrimitiveType(Class<?> type) {
        if (type == Boolean.class) {
            return boolean.class;
        }
        if (type == Byte.class) {
            return byte.class;
        }
        if (type == Character.class) {
            return char.class;
        }
        if (type == Double.class) {
            return double.class;
        }
        if (type == Float.class) {
            return float.class;
        }
        if (type == Integer.class) {
            return int.class;
        }
        if (type == Long.class) {
            return long.class;
        }
        if (type == Short.class) {
            return short.class;
        }
        return type;
    }

    private static void validateQualifiers(String message, Set<Annotation> qualifiers) {
        for (Annotation qualifier : qualifiers) {
            if (qualifier == null || !qualifier.annotationType().isAnnotationPresent(jakarta.inject.Qualifier.class)) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    private static boolean matchesBeanQualifiers(Set<Annotation> beanQualifiers, Set<Annotation> requiredQualifiers) {
        if (requiredQualifiers.isEmpty()) {
            return matchesBeanWithNoRequiredQualifiers(beanQualifiers);
        }
        Set<Annotation> candidates = normalizeCandidateQualifiers(beanQualifiers);
        return candidates.containsAll(requiredQualifiers);
    }

    private static boolean matchesEventQualifiers(Set<Annotation> eventQualifiers, Set<Annotation> observedEventQualifiers) {
        Set<Annotation> candidates = normalizeCandidateQualifiers(eventQualifiers);
        return candidates.containsAll(observedEventQualifiers);
    }

    private static boolean matchesBeanWithNoRequiredQualifiers(Set<Annotation> beanQualifiers) {
        if (beanQualifiers.isEmpty()) {
            return true;
        }
        return beanQualifiers.stream().anyMatch(annotation ->
                annotation.annotationType() == Default.class ||
                        annotation.annotationType() == Any.class ||
                        annotation.annotationType() == Named.class
        );
    }

    private static Set<Annotation> normalizeCandidateQualifiers(Set<Annotation> qualifiers) {
        Set<Annotation> normalized = new LinkedHashSet<>(qualifiers);
        normalized.add(Any.Literal.INSTANCE);
        if (qualifiers.isEmpty() || qualifiers.stream().anyMatch(OdiBeanContainerImpl::isDefaultedQualifier)) {
            normalized.add(Default.Literal.INSTANCE);
        }
        return normalized;
    }

    private static boolean isDefaultedQualifier(Annotation annotation) {
        return annotation.annotationType() == Default.class || annotation.annotationType() == Named.class;
    }
}
