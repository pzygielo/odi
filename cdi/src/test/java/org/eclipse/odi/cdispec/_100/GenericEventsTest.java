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

package org.eclipse.odi.cdispec._100;

import org.eclipse.odi.test.junit5.OdiTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

@OdiTest
public class GenericEventsTest {

    @Test
    void testBeanContainerGenericEventNotObserved(BeanContainer beanContainer, GenericLoginService loginService) {
        loginService.reset();
        beanContainer.getEvent().fire(new GenericEvent<LoggedInData>() {
        });
        Assertions.assertFalse(loginService.isLoginInEventTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testGenericEventNotObserved(Event<Object> objectEvent, GenericLoginService loginService) {
        loginService.reset();
        objectEvent.fire(new GenericEvent<LoggedInData>() {
        });
        Assertions.assertFalse(loginService.isLoginInEventTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testInjectedGenericObjectEvent(Event<GenericEvent<LoggedInData>> objectEvent, GenericLoginService loginService) {
        loginService.reset();
        objectEvent.fire(new GenericEvent<LoggedInData>() {
        });
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testTypeLiteralSelectedEvent(Event<Object> objectEvent, GenericLoginService loginService) {
        loginService.reset();
        objectEvent.select(new TypeLiteral<GenericEvent<LoggedInData>>() {
        }).fire(new GenericEvent<LoggedInData>() {
        });
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertFalse(loginService.isLoginInEventMobileTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testSelectedAnnotationEvent(Event<GenericEvent<LoggedInData>> event, GenericLoginService loginService) {
        loginService.reset();
        event.select(new AnnotationLiteral<MobileEvent>() {
        }).fire(new GenericEvent<LoggedInData>() {
        });
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertTrue(loginService.isLoginInEventMobileTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testSelectedTypeAndAnnotationEvent(Event<GenericEvent<LoggedInData>> event, GenericLoginService loginService) {
        loginService.reset();
        event.select(new TypeLiteral<GenericEvent<LoggedInData>>() {
        }, new AnnotationLiteral<MobileEvent>() {
        }).fire(new GenericEvent<LoggedInData>() {
        });
        Assertions.assertTrue(loginService.isLoginInEventTriggered());
        Assertions.assertTrue(loginService.isLoginInEventMobileTriggered());
        Assertions.assertTrue(loginService.isAbstractInEventTriggered());
    }

    @Test
    void testInheritedParameterizedObserver(Event<ParameterizedBar<List<Integer>>> event,
                                            IntegerListParameterizedObserver integerObserver,
                                            StringListParameterizedObserver stringObserver) {
        integerObserver.reset();
        stringObserver.reset();

        event.fire(new ParameterizedBar<>());

        Assertions.assertTrue(integerObserver.isFooableObserved());
        Assertions.assertTrue(integerObserver.isFooObserved());
        Assertions.assertTrue(integerObserver.isBarObserved());

        Assertions.assertFalse(stringObserver.isFooableObserved());
        Assertions.assertFalse(stringObserver.isFooObserved());
        Assertions.assertFalse(stringObserver.isBarObserved());
    }

}

@ApplicationScoped
class GenericLoginService {

    private boolean loginInEventTriggered = false;
    private boolean loginInEventMobileTriggered = false;
    private boolean abstractInEventTriggered = false;

    void catchLoggedInEvent(@Observes GenericEvent<LoggedInData> event) {
        loginInEventTriggered = true;
    }

    void catchLoggedInMobileEvent(@Observes @MobileEvent GenericEvent<LoggedInData> event) {
        loginInEventMobileTriggered = true;
    }

    void catchAbstractInEvent(@Observes GenericEvent<?> event) {
        abstractInEventTriggered = true;
    }

    public boolean isLoginInEventTriggered() {
        return loginInEventTriggered;
    }

    public boolean isLoginInEventMobileTriggered() {
        return loginInEventMobileTriggered;
    }

    public boolean isAbstractInEventTriggered() {
        return abstractInEventTriggered;
    }

    public void reset() {
        loginInEventTriggered = false;
        loginInEventMobileTriggered = false;
        abstractInEventTriggered = false;
    }
}

class GenericEvent<T> {

}

class LoggedInData {
}

@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
@interface MobileEvent {

}

interface ParameterizedFooable<F> {
}

class ParameterizedFoo<F> implements ParameterizedFooable<F> {
}

class ParameterizedBar<B> extends ParameterizedFoo<B> {
}

abstract class AbstractParameterizedObserver<T> {

    private boolean fooableObserved;
    private boolean fooObserved;
    private boolean barObserved;

    void observeFooable(@Observes ParameterizedFooable<T> event) {
        fooableObserved = true;
    }

    void observeFoo(@Observes ParameterizedFoo<T> event) {
        fooObserved = true;
    }

    void observeBar(@Observes ParameterizedBar<T> event) {
        barObserved = true;
    }

    boolean isFooableObserved() {
        return fooableObserved;
    }

    boolean isFooObserved() {
        return fooObserved;
    }

    boolean isBarObserved() {
        return barObserved;
    }

    void reset() {
        fooableObserved = false;
        fooObserved = false;
        barObserved = false;
    }
}

@ApplicationScoped
class IntegerListParameterizedObserver extends AbstractParameterizedObserver<List<Integer>> {
}

@ApplicationScoped
class StringListParameterizedObserver extends AbstractParameterizedObserver<List<String>> {
}
