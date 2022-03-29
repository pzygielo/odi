/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oracle.odi.cdi;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OdiBeanManagerTest {

    @Test
    void testOdiBeanManager() {
        try (SeContainer container = SeContainerInitializer.newInstance().initialize()) {
            final BeanContainer beanManager = CDI.current().getBeanContainer();

            assertNotNull(beanManager);

            OdiTestSupport.testUnsupportedAPI(beanManager, OdiBeanContainerImpl.class);

            final Set<Bean<?>> beans = beanManager.getBeans(Simple.class);
            assertEquals(1, beans.size());

            final Bean<?> bean = beans.iterator().next();
            assertEquals(
                    Simple.class,
                    bean.getBeanClass()
            );
            assertTrue(
                    bean.getInjectionPoints().isEmpty()
            );
            assertEquals(
                    Singleton.class,
                    bean.getScope()
            );
            assertEquals(
                    Simple.class,
                    bean.getTypes().iterator().next()
            );

            final Bean<?> foo1 = beanManager.getBeans(Foo1.class).iterator().next();

            final Set<Annotation> qualifiers = foo1.getQualifiers();
            assertEquals(1, qualifiers.size());
            final Named named = (Named) qualifiers.iterator().next();
            assertEquals("one", named.value());

        }
    }

    @Singleton
    static class Simple {}

    interface Foo<T extends CharSequence> {}

    @Singleton
    @Named("one")
    static class Foo1 implements Foo<String> {}

    @Singleton
    @Named("two")
    static class Foo2 implements Foo<StringBuilder> {}

}
