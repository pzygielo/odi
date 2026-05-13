/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package org.eclipse.odi.cdispec._23;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.eclipse.odi.test.junit5.OdiTest;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@OdiTest
public class DefaultQualifierInjectionPointTest {

    @Inject
    DefaultQualifiedEventSource source;

    @Inject
    DefaultQualifiedEventObserver observer;

    @Test
    void injectionPointQualifierMatchesLiteralWithDefaultMember(BeanContainer beanContainer) {
        Bean<?> bean = beanContainer.getBeans(DefaultQualifiedEventHolder.class).iterator().next();
        InjectionPoint injectionPoint = bean.getInjectionPoints()
                .stream()
                .filter(candidate -> candidate.getMember().getName().equals("event"))
                .findFirst()
                .orElseThrow();

        assertEquals(1, injectionPoint.getQualifiers().size());
        assertTrue(injectionPoint.getQualifiers().contains(new DefaultQualifierInjectionPointTest.DefaultMemberQualifier.Literal() {
        }));
    }

    @Test
    void eventQualifierMatchesObserverQualifierWithDefaultMember() {
        source.fire("observed");

        assertEquals(1, observer.getObserved());
    }

    @Target({TYPE, METHOD, PARAMETER, FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    public @interface DefaultMemberQualifier {
        String value() default "";

        class Literal extends AnnotationLiteral<DefaultMemberQualifier> implements DefaultMemberQualifier {
            @Override
            public String value() {
                return "";
            }
        }
    }
}

@Dependent
class DefaultQualifiedEventHolder {
    @Inject
    @DefaultQualifierInjectionPointTest.DefaultMemberQualifier
    Event<Object> event;
}

@Dependent
class DefaultQualifiedEventSource {
    @Inject
    @DefaultQualifierInjectionPointTest.DefaultMemberQualifier
    Event<String> event;

    void fire(String value) {
        event.fire(value);
    }
}

@ApplicationScoped
class DefaultQualifiedEventObserver {
    int observed;

    void observe(@Observes @DefaultQualifierInjectionPointTest.DefaultMemberQualifier String value) {
        observed++;
    }

    int getObserved() {
        return observed;
    }
}
