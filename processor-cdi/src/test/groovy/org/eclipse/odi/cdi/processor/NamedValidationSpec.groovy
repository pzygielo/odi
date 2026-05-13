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

package org.eclipse.odi.cdi.processor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import spock.lang.Unroll

class NamedValidationSpec extends AbstractTypeElementSpec {


    void 'test fail compilation for invalid name'() {
        when:
        buildBeanDefinition('named.Test', '''
package named;

@jakarta.inject.Named("(£$&")
final class Test {

}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('@Named annotation specifies an invalid name')
    }

    @Unroll
    void 'test valid name #name'() {
        expect:
        buildBeanDefinition('named.Test', """
package named;

@jakarta.inject.Named("$name")
final class Test {

}

""")
        where:
        name << ["com.acme.settings", "orderManager"]
    }

    void 'test parameter injection point with implicit named fails'() {
        when:
        buildBeanDefinition('named.Test', '''
package named;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Dependent
class Test {
    @Inject
    void init(@Named Bar bar) {
    }
}

@Named
@Dependent
class Bar {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("@Named injection points that are not fields must specify a value")
    }

    void 'test parameter injection point with explicit named compiles'() {
        expect:
        buildBeanDefinition('named.Test', '''
package named;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Dependent
class Test {
    @Inject
    void init(@Named("bar") Bar bar) {
    }
}

@Named("bar")
@Dependent
class Bar {
}
''')
    }

    void 'test duplicate bean names fail with exhaustive bean classes'() {
        when:
        withBeanClasses('duplicatenamed.Cod,duplicatenamed.Sole') {
            buildBeanDefinition('duplicatenamed.Cod', '''
package duplicatenamed;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;

@Named("whitefish")
@Dependent
class Cod {
}

@Named("whitefish")
@Dependent
class Sole {
}
''')
        }

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Ambiguous bean name 'whitefish' conflicts with bean name 'whitefish'")
    }

    void 'test bean name prefix fails with exhaustive bean classes'() {
        when:
        withBeanClasses('prefixnamed.Foo,prefixnamed.FooBarBaz') {
            buildBeanDefinition('prefixnamed.Foo', '''
package prefixnamed;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;

@Named
@Dependent
class Foo {
}

@Named("foo.bar.baz")
@Dependent
class FooBarBaz {
}
''')
        }

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Ambiguous bean name 'foo' conflicts with bean name 'foo.bar.baz'")
    }

    void 'test disabled alternative bean name does not create ambiguity'() {
        expect:
        withBeanClasses('disablednamed.Cod,disablednamed.Plaice') {
            buildBeanDefinition('disablednamed.Plaice', '''
package disablednamed;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Named;

@Named("whitefish")
@Alternative
@Dependent
class Cod {
}

@Named("whitefish")
@Dependent
class Plaice {
}
''')
        }
    }

    void 'test priority alternative bean name resolves ambiguity'() {
        expect:
        withBeanClasses('prioritynamed.Salmon,prioritynamed.Sole') {
            buildBeanDefinition('prioritynamed.Salmon', '''
package prioritynamed;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Named;

@Named("fish")
@Dependent
class Salmon {
}

@Named("fish")
@Alternative
@Priority(1)
@Dependent
class Sole {
}
''')
        }
    }

    void 'test duplicate bean names do not fail without exhaustive bean classes'() {
        expect:
        buildBeanDefinition('duplicatenamednonexhaustive.Cod', '''
package duplicatenamednonexhaustive;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;

@Named("whitefish")
@Dependent
class Cod {
}

@Named("whitefish")
@Dependent
class Sole {
}
''')
    }

    private static Object withBeanClasses(String classNames, Closure<?> closure) {
        String previous = System.getProperty(CdiUtil.BEAN_CLASSES_OPTION)
        System.setProperty(CdiUtil.BEAN_CLASSES_OPTION, classNames)
        try {
            return closure.call()
        } finally {
            if (previous == null) {
                System.clearProperty(CdiUtil.BEAN_CLASSES_OPTION)
            } else {
                System.setProperty(CdiUtil.BEAN_CLASSES_OPTION, previous)
            }
        }
    }
}
