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

package org.eclipse.odi.cdispec._23._6;

import org.eclipse.odi.test.junit5.OdiTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@OdiTest
public class RepeatableQualifiersTest {

    private static final RepeatableStart.Literal A = new RepeatableStart.Literal("A");
    private static final RepeatableStart.Literal B = new RepeatableStart.Literal("B");
    private static final RepeatableStart.Literal C = new RepeatableStart.Literal("C");

    @Inject
    @Any
    Instance<RepeatableProcess> processInstance;

    @Inject
    RepeatableProcessObserver observer;

    @Test
    void resolutionWithRepeatableQualifiers() {
        assertEquals(4, processInstance.stream().count());
        assertTrue(processInstance.select(A).isResolvable());
        assertFalse(processInstance.select(B).isResolvable());
        assertTrue(processInstance.select(C).isResolvable());
        assertTrue(processInstance.select(B, C).isResolvable());

        assertEquals(1, observer.getProcessAObserved());
        assertEquals(2, observer.getProcessBObserved());
        assertEquals(1, observer.getProcessBCObserved());

        assertEquals(3, observer.getProcessAMetadata().getQualifiers().size());
        assertTrue(observer.getProcessAMetadata().getQualifiers().contains(A));

        assertEquals(4, observer.getProcessBCMetadata().getQualifiers().size());
        assertTrue(observer.getProcessBCMetadata().getQualifiers().contains(B));
        assertTrue(observer.getProcessBCMetadata().getQualifiers().contains(C));
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(RepeatableStarts.class)
    @Qualifier
    public @interface RepeatableStart {

        String value();

        final class Literal extends AnnotationLiteral<RepeatableStart> implements RepeatableStart {
            private final String value;

            Literal(String value) {
                this.value = value;
            }

            @Override
            public String value() {
                return value;
            }
        }
    }

    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RepeatableStarts {

        RepeatableStart[] value();
    }
}

@Dependent
class RepeatableProcess {
}

@Dependent
class RepeatableProcessProducer {

    @Inject
    Event<RepeatableProcess> event;

    @Produces
    @RepeatableQualifiersTest.RepeatableStart("A")
    RepeatableProcess createProcessA() {
        RepeatableProcess process = new RepeatableProcess();
        event.select(new RepeatableQualifiersTest.RepeatableStart.Literal("A")).fire(process);
        return process;
    }

    @Produces
    @RepeatableQualifiersTest.RepeatableStart("B")
    RepeatableProcess createProcessB() {
        RepeatableProcess process = new RepeatableProcess();
        event.select(new RepeatableQualifiersTest.RepeatableStart.Literal("B")).fire(process);
        return process;
    }

    @Produces
    @RepeatableQualifiersTest.RepeatableStart("B")
    @RepeatableQualifiersTest.RepeatableStart("C")
    RepeatableProcess createProcessBC() {
        RepeatableProcess process = new RepeatableProcess();
        event.select(new RepeatableQualifiersTest.RepeatableStart.Literal("B"), new RepeatableQualifiersTest.RepeatableStart.Literal("C")).fire(process);
        return process;
    }
}

@ApplicationScoped
class RepeatableProcessObserver {

    int processAObserved;
    int processBObserved;
    int processBCObserved;
    EventMetadata processAMetadata;
    EventMetadata processBCMetadata;

    void observeProcessA(@Observes @RepeatableQualifiersTest.RepeatableStart("A") RepeatableProcess process, EventMetadata metadata) {
        processAObserved++;
        processAMetadata = metadata;
    }

    void observeProcessB(@Observes @RepeatableQualifiersTest.RepeatableStart("B") RepeatableProcess process, EventMetadata metadata) {
        processBObserved++;
    }

    void observeProcessBC(@Observes @RepeatableQualifiersTest.RepeatableStart("B") @RepeatableQualifiersTest.RepeatableStart("C") RepeatableProcess process, EventMetadata metadata) {
        processBCObserved++;
        processBCMetadata = metadata;
    }

    int getProcessAObserved() {
        return processAObserved;
    }

    int getProcessBObserved() {
        return processBObserved;
    }

    int getProcessBCObserved() {
        return processBCObserved;
    }

    EventMetadata getProcessAMetadata() {
        return processAMetadata;
    }

    EventMetadata getProcessBCMetadata() {
        return processBCMetadata;
    }
}
