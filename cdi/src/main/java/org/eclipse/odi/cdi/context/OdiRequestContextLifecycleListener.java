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
package org.eclipse.odi.cdi.context;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;
import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;
import org.eclipse.odi.cdi.annotation.OdiBeanDefinition;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Activates the CDI request context while {@code @PostConstruct} callbacks run.
 */
@Internal
@Singleton
final class OdiRequestContextLifecycleListener implements BeanInitializedEventListener<Object>, BeanCreatedEventListener<Object> {

    private final BeanProvider<OdiRequestContext> requestContext;
    private final Set<Object> activatedBeans = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ReentrantLock lock = new ReentrantLock();

    OdiRequestContextLifecycleListener(BeanProvider<OdiRequestContext> requestContext) {
        this.requestContext = requestContext;
    }

    @Override
    public Object onInitialized(BeanInitializingEvent<Object> event) {
        Object bean = event.getBean();
        if (event.getBeanDefinition().hasAnnotation(OdiBeanDefinition.class)
                && !event.getBeanDefinition().getPostConstructMethods().isEmpty()
                && requestContext.get().activateRequestContext()) {
            lock.lock();
            try {
                activatedBeans.add(bean);
            } finally {
                lock.unlock();
            }
        }
        return bean;
    }

    @Override
    public Object onCreated(BeanCreatedEvent<Object> event) {
        Object bean = event.getBean();
        boolean deactivate;
        lock.lock();
        try {
            deactivate = activatedBeans.remove(bean);
        } finally {
            lock.unlock();
        }
        if (deactivate) {
            requestContext.get().deactivateRequestContext();
        }
        return bean;
    }
}
