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
package org.eclipse.odi.cdi.events;

import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Singleton;
import org.eclipse.odi.cdi.OdiBean;
import org.eclipse.odi.cdi.OdiBeanContainer;

import java.lang.annotation.Annotation;

@Internal
final class ObserverMethodContext {

    private ObserverMethodContext() {
    }

    static boolean isActive(OdiBeanContainer beanContainer, Bean<?> bean) {
        Class<? extends Annotation> scope = bean.getScope();
        return scope == Dependent.class || findActiveContext(beanContainer, scope) != null;
    }

    static boolean hasExistingContextualInstance(OdiBeanContainer beanContainer, Bean<?> bean) {
        Class<? extends Annotation> scope = bean.getScope();
        if (scope == Dependent.class) {
            return false;
        }
        if (scope == Singleton.class) {
            return hasActiveSingletonRegistration(beanContainer, bean);
        }
        Context context = findActiveContext(beanContainer, scope);
        if (context == null) {
            return false;
        }
        return getExistingInstance(context, bean) != null;
    }

    private static Context findActiveContext(OdiBeanContainer beanContainer, Class<? extends Annotation> scope) {
        return beanContainer.getContexts(scope).stream().filter(Context::isActive).findFirst().orElse(null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getExistingInstance(Context context, Bean<?> bean) {
        return context.get((Contextual) bean);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean hasActiveSingletonRegistration(OdiBeanContainer beanContainer, Bean<?> bean) {
        if (!(bean instanceof OdiBean)) {
            return false;
        }
        BeanDefinition<?> beanDefinition = ((OdiBean<?>) bean).getBeanDefinition();
        for (BeanRegistration<?> registration : beanContainer.getBeanContext().getActiveBeanRegistrations(beanDefinition.getBeanType())) {
            Bean<?> registeredBean = beanContainer.getBean((BeanDefinition) registration.getBeanDefinition());
            if (bean.equals(registeredBean)) {
                return true;
            }
        }
        return false;
    }
}
