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

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.BeanContextEvent;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.Internal;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Shutdown;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Singleton;
import org.eclipse.odi.cdi.OdiBeanContainer;

@Internal
@Singleton
final class OdiLifecycleEventBridge implements ApplicationEventListener<BeanContextEvent> {

    private final OdiBeanContainer beanContainer;

    OdiLifecycleEventBridge(OdiBeanContainer beanContainer) {
        this.beanContainer = beanContainer;
    }

    @Override
    public void onApplicationEvent(BeanContextEvent event) {
        if (event instanceof StartupEvent) {
            beanContainer.getEvent().select(Initialized.Literal.APPLICATION).fire(event.getSource());
            beanContainer.getEvent().select(Startup.class).fire(new Startup());
        } else if (event instanceof ShutdownEvent) {
            beanContainer.getEvent().select(Shutdown.class).fire(new Shutdown());
            beanContainer.getEvent().select(BeforeDestroyed.Literal.APPLICATION).fire(event.getSource());
        }
    }

    @Override
    public boolean supports(BeanContextEvent event) {
        return event instanceof StartupEvent || event instanceof ShutdownEvent;
    }
}
