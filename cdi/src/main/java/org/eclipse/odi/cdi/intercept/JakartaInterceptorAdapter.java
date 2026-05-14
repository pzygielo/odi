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
package org.eclipse.odi.cdi.intercept;

import io.micronaut.aop.ConstructorInterceptor;
import io.micronaut.aop.ConstructorInvocationContext;
import io.micronaut.aop.InterceptedProxy;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InvocationContext;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.interceptor.AroundInvoke;
import org.eclipse.odi.cdi.AnnotationUtils;
import org.eclipse.odi.cdi.OdiBeanImpl;
import org.eclipse.odi.cdi.annotation.DisposerMethod;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * Adapter type for delegating interception to a Jakarta Interceptor implementation.
 *
 * @param <B> The bean type. The target bean type
 */
@Internal
public final class JakartaInterceptorAdapter<B> extends OdiBeanImpl<B> implements
        ConstructorInterceptor<Object>,
        MethodInterceptor<Object, Object>,
        Interceptor<B> {

    private final BeanDefinition<B> beanDefinition;
    private final BeanContext beanContext;
    private final int priority;
    private ExecutableMethod<B, Object>[] aroundConstruct;
    private ExecutableMethod<B, Object>[] aroundInvoke;
    private ExecutableMethod<B, Object>[] preDestroy;
    private ExecutableMethod<B, Object>[] postConstruct;
    private Set<Annotation> interceptorBindings;
    private boolean isSelfInterceptor;
    private static final ReentrantReadWriteLock TARGET_INTERCEPTOR_INSTANCES_LOCK = new ReentrantReadWriteLock();
    private static final Map<Object, Map<String, BeanRegistration<?>>> TARGET_INTERCEPTOR_INSTANCES =
            new IdentityHashMap<>();
    private static final Map<Object, Object> TARGET_CONTEXTUAL_INSTANCES = new IdentityHashMap<>();

    /**
     * Default constructor.
     *
     * @param beanDefinition    The bean definition
     * @param beanContext       The bean context
     * @param resolutionContext The resolution context
     */
    public JakartaInterceptorAdapter(BeanDefinition<B> beanDefinition,
                                     BeanContext beanContext,
                                     BeanResolutionContext resolutionContext) {
        super(beanContext, beanDefinition);
        this.beanContext = beanContext;
        this.beanDefinition = beanContext.getBeanDefinition(beanDefinition.asArgument());
        if (!this.beanDefinition.hasStereotype(jakarta.interceptor.Interceptor.class)) {
            this.isSelfInterceptor = true;
        }
        this.priority = this.beanDefinition.intValue(Priority.class).orElse(0);
    }

    /**
     * Mark interceptor as self interceptor.
     *
     * @param isSelfInterceptor true if is self interceptor
     */
    public void setSelfInterceptor(String isSelfInterceptor) {
        this.isSelfInterceptor = Boolean.parseBoolean(isSelfInterceptor);
    }

    /**
     * @return true if self interceptor
     */
    public boolean isSelfInterceptor() {
        return isSelfInterceptor;
    }

    /**
     * Sets the name of the method that defines {@link jakarta.interceptor.AroundConstruct}.
     *
     * @param aroundConstructMethod The name of the method.
     */
    @SuppressWarnings("unchecked")
    public void setAroundConstruct(List<String> aroundConstructMethod) {
        this.aroundConstruct = toMethodArray(aroundConstructMethod);
    }

    /**
     * Sets the name of the method that defines {@link jakarta.interceptor.AroundInvoke}.
     *
     * @param aroundInvokeMethod The name of the method.
     */
    public void setAroundInvoke(List<String> aroundInvokeMethod) {
        this.aroundInvoke = toMethodArray(aroundInvokeMethod);
    }

    /**
     * Sets the name of the method that defines {@link jakarta.annotation.PreDestroy} interception.
     *
     * @param preDestroyMethod The name of the method.
     */
    public void setPreDestroy(List<String> preDestroyMethod) {
        this.preDestroy = toMethodArray(preDestroyMethod);
    }

    /**
     * Sets the name of the method that defines {@link jakarta.annotation.PostConstruct} interception.
     *
     * @param postConstructMethod The name of the method
     */
    public void setPostConstruct(List<String> postConstructMethod) {
        this.postConstruct = toMethodArray(postConstructMethod);
    }

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        if (context.hasAnnotation(AroundInvoke.class)) {
            return context.proceed();
        }
        if (context instanceof ConstructorInvocationContext) {
            return intercept((ConstructorInvocationContext<Object>) context);
        } else if (context instanceof MethodInvocationContext) {
            Object result = intercept((MethodInvocationContext<Object, Object>) context);
            if (result == null) {
                throw new IllegalStateException("Result cannot be null");
            }
        }
        throw new IllegalStateException("Unknown context type: " + context);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        if (aroundConstruct != null) {
            ConstructorInvocationContextAdapter<B> constructorInvocationContextAdapter =
                    new ConstructorInvocationContextAdapter<>(this, context, aroundConstruct);
            BeanRegistration<B> interceptorRegistration = resolveInterceptorBeanRegistration();
            B interceptor = interceptorRegistration.getBean();
            Object target = constructorInvocationContextAdapter.invoke(interceptor);
            rememberInterceptorBean(target, interceptorRegistration);
            return target;
        } else {
            return context.proceed();
        }
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.hasAnnotation(AroundInvoke.class)) {
            return context.proceed();
        }
        InterceptorKind interceptorKind = selectMethodKind(context);
        final ExecutableMethod<B, Object>[] executableMethods = selectMethod(interceptorKind);

        if (executableMethods == null) {
            return context.proceed();
        }
        ExecutableMethod<B, Object> executableMethod = executableMethods[0];
        InvocationContextAdapter<B> ctx = new InvocationContextAdapter<>(
                this,
                context,
                executableMethods,
                interceptorKind
        );
        B target = resolveInterceptorBean(context);

        try {
            if (executableMethod.getReturnType().isVoid()) {
                ctx.invoke(target);
                return context.getTarget();
            }
            return ctx.invoke(target);
        } finally {
            if (context.getKind() == InterceptorKind.PRE_DESTROY) {
                forgetInterceptorBean(context.getTarget());
            }
        }
    }

    private InterceptorKind selectMethodKind(MethodInvocationContext<Object, Object> context) {
        if (context.getKind() == InterceptorKind.PRE_DESTROY && context.hasAnnotation(DisposerMethod.class)) {
            return InterceptorKind.AROUND;
        }
        return context.getKind();
    }

    @Override
    public int getOrder() {
        return priority;
    }

    @Override
    public Set<Annotation> getInterceptorBindings() {
        if (interceptorBindings == null) {
            interceptorBindings = AnnotationUtils.synthesizeInterceptorBindingAnnotations(beanDefinition.getAnnotationMetadata());
        }
        return interceptorBindings;
    }

    @Override
    public boolean intercepts(InterceptionType type) {
        return selectMethod(type) != null;
    }

    /**
     * @param kind The Micronaut interceptor kind
     * @return true if the backing Jakarta interceptor declares a method for the kind
     */
    boolean intercepts(InterceptorKind kind) {
        return selectMethod(kind) != null;
    }

    @Override
    public Object intercept(InterceptionType type, B instance, jakarta.interceptor.InvocationContext ctx) {
        final ExecutableMethod<B, Object>[] executableMethods = selectMethod(type);
        if (executableMethods != null) {
            return executableMethods[0].invoke(instance, ctx);
        }
        throw new IllegalStateException("Not supported intercept type: " + type);
    }

    @Nullable
    private ExecutableMethod<B, Object>[] selectMethod(@NonNull InterceptionType type) {
        try {
            if (type == InterceptionType.AROUND_INVOKE) {
                return selectMethod(InterceptorKind.AROUND);
            } else {
                final InterceptorKind kind = InterceptorKind.valueOf(type.name());
                return selectMethod(kind);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private ExecutableMethod<B, Object>[] selectMethod(@NonNull InterceptorKind type) {
        switch (type) {
            case AROUND_CONSTRUCT:
                return aroundConstruct;
            case PRE_DESTROY:
                return preDestroy;
            case AROUND:
                return aroundInvoke;
            case POST_CONSTRUCT:
                return postConstruct;
            default:
                return null;
        }
    }

    private B resolveInterceptorBean() {
        return resolveInterceptorBeanRegistration().getBean();
    }

    private BeanRegistration<B> resolveInterceptorBeanRegistration() {
        if (isSelfInterceptor) {
            throw new IllegalStateException("Self interceptor target is not available");
        }
        return beanContext.getBeanRegistration(beanDefinition.asArgument(), beanDefinition.getDeclaredQualifier());
    }

    @SuppressWarnings("unchecked")
    private B resolveInterceptorBean(InvocationContext<Object, Object> context) {
        if (!isSelfInterceptor) {
            B targetInterceptorBean = findTargetInterceptorBean(context.getTarget());
            if (targetInterceptorBean != null) {
                return targetInterceptorBean;
            }
            return ensureInterceptorBean(context.getTarget());
        }
        Object target = context.getTarget();
        if (target instanceof InterceptedProxy<?> interceptedProxy) {
            target = interceptedProxy.interceptedTarget();
        }
        return (B) target;
    }

    @SuppressWarnings("unchecked")
    B ensureInterceptorBean(@Nullable Object target) {
        if (isSelfInterceptor) {
            if (target instanceof InterceptedProxy<?> interceptedProxy) {
                target = interceptedProxy.interceptedTarget();
            }
            return (B) target;
        }
        if (target == null) {
            return resolveInterceptorBean();
        }
        TARGET_INTERCEPTOR_INSTANCES_LOCK.writeLock().lock();
        try {
            BeanRegistration<B> interceptorRegistration = findInterceptorRegistrationForTargetWithoutLock(target);
            if (interceptorRegistration == null && target instanceof InterceptedProxy<?> interceptedProxy) {
                interceptorRegistration = findInterceptorRegistrationForTargetWithoutLock(interceptedProxy.interceptedTarget());
            }
            if (interceptorRegistration == null) {
                interceptorRegistration = resolveInterceptorBeanRegistration();
                rememberInterceptorBeanForTargetWithoutLock(target, interceptorRegistration);
                if (target instanceof InterceptedProxy<?> interceptedProxy) {
                    Object interceptedTarget = interceptedProxy.interceptedTarget();
                    if (interceptedTarget != null) {
                        rememberInterceptorBeanForTargetWithoutLock(interceptedTarget, interceptorRegistration);
                        rememberContextualTargetWithoutLock(interceptedTarget, target);
                    }
                }
            }
            return interceptorRegistration.getBean();
        } finally {
            TARGET_INTERCEPTOR_INSTANCES_LOCK.writeLock().unlock();
        }
    }

    private void rememberInterceptorBean(@Nullable Object target, BeanRegistration<B> interceptorRegistration) {
        if (target == null || isSelfInterceptor) {
            return;
        }
        rememberInterceptorBeanForTarget(target, interceptorRegistration);
        if (target instanceof InterceptedProxy<?> interceptedProxy) {
            Object interceptedTarget = interceptedProxy.interceptedTarget();
            if (interceptedTarget != null) {
                rememberInterceptorBeanForTarget(interceptedTarget, interceptorRegistration);
                rememberContextualTarget(interceptedTarget, target);
            }
        }
    }

    private void rememberInterceptorBeanForTarget(Object target, BeanRegistration<B> interceptorRegistration) {
        TARGET_INTERCEPTOR_INSTANCES_LOCK.writeLock().lock();
        try {
            rememberInterceptorBeanForTargetWithoutLock(target, interceptorRegistration);
        } finally {
            TARGET_INTERCEPTOR_INSTANCES_LOCK.writeLock().unlock();
        }
    }

    private void rememberInterceptorBeanForTargetWithoutLock(Object target, BeanRegistration<B> interceptorRegistration) {
        TARGET_INTERCEPTOR_INSTANCES
                .computeIfAbsent(target, ignored -> new HashMap<>())
                .put(beanDefinition.getName(), interceptorRegistration);
    }

    private void rememberContextualTarget(Object interceptedTarget, Object contextualTarget) {
        TARGET_INTERCEPTOR_INSTANCES_LOCK.writeLock().lock();
        try {
            rememberContextualTargetWithoutLock(interceptedTarget, contextualTarget);
        } finally {
            TARGET_INTERCEPTOR_INSTANCES_LOCK.writeLock().unlock();
        }
    }

    private void rememberContextualTargetWithoutLock(Object interceptedTarget, Object contextualTarget) {
        TARGET_CONTEXTUAL_INSTANCES.put(interceptedTarget, contextualTarget);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private B findTargetInterceptorBean(@Nullable Object target) {
        if (target == null) {
            return null;
        }
        B interceptor = findInterceptorBeanForTarget(target);
        if (interceptor == null && target instanceof InterceptedProxy<?> interceptedProxy) {
            interceptor = findInterceptorBeanForTarget(interceptedProxy.interceptedTarget());
        }
        return interceptor;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private B findInterceptorBeanForTarget(@Nullable Object target) {
        if (target == null) {
            return null;
        }
        TARGET_INTERCEPTOR_INSTANCES_LOCK.readLock().lock();
        try {
            return findInterceptorBeanForTargetWithoutLock(target);
        } finally {
            TARGET_INTERCEPTOR_INSTANCES_LOCK.readLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private B findInterceptorBeanForTargetWithoutLock(@Nullable Object target) {
        BeanRegistration<B> interceptorRegistration = findInterceptorRegistrationForTargetWithoutLock(target);
        return interceptorRegistration == null ? null : interceptorRegistration.getBean();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private BeanRegistration<B> findInterceptorRegistrationForTargetWithoutLock(@Nullable Object target) {
        if (target == null) {
            return null;
        }
        Map<String, BeanRegistration<?>> interceptors = TARGET_INTERCEPTOR_INSTANCES.get(target);
        if (interceptors == null) {
            return null;
        }
        return (BeanRegistration<B>) interceptors.get(beanDefinition.getName());
    }

    private void forgetInterceptorBean(@Nullable Object target) {
        if (target == null || isSelfInterceptor) {
            return;
        }
        BeanRegistration<?> interceptorRegistration;
        TARGET_INTERCEPTOR_INSTANCES_LOCK.writeLock().lock();
        try {
            interceptorRegistration = forgetInterceptorBeanForTarget(target);
            if (target instanceof InterceptedProxy<?> interceptedProxy) {
                Object interceptedTarget = interceptedProxy.interceptedTarget();
                if (interceptedTarget != null) {
                    BeanRegistration<?> targetInterceptorRegistration = forgetInterceptorBeanForTarget(interceptedTarget);
                    if (interceptorRegistration == null) {
                        interceptorRegistration = targetInterceptorRegistration;
                    }
                    TARGET_CONTEXTUAL_INSTANCES.remove(interceptedTarget);
                }
            }
            TARGET_CONTEXTUAL_INSTANCES.remove(target);
        } finally {
            TARGET_INTERCEPTOR_INSTANCES_LOCK.writeLock().unlock();
        }
        if (interceptorRegistration != null) {
            beanContext.destroyBean((BeanRegistration) interceptorRegistration);
        }
    }

    @Nullable
    private BeanRegistration<?> forgetInterceptorBeanForTarget(Object target) {
        Map<String, BeanRegistration<?>> interceptors = TARGET_INTERCEPTOR_INSTANCES.get(target);
        if (interceptors != null) {
            BeanRegistration<?> interceptor = interceptors.remove(beanDefinition.getName());
            if (interceptors.isEmpty()) {
                TARGET_INTERCEPTOR_INSTANCES.remove(target);
            }
            return interceptor;
        }
        return null;
    }

    static void clearTargetInterceptorBeans() {
        TARGET_INTERCEPTOR_INSTANCES_LOCK.writeLock().lock();
        try {
            TARGET_INTERCEPTOR_INSTANCES.clear();
            TARGET_CONTEXTUAL_INSTANCES.clear();
        } finally {
            TARGET_INTERCEPTOR_INSTANCES_LOCK.writeLock().unlock();
        }
    }

    static void destroyInterceptorBeansForTarget(BeanContext beanContext, @Nullable Object target) {
        if (target == null) {
            return;
        }
        Set<BeanRegistration<?>> interceptors = Collections.newSetFromMap(new IdentityHashMap<>());
        TARGET_INTERCEPTOR_INSTANCES_LOCK.writeLock().lock();
        try {
            collectInterceptorBeansForTargetWithoutLock(target, interceptors);
            Object contextualTarget = TARGET_CONTEXTUAL_INSTANCES.remove(target);
            if (contextualTarget != null) {
                collectInterceptorBeansForTargetWithoutLock(contextualTarget, interceptors);
            }
            if (target instanceof InterceptedProxy<?> interceptedProxy) {
                Object interceptedTarget = interceptedProxy.interceptedTarget();
                if (interceptedTarget != null) {
                    TARGET_CONTEXTUAL_INSTANCES.remove(interceptedTarget);
                    collectInterceptorBeansForTargetWithoutLock(interceptedTarget, interceptors);
                }
            }
        } finally {
            TARGET_INTERCEPTOR_INSTANCES_LOCK.writeLock().unlock();
        }
        for (BeanRegistration<?> interceptor : interceptors) {
            beanContext.destroyBean((BeanRegistration) interceptor);
        }
    }

    private static void collectInterceptorBeansForTargetWithoutLock(Object target, Set<BeanRegistration<?>> interceptors) {
        Map<String, BeanRegistration<?>> targetInterceptors = TARGET_INTERCEPTOR_INSTANCES.remove(target);
        if (targetInterceptors != null) {
            interceptors.addAll(targetInterceptors.values());
        }
    }

    static Object resolveContextualTarget(Object target) {
        TARGET_INTERCEPTOR_INSTANCES_LOCK.readLock().lock();
        try {
            return TARGET_CONTEXTUAL_INSTANCES.getOrDefault(target, target);
        } finally {
            TARGET_INTERCEPTOR_INSTANCES_LOCK.readLock().unlock();
        }
    }

    @SuppressWarnings("rawtypes")
    private ExecutableMethod[] toMethodArray(List<String> methods) {
        return methods.stream().flatMap(name -> {
            Optional<ExecutableMethod<B, Object>> method = beanDefinition.findMethod(name, jakarta.interceptor.InvocationContext.class);
            if (method.isPresent()) {
                return method.stream();
            }
            method = beanDefinition.findMethod(name);
            if (method.isPresent()) {
                return method.stream();
            }
            return Stream.empty();
        }).toArray(ExecutableMethod[]::new);
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public String toString() {
        return beanDefinition.toString();
    }
}
