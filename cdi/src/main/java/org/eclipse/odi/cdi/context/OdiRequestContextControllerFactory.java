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

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.Default;

@Internal
@Factory
final class OdiRequestContextControllerFactory {

    @Bean(typed = RequestContextController.class)
    @Default
    @Dependent
    RequestContextController requestContextController(OdiRequestContext requestContext) {
        return new RequestContextController() {
            @Override
            public boolean activate() {
                return requestContext.activateRequestContext();
            }

            @Override
            public void deactivate() {
                requestContext.deactivateRequestContext();
            }
        };
    }
}
