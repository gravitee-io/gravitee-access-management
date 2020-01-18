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
package io.gravitee.am.repository.mongodb.management;

import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.EventRepository;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoEventRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    @Override
    public String collectionName() {
        return "events";
    }

    @Test
    public void testFindByTimeFrame() throws TechnicalException {
        final long from = 1571214259000l;
        final long to =  1571214281000l;
        // create event
        Event event = new Event();
        event.setType(Type.DOMAIN);
        event.setCreatedAt(new Date(from));
        event.setUpdatedAt(event.getCreatedAt());
        eventRepository.create(event).blockingGet();

        // fetch events
        TestObserver<List<Event>> testObserver1 = eventRepository.findByTimeFrame(from, to).test();
        testObserver1.awaitTerminalEvent();

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValue(events -> events.size() == 1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create event
        Event event = new Event();
        event.setType(Type.DOMAIN);
        Event eventCreated = eventRepository.create(event).blockingGet();

        // fetch domain
        TestObserver<Event> testObserver = eventRepository.findById(eventCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getType().equals(Type.DOMAIN));
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
