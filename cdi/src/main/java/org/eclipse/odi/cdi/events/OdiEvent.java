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
package org.eclipse.odi.cdi.events;

import io.micronaut.context.Qualifier;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.util.TypeLiteral;
import org.eclipse.odi.cdi.AnnotationUtils;
import org.eclipse.odi.cdi.OdiBeanContainer;
import org.eclipse.odi.cdi.OdiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The implementation of {@link Event}.
 *
 * @param <T> The event type
 */
@Internal
final class OdiEvent<T> implements Event<T>, OdiEventMetadata {

    private static final Logger EVENT_LOGGER = LoggerFactory.getLogger(ApplicationEventPublisher.class);

    private final OdiBeanContainer beanContainer;
    private final AnnotationMetadata annotationMetadata;
    private final Argument<T> eventType;
    private final Type selectedEventType;
    @Nullable
    private final Qualifier<T> qualifier;
    @Nullable
    private final InjectionPoint<?> injectionPoint;
    private final Supplier<Collection<ObserverMethod<T>>> observerMethodsSyncSupplier;
    private final Supplier<Collection<ObserverMethod<T>>> observerMethodsAsyncSupplier;
    private final OdiObserverMethodRegistry observerMethodRegistry;
    private final Supplier<Executor> executorSupplier;

    @Nullable
    private jakarta.enterprise.inject.spi.InjectionPoint cdiInjectionPoint;
    @Nullable
    private Set<Annotation> qualifierAnnotations;

    OdiEvent(OdiBeanContainer beanContainer,
             AnnotationMetadata annotationMetadata,
             Argument<T> eventType,
             Type selectedEventType,
             @Nullable Qualifier<T> qualifier,
             @Nullable InjectionPoint<?> injectionPoint,
             OdiObserverMethodRegistry observerMethodRegistry,
             Supplier<Executor> executorSupplier) {
        this.beanContainer = beanContainer;
        this.annotationMetadata = annotationMetadata;
        this.eventType = eventType;
        this.selectedEventType = selectedEventType;
        this.qualifier = qualifier;
        this.injectionPoint = injectionPoint;
        this.observerMethodsSyncSupplier = SupplierUtil.memoizedNonEmpty(() ->
                observerMethodRegistry.findListOfObserverMethods(eventType, qualifier).stream().filter(m -> !m.isAsync()).collect(Collectors.toList()));
        this.observerMethodsAsyncSupplier = SupplierUtil.memoizedNonEmpty(() ->
                observerMethodRegistry.findListOfObserverMethods(eventType, qualifier).stream().filter(ObserverMethod::isAsync).collect(Collectors.toList()));
        this.executorSupplier = executorSupplier;
        this.observerMethodRegistry = observerMethodRegistry;
    }

    @Override
    public void fire(T event) {
        if (event != null) {
            if (EVENT_LOGGER.isDebugEnabled()) {
                EVENT_LOGGER.debug("Firing event: {}", event);
            }
            notifyObserverMethods(event, findObserverMethods(event, false));
        }
    }

    public <U extends T> CompletionStage<U> fireAsync(U event) {
        return fireAsync(event, executorSupplier.get());
    }

    @Override
    public <U extends T> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
        return fireAsync(event, options.getExecutor());
    }

    @Override
    public Event<T> select(Annotation... qualifiers) {
        return select(eventType, qualifiers);
    }

    @Override
    public <U extends T> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
        return select(Argument.of(subtype), qualifiers);
    }

    @Override
    public <U extends T> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        Argument<U> argument = OdiEventFactory.argumentOf(subtype.getType());
        return select(argument, subtype.getType(), qualifiers);
    }

    private <U extends T> OdiEvent<U> select(Argument<U> argument, Annotation[] annotations) {
        return select(argument, argument.asType(), annotations);
    }

    private <U extends T> OdiEvent<U> select(Argument<U> argument, Type selectedEventType, Annotation[] annotations) {
        validateNoTypeVariables(selectedEventType);
        AnnotationMetadata annotationMetadata = this.annotationMetadata;
        Qualifier<U> qualifier = (Qualifier<U>) this.qualifier;
        if (annotations != null && annotations.length > 0) {
            annotationMetadata = new AnnotationMetadataHierarchy(
                    AnnotationUtils.annotationMetadataFromQualifierAnnotations(annotations), this.annotationMetadata
            );
            final Qualifier<U> resolvedQualifiers = AnnotationUtils
                    .qualifierFromQualifierAnnotations(annotationMetadata, annotations);
            if (this.qualifier != null && this.qualifier.equals(AnyQualifier.INSTANCE)) {
                qualifier = resolvedQualifiers;
            } else {
                qualifier = Qualifiers.byQualifiers(resolvedQualifiers, (Qualifier<U>) this.qualifier);
            }
        }
        return new OdiEvent<>(
                beanContainer,
                annotationMetadata,
                argument,
                selectedEventType,
                qualifier,
                injectionPoint,
                observerMethodRegistry,
                executorSupplier
        );
    }

    private <U extends T> CompletableFuture<U> fireAsync(U event, Executor executor) {
        Objects.requireNonNull(event, "Event cannot be null");
        return notifyObserverMethodsAsync(event, findObserverMethods(event, true), executor);
    }

    private Collection<ObserverMethod<T>> findObserverMethods(@NonNull Object event, boolean async) {
        validateNoTypeVariables(selectedEventType);
        Set<ObserverMethod<T>> methods = new LinkedHashSet<>();
        for (Argument<?> argument : resolveEventArguments(event)) {
            Collection<ObserverMethod<T>> observerMethods = (Collection) observerMethodRegistry.findListOfObserverMethods((Argument) argument, (Qualifier) qualifier);
            for (ObserverMethod<T> observerMethod : observerMethods) {
                if (observerMethod.isAsync() == async) {
                    methods.add(observerMethod);
                }
            }
        }
        List<ObserverMethod<T>> sortedMethods = new ArrayList<>(methods);
        sortedMethods.sort(Comparator.comparing(ObserverMethod::getPriority));
        return sortedMethods;
    }

    private Collection<Argument<?>> resolveEventArguments(@NonNull Object event) {
        LinkedHashSet<Argument<?>> arguments = new LinkedHashSet<>();
        Argument<?> runtimeArgument = resolveRuntimeArgument(event);
        arguments.add(runtimeArgument);
        arguments.add(eventType);
        return arguments;
    }

    private Argument<?> resolveRuntimeArgument(@NonNull Object event) {
        Class<?> runtimeType = event.getClass();
        if (runtimeType == eventType.getType()) {
            return eventType;
        }
        TypeVariable<? extends Class<?>>[] runtimeTypeParameters = runtimeType.getTypeParameters();
        if (runtimeTypeParameters.length == 0) {
            return Argument.of(runtimeType);
        }
        Argument<?>[] selectedTypeParameters = eventType.getTypeParameters();
        if (runtimeTypeParameters.length != selectedTypeParameters.length) {
            throw new IllegalArgumentException("Type variable in event type");
        }
        return Argument.of(runtimeType, selectedTypeParameters);
    }

    private static void validateNoTypeVariables(Type type) {
        if (containsTypeVariable(type)) {
            throw new IllegalArgumentException("Type variable in event type");
        }
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

    private void notifyObserverMethods(@NonNull T event, Collection<ObserverMethod<T>> observerMethods) {
        if (!observerMethods.isEmpty()) {
            for (ObserverMethod<T> observerMethod : observerMethods) {
                notifyObserverMethod(event, observerMethod);
            }
        }
    }

    private <U extends T> CompletableFuture<U> notifyObserverMethodsAsync(@NonNull U event, Collection<ObserverMethod<T>> observerMethods, Executor executor) {
        if (!observerMethods.isEmpty()) {
            CompletableFuture<Throwable> cf = CompletableFuture.completedFuture(null);
            for (ObserverMethod<T> observerMethod : observerMethods) {
                CompletableFuture<Throwable> nextNotify = CompletableFuture.supplyAsync(() -> {
                    try {
                        notifyObserverMethod(event, observerMethod);
                    } catch (Throwable e) {
                        return e;
                    }
                    return null;
                }, executor);
                cf = cf.thenCombine(nextNotify, (t1, t2) -> {
                    if (t2 != null) {
                        if (t1 == null) {
                            t1 = new CompletionException(new IllegalStateException("Failed to invoke async event handler"));
                        }
                        t1.addSuppressed(t2);
                    }
                    return t1;
                });
            }
            return cf.thenApply(throwable -> {
                if (throwable != null) {
                    throw (CompletionException) throwable;
                }
                return event;
            });
        }
        return CompletableFuture.completedFuture(null);
    }

    private <U extends T> void notifyObserverMethod(U event, ObserverMethod<T> observerMethod) {
        try {
            if (EVENT_LOGGER.isTraceEnabled()) {
                EVENT_LOGGER.trace("Invoking observer method [{}] for event: {}", observerMethod, event);
            }
            observerMethod.notify(createContext(event));
        } catch (ClassCastException ex) {
            String msg = ex.getMessage();
            if (msg == null || msg.startsWith(event.getClass().getName())) {
                if (EVENT_LOGGER.isDebugEnabled()) {
                    EVENT_LOGGER.debug("Incompatible observerMethod for event: " + observerMethod, ex);
                }
            } else {
                throw ex;
            }
        }
    }

    private <K> EventContext<K> createContext(K event) {
        return new EventContext<K>() {
            @Override
            public K getEvent() {
                return event;
            }

            @Override
            public EventMetadata getMetadata() {
                return OdiEvent.this;
            }
        };
    }

    @Override
    public Qualifier<?> getQualifier() {
        return qualifier;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        if (qualifier == null) {
            return Collections.emptySet();
        }
        if (qualifierAnnotations == null) {
            qualifierAnnotations = AnnotationUtils.synthesizeQualifierAnnotations(annotationMetadata, beanContainer.getBeanContext().getClassLoader());
        }
        qualifierAnnotations = new HashSet<>(qualifierAnnotations);
        qualifierAnnotations.add(Any.Literal.INSTANCE);
        return qualifierAnnotations;
    }

    @Override
    public jakarta.enterprise.inject.spi.InjectionPoint getInjectionPoint() {
        if (injectionPoint == null) {
            return null;
        }
        if (cdiInjectionPoint == null) {
            cdiInjectionPoint = OdiUtils.createCDIInjectionPoint(beanContainer, injectionPoint, Argument.of(Event.class, eventType));
        }
        return cdiInjectionPoint;
    }

    @Override
    public Type getType() {
        return eventType.asType();
    }

}
