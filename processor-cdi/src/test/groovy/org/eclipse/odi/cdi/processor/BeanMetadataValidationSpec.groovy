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
}
