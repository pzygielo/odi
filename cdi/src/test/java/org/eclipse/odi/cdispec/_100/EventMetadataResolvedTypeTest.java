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

package org.eclipse.odi.cdispec._100;

import org.eclipse.odi.test.junit5.OdiTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

@OdiTest
public class EventMetadataResolvedTypeTest {

    @Test
    void metadataTypeUsesResolvedRuntimeEventType(
            ParameterizedEventSource source,
            ParameterizedEventObserver observer) {
        source.fire();

        Assertions.assertEquals(
                new TypeLiteral<ArrayList<ParameterizedEventPayload<Number>>>() {
                }.getType(),
                observer.metadata.getType()
        );
    }
}

@ApplicationScoped
class ParameterizedEventSource {

    @Inject
    @Any
    Event<List<ParameterizedEventPayload<Number>>> event;

    void fire() {
        event.fire(new ArrayList<ParameterizedEventPayload<Number>>());
    }
}

@ApplicationScoped
class ParameterizedEventObserver {

    EventMetadata metadata;

    void observe(@Observes List<ParameterizedEventPayload<?>> event, EventMetadata metadata) {
        this.metadata = metadata;
    }
}

class ParameterizedEventPayload<T> {
}
