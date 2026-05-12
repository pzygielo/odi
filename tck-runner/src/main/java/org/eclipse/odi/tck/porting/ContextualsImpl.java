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
package org.eclipse.odi.tck.porting;

import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.CreationalContext;
import org.jboss.cdi.tck.spi.Contextuals;

/**
 * TCK's contextuals implementation.
 */
public class ContextualsImpl implements Contextuals {

    @Override
    public <T> Inspectable<T> create(T instance, Context context) {
        return new InspectableContextual<>(instance);
    }

    private static final class InspectableContextual<T> implements Inspectable<T> {
        private final T instance;
        private CreationalContext<T> creationalContextPassedToCreate;
        private T instancePassedToDestroy;
        private CreationalContext<T> creationalContextPassedToDestroy;

        private InspectableContextual(T instance) {
            this.instance = instance;
        }

        @Override
        public T create(CreationalContext<T> creationalContext) {
            this.creationalContextPassedToCreate = creationalContext;
            return instance;
        }

        @Override
        public void destroy(T instance, CreationalContext<T> creationalContext) {
            this.instancePassedToDestroy = instance;
            this.creationalContextPassedToDestroy = creationalContext;
        }

        @Override
        public CreationalContext<T> getCreationalContextPassedToCreate() {
            return creationalContextPassedToCreate;
        }

        @Override
        public T getInstancePassedToDestroy() {
            return instancePassedToDestroy;
        }

        @Override
        public CreationalContext<T> getCreationalContextPassedToDestroy() {
            return creationalContextPassedToDestroy;
        }
    }
}
