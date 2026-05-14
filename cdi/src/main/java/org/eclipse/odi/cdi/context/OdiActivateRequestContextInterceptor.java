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

import io.micronaut.core.annotation.Internal;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Internal
@Interceptor
@ActivateRequestContext
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 100)
final class OdiActivateRequestContextInterceptor {

    @Inject
    OdiRequestContext requestContext;

    @AroundInvoke
    Object intercept(InvocationContext context) throws Exception {
        boolean activated = requestContext.activateRequestContext();
        try {
            return context.proceed();
        } finally {
            if (activated) {
                requestContext.deactivateRequestContext();
            }
        }
    }
}
