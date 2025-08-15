/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.repository.management.api;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.jdbc.management.api.JdbcEventRepository;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author GraviteeSource Team
 */
public class EventRepositoryPurgeTest extends AbstractManagementTest {

    @Autowired
    private JdbcEventRepository eventRepository;

    @Test
    public void shouldPurge() {
        Instant now = Instant.now();

        Event recentEvent = new Event();
        recentEvent.setId("recent-event");
        recentEvent.setType(Type.DOMAIN);
        recentEvent.setPayload(new Payload(Map.of("content", "recent event", "action", Action.CREATE)));
        recentEvent.setCreatedAt(new Date(now.toEpochMilli()));

        Event oldEvent = new Event();
        oldEvent.setId("old-event");
        oldEvent.setType(Type.DOMAIN);
        oldEvent.setPayload(new Payload(Map.of("content", "old event", "action", Action.CREATE)));
        oldEvent.setCreatedAt(new Date(now.minus(91, ChronoUnit.DAYS).toEpochMilli()));

        TestObserver<Void> test = eventRepository.create(recentEvent).ignoreElement()
                .andThen(eventRepository.create(oldEvent).ignoreElement())
                .test();
        test.awaitDone(10, TimeUnit.SECONDS);
        test.assertNoErrors();

        assertNotNull(eventRepository.findById("recent-event").blockingGet());
        assertNotNull(eventRepository.findById("old-event").blockingGet());

        TestObserver<Void> testPurge = eventRepository.purgeExpiredData().test();
        testPurge.awaitDone(10, TimeUnit.SECONDS);
        testPurge.assertNoErrors();

        assertNotNull(eventRepository.findById("recent-event").blockingGet());
        assertNull(eventRepository.findById("old-event").blockingGet());
    }
} 