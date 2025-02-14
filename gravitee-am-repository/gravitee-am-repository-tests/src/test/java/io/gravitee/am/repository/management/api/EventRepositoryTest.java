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
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EventRepositoryTest extends AbstractManagementTest {

    @Autowired
    private EventRepository eventRepository;

    @Test
    public void testFindByTimeFrame() {
        final long from = 1571214259000L;
        final long to = 1571214281000L;
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
        TestSubscriber<Event> testSubscriber = eventRepository.findByTimeFrame(from, to).test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(evt -> evt.getId().equals(expectedEvent.getId()));
    }

    @Test
    public void testFindByTimeFrameWithDataPlaneId() {
        final long from = 1571214259000L;
        final long to = 1571214281000L;
        // create event for default DP
        Event event = new Event();
        event.setType(Type.DOMAIN);
        event.setCreatedAt(new Date(from));
        event.setUpdatedAt(event.getCreatedAt());
        event.setDataPlaneId("default");
        event.setEnvironmentId("envId");
        Event expectedEvent = eventRepository.create(event).blockingGet();

        // create event for default123 DP
        Event event123 = new Event();
        event123.setType(Type.DOMAIN);
        event123.setCreatedAt(new Date(from));
        event123.setUpdatedAt(event.getCreatedAt());
        event123.setDataPlaneId("default123");
        event123.setEnvironmentId("envId123");
        eventRepository.create(event123).blockingGet();

        // fetch events
        TestSubscriber<Event> testSubscriber = eventRepository.findByTimeFrameAndDataPlaneId(from, to, "default").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(evt -> evt.getId().equals(expectedEvent.getId())
                && evt.getDataPlaneId().equals(expectedEvent.getDataPlaneId())
                && evt.getEnvironmentId().equals(expectedEvent.getEnvironmentId()));
    }

    @Test
    public void testFindByTimeFrameWithDataPlaneId_withNullDataPlaneId() {
        final long from = 1571214259000L;
        final long to = 1571214281000L;
        // create event for default DP
        Event event = new Event();
        event.setType(Type.DOMAIN);
        event.setCreatedAt(new Date(from));
        event.setUpdatedAt(event.getCreatedAt());
        event.setDataPlaneId("default");
        eventRepository.create(event).blockingGet();

        // create event for default123 DP
        Event event123 = new Event();
        event123.setType(Type.DOMAIN);
        event123.setCreatedAt(new Date(from));
        event123.setUpdatedAt(event.getCreatedAt());
        event123.setDataPlaneId("default123");
        eventRepository.create(event123).blockingGet();

        // create event for default123 DP
        Event eventNull = new Event();
        eventNull.setType(Type.MEMBERSHIP);
        eventNull.setCreatedAt(new Date(from));
        eventNull.setUpdatedAt(event.getCreatedAt());
        eventRepository.create(eventNull).blockingGet();

        // fetch events
        TestSubscriber<Event> testSubscriber = eventRepository.findByTimeFrameAndDataPlaneId(from, to, "default").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(2);
    }

    @Test
    public void testFindById() {
        // create event
        Event event = new Event();
        event.setType(Type.DOMAIN);
        Payload payload = new Payload("pid", ReferenceType.ORGANIZATION, "oid", Action.BULK_CREATE);
        event.setPayload(new Payload(payload)); // duplicate the payload to avoid inner transformation that make test failing

        Event eventCreated = eventRepository.create(event).blockingGet();

        // fetch domain
        TestObserver<Event> testObserver = eventRepository.findById(eventCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
    public void testNotFoundById() throws Exception {
        var observer = eventRepository.findById("test").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void testCreate() {
        // create event
        Event event = new Event();
        event.setType(Type.DOMAIN);
        Payload payload = new Payload("idx", ReferenceType.DOMAIN, "domainid", Action.CREATE);
        event.setPayload(payload);

        TestObserver<Event> testObserver = eventRepository.create(event).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getType().equals(Type.DOMAIN));
    }

    @Test
    public void testDelete() throws Exception {
        // create event
        Event event = new Event();
        event.setType(Type.DOMAIN);
        Event eventCreated = eventRepository.create(event).blockingGet();

        // fetch event
        TestObserver<Event> testObserver = eventRepository.findById(eventCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getType().equals(Type.DOMAIN));

        // delete event
        TestObserver testObserver1 = eventRepository.delete(eventCreated.getId()).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch event
        var observer = eventRepository.findById(eventCreated.getId()).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }
}
