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
import io.micronaut.context.ApplicationContextProvider;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.ResolutionException;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.TypeLiteral;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
@Factory
final class OdiSeContainer extends CDI<Object>
        implements SeContainer, OdiInstance<Object>, ApplicationContextProvider {
    private static final Map<ApplicationContext, OdiSeContainer> RUNNING_CONTAINERS = new LinkedHashMap<>(5);
    private static final ReentrantReadWriteLock RUNNING_CONTAINERS_LOCK = new ReentrantReadWriteLock();
    private final ApplicationContext applicationContext;
    private final OdiBeanContainerImpl beanContainer;

    protected OdiSeContainer(ApplicationContext context) {
        this.applicationContext = context;
        this.beanContainer = new OdiBeanContainerImpl(this, context.getBean(OdiAnnotations.class), context);
        register(context, this);
    }

    @Override
    public void close() {
        try {
            applicationContext.close();
        } finally {
            unregister(applicationContext);
        }
    }

    static CDI<Object> currentContainer() {
        RUNNING_CONTAINERS_LOCK.writeLock().lock();
        try {
            OdiSeContainer latestRunningContainer = null;
            Iterator<Map.Entry<ApplicationContext, OdiSeContainer>> iterator = RUNNING_CONTAINERS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ApplicationContext, OdiSeContainer> entry = iterator.next();
                OdiSeContainer container = entry.getValue();
                if (entry.getKey().isRunning() && container.isRunning()) {
                    latestRunningContainer = container;
                } else {
                    iterator.remove();
                }
            }
            if (latestRunningContainer != null) {
                return latestRunningContainer;
            }
        } finally {
            RUNNING_CONTAINERS_LOCK.writeLock().unlock();
        }
        throw new IllegalStateException("No running SeContainer present");
    }

    private static void register(ApplicationContext context, OdiSeContainer container) {
        RUNNING_CONTAINERS_LOCK.writeLock().lock();
        try {
            RUNNING_CONTAINERS.put(context, container);
        } finally {
            RUNNING_CONTAINERS_LOCK.writeLock().unlock();
        }
    }

    private static void unregister(ApplicationContext context) {
        RUNNING_CONTAINERS_LOCK.writeLock().lock();
        try {
            RUNNING_CONTAINERS.remove(context);
        } finally {
            RUNNING_CONTAINERS_LOCK.writeLock().unlock();
        }
    }

    @Override
    public boolean isRunning() {
        return applicationContext.isRunning();
    }

    @Override
    public BeanManager getBeanManager() {
        throw new UnsupportedOperationException("Use CDI.current().getBeanContainer() instead");
    }

    @Override
    public BeanContainer getBeanContainer() {
        return beanContainer;
    }

    OdiInstance<Object> select(Context context) {
        return new OdiInstanceImpl<>(
                beanContainer,
                context,
                Argument.OBJECT_ARGUMENT,
                null,
                (Qualifier<Object>) null
        );
    }

    @Override
    public <U> OdiInstance<U> select(Argument<U> argument, Qualifier<U> qualifier) {
        return new OdiInstanceImpl<>(
                beanContainer,
                null,
                argument,
                null,
                qualifier
        );
    }

    @Override
    public OdiInstance<Object> select(Annotation... qualifiers) {
        if (!isRunning()) {
            throw new IllegalStateException("SeContainer already shutdown");
        }
        return new OdiInstanceImpl<>(beanContainer, null, Argument.OBJECT_ARGUMENT, qualifiers);
    }

    @Override
    public <U> OdiInstance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return new OdiInstanceImpl<>(beanContainer, null, Argument.of(subtype), qualifiers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <U> OdiInstance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return new OdiInstanceImpl(beanContainer, null, Argument.of(subtype.getType()), qualifiers);
    }

    @Override
    public boolean isUnsatisfied() {
        return false;
    }

    @Override
    public boolean isAmbiguous() {
        return false;
    }

    @Override
    public void destroy(Object instance) {
        applicationContext.destroyBean(instance);
    }

    @Override
    public Handle<Object> getHandle() {
        return new Handle<>() {
            @Override
            public Object get() {
                return OdiSeContainer.this;
            }

            @Override
            public jakarta.enterprise.inject.spi.Bean<Object> getBean() {
                return new OdiBeanImpl(OdiSeContainer.this.applicationContext, new BeanDefinition() {

                    @Override
                    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
                        return true;
                    }

                    @Override
                    public Class getBeanType() {
                        return SeContainer.class;
                    }
                });
            }

            @Override
            public void destroy() {
                close();
            }

            @Override
            public void close() {
                if (OdiSeContainer.this.applicationContext.isRunning()) {
                    OdiSeContainer.this.close();
                }
            }
        };
    }

    @Override
    public Iterable<Handle<Object>> handles() {
        return Collections.singletonList(getHandle());
    }

    @Override
    public Object get() {
        return this;
    }

    @Override
    public Iterator<Object> iterator() {
        return Collections.singletonList((Object) this).iterator();
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Bean
    @Default
    OdiBeanContainer beanContainer() {
        return beanContainer;
    }

    /**
     * Creates the parameters object for synthetic beans.
     *
     * @param injectionPoint The injection point
     * @return The parameters
     */
    @Bean
    Parameters parameterCreator(ArgumentInjectionPoint<?, ?> injectionPoint) {
        final BeanDefinition<?> declaringBean = injectionPoint.getDeclaringBean();
        return OdiUtils.createParameters(declaringBean);
    }

    @Bean
    @Default
    SeContainer seContainer() {
        return this;
    }

    @Bean
    @Any
    jakarta.enterprise.inject.spi.Bean<?> getBean(InjectionPoint<?> injectionPoint) {
        if (injectionPoint instanceof ArgumentCoercible) {
            final Argument<?> argument = ((ArgumentCoercible<?>) injectionPoint).asArgument();
            try {
                return beanContainer.getBean(
                        argument.getFirstTypeVariable()
                                .orElseThrow(() -> new UnsatisfiedResolutionException("Cannot resolve bean for injection point:"
                                        + " " + injectionPoint)),
                        Qualifiers.forArgument(argument)
                );
            } catch (NonUniqueBeanException e) {
                throw new AmbiguousResolutionException(e.getMessage(), e);
            } catch (NoSuchBeanException e) {
                throw new UnsatisfiedResolutionException(e.getMessage(), e);
            } catch (Throwable t) {
                throw new ResolutionException(t.getMessage(), t);
            }
        }

        throw new UnsatisfiedResolutionException("Cannot resolve bean for injection point: " + injectionPoint);
    }

}
