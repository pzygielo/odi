/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import io.micronaut.core.annotation.Internal;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Singleton;
import org.eclipse.odi.cdi.OdiBeanContainer;

import java.lang.annotation.Annotation;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple {@link RequestScoped} Micronaut context.
 */
@Internal
@Singleton
final class RequestContext extends AbstractContext implements OdiRequestContext {

    private final BeanProvider<OdiBeanContainer> beanContainer;
    private final ReentrantLock lock = new ReentrantLock();

    RequestContext(BeanProvider<OdiBeanContainer> beanContainer) {
        super(false);
        this.beanContainer = beanContainer;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return RequestScoped.class;
    }

    @Override
    public boolean activateRequestContext() {
        lock.lock();
        try {
            if (isActive()) {
                return false;
            }
            activate();
            beanContainer.get().getEvent().select(Initialized.Literal.REQUEST).fire(this);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deactivateRequestContext() {
        lock.lock();
        try {
            if (!isActive()) {
                throw new ContextNotActiveException("Request context is not active");
            }
            beanContainer.get().getEvent().select(BeforeDestroyed.Literal.REQUEST).fire(this);
            destroy();
            beanContainer.get().getEvent().select(Destroyed.Literal.REQUEST).fire(this);
        } finally {
            lock.unlock();
        }
    }
}
