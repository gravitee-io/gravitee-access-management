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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EventRepositoryTest extends AbstractManagementTest {

    @Autowired
    private EventRepository eventRepository;

    @Test
    public void testFindByTimeFrame() throws TechnicalException {
        final long from = 1571214259000l;
        final long to =  1571214281000l;
        // create event
        Event event = new Event();
        event.setType(Type.DOMAIN);
        event.setCreatedAt(new Date(from));
        event.setUpdatedAt(event.getCreatedAt());
        Event expectedEvent = eventRepository.create(event).blockingGet();

        // create event before the from date
        Event eventBefore = new Event();
        eventBefore.setType(Type.DOMAIN);
        eventBefore.setCreatedAt(new Date(Instant.ofEpochMilli(from).minusSeconds(30).getEpochSecond()));
        eventBefore.setUpdatedAt(eventBefore.getCreatedAt());
        eventRepository.create(eventBefore).blockingGet();

        // create event after the to date
        Event eventAfter = new Event();
        eventAfter.setType(Type.DOMAIN);
        eventAfter.setCreatedAt(new Date(Instant.ofEpochMilli(to).plusSeconds(30).getEpochSecond()));
        eventAfter.setUpdatedAt(eventAfter.getCreatedAt());
        eventRepository.create(eventAfter).blockingGet();

        // fetch events
        TestObserver<List<Event>> testObserver1 = eventRepository.findByTimeFrame(from, to).test();
        testObserver1.awaitTerminalEvent();

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValue(events -> events.size() == 1);
        testObserver1.assertValue(events -> events.get(0).getId().equals(expectedEvent.getId()));
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create event
        Event event = new Event();
        event.setType(Type.DOMAIN);
        Payload payload = new Payload("pid", ReferenceType.ORGANIZATION, "oid", Action.BULK_CREATE);
        event.setPayload(new Payload(payload)); // duplicate the payload to avoid inner transformation that make test failing

        Event eventCreated = eventRepository.create(event).blockingGet();

        // fetch domain
        TestObserver<Event> testObserver = eventRepository.findById(eventCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getId().equals(eventCreated.getId()));
        testObserver.assertValue(e -> e.getType().equals(Type.DOMAIN));
        testObserver.assertValue(e -> e.getPayload().keySet().size() == event.getPayload().keySet().size());
        testObserver.assertValue(e -> e.getPayload().keySet().containsAll(event.getPayload().keySet()));
        testObserver.assertValue(e -> e.getPayload().getId().equals(payload.getId()));
        testObserver.assertValue(e -> e.getPayload().getAction().equals(payload.getAction()));
        testObserver.assertValue(e -> e.getPayload().getReferenceId().equals(payload.getReferenceId()));
        testObserver.assertValue(e -> e.getPayload().getReferenceType().equals(payload.getReferenceType()));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        eventRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        // create event
        Event event = new Event();
        event.setType(Type.DOMAIN);
        Payload payload = new Payload("idx", ReferenceType.DOMAIN, "domainid", Action.CREATE);
        event.setPayload(payload);

        TestObserver<Event> testObserver = eventRepository.create(event).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getType().equals(Type.DOMAIN));
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create event
        Event event = new Event();
        event.setType(Type.DOMAIN);
        Event eventCreated = eventRepository.create(event).blockingGet();

        // fetch event
        TestObserver<Event> testObserver = eventRepository.findById(eventCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getType().equals(Type.DOMAIN));

        // delete event
        TestObserver testObserver1 = eventRepository.delete(eventCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch event
        eventRepository.findById(eventCreated.getId()).test().assertEmpty();
    }
}
