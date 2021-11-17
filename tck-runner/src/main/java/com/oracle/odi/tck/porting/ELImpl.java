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
package com.oracle.odi.tck.porting;

import jakarta.el.ELContext;
import jakarta.enterprise.inject.spi.BeanManager;
import org.jboss.cdi.tck.spi.EL;

/**
 * TCK's EL implementation.
 */
public class ELImpl implements EL {

    @Override
    public <T> T evaluateValueExpression(BeanManager beanManager, String expression, Class<T> expectedType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T evaluateMethodExpression(BeanManager beanManager, String expression, Class<T> expectedType, Class<?>[] expectedParamTypes,
            Object[] expectedParams) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ELContext createELContext(BeanManager beanManager) {
        throw new UnsupportedOperationException();
    }

}
