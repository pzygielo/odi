/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

class BeanMetadataValidationSpec extends AbstractTypeElementSpec {

    void "test bean metadata type parameter must match managed bean"() {
        when:
        buildBeanDefinition('beanmetadata.Yoghurt', '''
package beanmetadata;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;

@Dependent
class Yoghurt {
    @Inject
    Bean<Cream> bean;
}

class Cream {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean metadata type parameter must be assignable from the declaring bean type")
    }

    void "test bean metadata type parameter may match managed bean"() {
        expect:
        buildBeanDefinition('beanmetadata.Yoghurt', '''
package beanmetadata;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;

@Dependent
class Yoghurt {
    @Inject
    Bean<Yoghurt> bean;
}
''')
    }

    void "test bean metadata wildcard type parameter compiles"() {
        expect:
        buildBeanDefinition('beanmetadata.Yoghurt', '''
package beanmetadata;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;

@Dependent
class Yoghurt {
    @Inject
    Bean<?> bean;
}
''')
    }

    void "test bean metadata type parameter must match producer bean"() {
        when:
        buildBeanDefinition('beanmetadataproducer.Factory', '''
package beanmetadataproducer;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;

@Dependent
class Factory {
    @Produces
    Milk produce(Bean<Cream> bean) {
        return null;
    }
}

interface Milk {
}

class Cream {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean metadata type parameter must be assignable from the declaring bean type")
    }

    void "test bean metadata type parameter must match disposed bean"() {
        when:
        buildBeanDefinition('beanmetadatadisposer.Factory', '''
package beanmetadatadisposer;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;

@Dependent
class Factory {
    @Produces
    Milk produce() {
        return null;
    }

    void dispose(@Disposes Milk milk, Bean<Cream> bean) {
    }
}

interface Milk {
}

class Cream {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean metadata type parameter must be assignable from the declaring bean type")
    }

    void "test intercepted bean metadata can only be injected into interceptor beans"() {
        when:
        buildBeanDefinition('interceptedmetadata.Foo', '''
package interceptedmetadata;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;

@Dependent
class Foo {
    @Inject
    @Intercepted
    Bean<Foo> bean;
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("@Intercepted Bean metadata may only be injected into interceptor beans")
    }

    void "test intercepted bean metadata must use unbounded wildcard"() {
        when:
        buildBeanDefinition('interceptedmetadata.BadInterceptor', '''
package interceptedmetadata;

import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Binding
@Interceptor
class BadInterceptor {
    @Inject
    @Intercepted
    Bean<Cream> bean;

    @AroundInvoke
    Object intercept(InvocationContext context) throws Exception {
        return context.proceed();
    }
}

class Cream {
}

@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@interface Binding {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("@Intercepted Bean metadata must use Bean<?>")
    }

    void "test interceptor metadata can only be injected into interceptor beans"() {
        when:
        buildBeanDefinition('interceptormetadata.Foo', '''
package interceptormetadata;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.inject.Inject;

@Dependent
class Foo {
    @Inject
    Interceptor<Foo> interceptor;
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Interceptor metadata may only be injected into interceptor beans")
    }

    void "test interceptor metadata type parameter must match interceptor bean"() {
        when:
        buildBeanDefinition('interceptormetadata.BadInterceptor', '''
package interceptormetadata;

import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Binding
@jakarta.interceptor.Interceptor
class BadInterceptor {
    @Inject
    Interceptor<Cream> interceptor;

    @AroundInvoke
    Object intercept(InvocationContext context) throws Exception {
        return context.proceed();
    }
}

class Cream {
}

@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@interface Binding {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Interceptor metadata type parameter must be assignable from the interceptor bean type")
    }

    void "test interceptor and intercepted bean metadata compile in interceptor bean"() {
        expect:
        buildBeanDefinition('interceptormetadata.GoodInterceptor', '''
package interceptormetadata;

import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.InterceptorBinding;
import jakarta.interceptor.InvocationContext;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Binding
@jakarta.interceptor.Interceptor
class GoodInterceptor {
    @Inject
    Bean<GoodInterceptor> bean;

    @Inject
    Interceptor<GoodInterceptor> interceptor;

    @Inject
    @Intercepted
    Bean<?> interceptedBean;

    @AroundInvoke
    Object intercept(InvocationContext context) throws Exception {
        return context.proceed();
    }
}

@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@interface Binding {
}
''')
    }
}
