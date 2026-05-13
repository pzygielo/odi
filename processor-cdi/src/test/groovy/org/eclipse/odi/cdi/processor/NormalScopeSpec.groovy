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
import io.micronaut.aop.InterceptedProxy
import io.micronaut.runtime.context.scope.ScopedProxy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.RequestScoped

import jakarta.inject.Scope

class NormalScopeSpec extends AbstractTypeElementSpec {

    void 'test compile class with application scope'() {
        given:
        def definition = buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.ApplicationScoped
class Test {

}

''')
        expect:
        definition != null
        definition.getAnnotationNamesByStereotype(Scope).contains(ApplicationScoped.name)
        definition.hasStereotype(ScopedProxy)
    }

    void 'test compile class with request scope'() {
        given:
        def definition = buildBeanDefinition('appscope.Test', '''
package appscope;

import jakarta.enterprise.inject.Stereotype;
import jakarta.inject.Named;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Stereotype
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@ApplicationScoped
@Named
@interface FishStereotype {
    
}
@FishStereotype
@RequestScoped
class Test {

}

''')
        expect:
        definition != null
        definition.getAnnotationNamesByStereotype(Scope).contains(RequestScoped.name)
        definition.getAnnotationNamesByStereotype(Scope).contains(ApplicationScoped.name)
        definition.hasStereotype(ScopedProxy)
        definition.hasStereotype(ApplicationScoped)
        definition.hasStereotype(RequestScoped)
    }

    void "test built bean is a scoped proxy"() {
        given:
        def context = buildContext('''
package appscope;

@jakarta.enterprise.context.ApplicationScoped
class Test {

}

''')
        expect:
        getBean(context, 'appscope.Test') instanceof InterceptedProxy
    }

    void 'test fail compilation with normal scope final class'() {
        when:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.ApplicationScoped
final class Test {

}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Managed bean classes that are final must have @Dependent scope')
    }

    void 'test fail compilation with multiple declared scopes'() {
        when:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.ApplicationScoped
@jakarta.enterprise.context.RequestScoped
class Test {

}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Bean declares more than one scope:')
        e.message.contains('@ApplicationScoped')
        e.message.contains('@RequestScoped')
    }

    void 'test dependent final class compiles'() {
        expect:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.Dependent
final class Test {

}

''')
    }

    void 'test fail compilation with normal scoped bean constructor with parameters'() {
        when:
        buildBeanDefinition('appscope.Test', '''
package appscope;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

@jakarta.enterprise.context.RequestScoped
class Test {
    @Inject
    Test(BeanManager beanManager) {
    }
}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Managed bean classes with normal scope must declare a non-private no-arguments constructor')
    }

    void 'test fail compilation with normal scoped private constructor'() {
        when:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.RequestScoped
class Test {
    private Test() {
    }
}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Managed bean classes with normal scope must declare a non-private no-arguments constructor')
    }

    void 'test normal scoped protected no arguments constructor compiles'() {
        expect:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.RequestScoped
class Test {
    protected Test() {
    }
}

''')
    }

    void 'test fail compilation with normal scoped generic managed bean'() {
        when:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.ApplicationScoped
class Test<T> {

}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Managed bean classes with type parameters must have @Dependent scope')
    }

    void 'test dependent generic managed bean compiles'() {
        expect:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.Dependent
class Test<T> {

}

''')
    }

    void 'test fail compilation with normal scoped public field'() {
        when:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.RequestScoped
class Test {
    public String name;
}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Managed bean classes with non-static public fields must have @Dependent scope')
    }

    void 'test dependent bean with public field compiles'() {
        expect:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.Dependent
class Test {
    public String name;
}

''')
    }

    void 'test normal scoped static public field compiles'() {
        expect:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.RequestScoped
class Test {
    public static String name;
}

''')
    }

    void 'test fail compilation with normal scoped public final method'() {
        when:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.RequestScoped
class Test {
    public final String getName() {
        return null;
    }
}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Managed bean classes with non-private, non-static final methods must have @Dependent scope')
    }

    void 'test fail compilation with normal scoped protected final method'() {
        when:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.RequestScoped
class Test {
    protected final void swim() {
    }
}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Managed bean classes with non-private, non-static final methods must have @Dependent scope')
    }

    void 'test fail compilation with normal scoped package private final method'() {
        when:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.RequestScoped
class Test {
    final void swim() {
    }
}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Managed bean classes with non-private, non-static final methods must have @Dependent scope')
    }

    void 'test fail compilation with normal scoped inherited final method'() {
        when:
        buildBeanDefinition('appscope.Test', '''
package appscope;

class Base {
    public final String getName() {
        return null;
    }
}

@jakarta.enterprise.context.RequestScoped
class Test extends Base {
}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Managed bean classes with non-private, non-static final methods must have @Dependent scope')
    }

    void 'test normal scoped private final method compiles'() {
        expect:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.RequestScoped
class Test {
    private final void swim() {
    }
}

''')
    }

    void 'test normal scoped static final method compiles'() {
        expect:
        buildBeanDefinition('appscope.Test', '''
package appscope;

@jakarta.enterprise.context.RequestScoped
class Test {
    public static final String getName() {
        return null;
    }
}

''')
    }
}
