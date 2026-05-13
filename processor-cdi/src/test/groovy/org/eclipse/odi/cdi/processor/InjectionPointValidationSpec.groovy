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

package org.eclipse.odi.cdi.processor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class InjectionPointValidationSpec extends AbstractTypeElementSpec {

    void "test raw generic injection point with multiple CDI assignable beans fails"() {
        when:
        buildBeanDefinition('rawambiguous.Consumer', '''
package rawambiguous;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
class Consumer {
    @Inject
    Dao dao;
}

@Dependent
class Dao<T1, T2> {
}

@Dependent
class ObjectDao extends Dao<Object, Object> {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Ambiguous dependency for injection point of type rawambiguous.Dao")
        e.message.contains("rawambiguous.Dao")
        e.message.contains("rawambiguous.ObjectDao")
    }

    void "test raw generic injection point with single CDI assignable bean compiles"() {
        expect:
        buildBeanDefinition('rawsingle.Consumer', '''
package rawsingle;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
class Consumer {
    @Inject
    Dao dao;
}

@Dependent
class Dao<T1, T2> {
}
''')
    }

    void "test raw event injection point fails"() {
        when:
        buildBeanDefinition('rawevent.Consumer', '''
package rawevent;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@Dependent
class Consumer {
    @SuppressWarnings("rawtypes")
    @Inject
    Event event;
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("jakarta.enterprise.event.Event must have a required type parameter specified")
    }

    void "test raw instance injection point fails"() {
        when:
        buildBeanDefinition('rawinstance.Consumer', '''
package rawinstance;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@Dependent
class Consumer {
    @SuppressWarnings("rawtypes")
    @Inject
    Consumer(Instance instance) {
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("jakarta.enterprise.inject.Instance must have a required type parameter specified")
    }

    void "test parameterized event and instance injection points compile"() {
        expect:
        buildBeanDefinition('typedbuiltin.Consumer', '''
package typedbuiltin;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@Dependent
class Consumer {
    @Inject
    Event<Foo> event;

    @Inject
    Instance<Foo> instance;
}

@Dependent
class Foo {
}
''')
    }

    void "test type variable injection point fails"() {
        when:
        buildBeanDefinition('typevariable.Consumer', '''
package typevariable;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
class Consumer {
    @Inject
    <T extends Animal> void setAnimal(T animal) {
    }
}

interface Animal {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Injection point type must not be a type variable")
    }

    void "test parameterized type variable injection point compiles"() {
        expect:
        buildBeanDefinition('typevariableparam.Consumer', '''
package typevariableparam;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

@Dependent
class Consumer<T extends Animal> {
    @Inject
    Box<T> animal;
}

@Dependent
class Box<T extends Animal> {
}

interface Animal {
}
''')
    }

    void "test type variable observer event parameter compiles"() {
        expect:
        buildBeanDefinition('typevariableobserver.Consumer', '''
package typevariableobserver;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;

@Dependent
class Consumer {
    <T extends Animal> void observe(@Observes T animal) {
    }
}

interface Animal {
}
''')
    }

    void "test type variable injection point on vetoed superclass compiles"() {
        expect:
        buildBeanDefinition('typevariablevetoed.Consumer', '''
package typevariablevetoed;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Vetoed;
import jakarta.inject.Inject;

@Dependent
class Consumer extends Base<Animal> {
}

@Vetoed
class Base<T extends Animal> {
    @Inject
    T animal;
}

interface Animal {
}
''')
    }

    void "test injection point metadata in normal scoped bean fails"() {
        when:
        buildBeanDefinition('metadataip.Cat', '''
package metadataip;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

@RequestScoped
class Cat {
    @Inject
    InjectionPoint injectionPoint;
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("InjectionPoint metadata may only be injected into @Dependent beans")
    }

    void "test injection point metadata in dependent bean compiles"() {
        expect:
        buildBeanDefinition('dependentmetadataip.Cat', '''
package dependentmetadataip;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

@Dependent
class Cat {
    @Inject
    InjectionPoint fieldInjectionPoint;

    @Inject
    Cat(InjectionPoint constructorInjectionPoint) {
    }

    @Inject
    void init(InjectionPoint methodInjectionPoint) {
    }
}
''')
    }

    void "test injection point metadata in dependent producer method compiles"() {
        expect:
        buildBeanDefinition('producermetadataip.Factory', '''
package producermetadataip;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;

@Dependent
class Factory {
    @Produces
    @Dependent
    Product produce(InjectionPoint injectionPoint) {
        return new Product();
    }
}

class Product {
}
''')
    }
}
