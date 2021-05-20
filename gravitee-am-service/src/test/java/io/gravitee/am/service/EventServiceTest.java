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
package io.gravitee.am.service;

import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.EventRepository;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.EventServiceImpl;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class EventServiceTest {

    @InjectMocks
    private EventService eventService = new EventServiceImpl();

    @Mock
    private EventRepository eventRepository;

    @Test
    public void shouldFindByTimeFrame() {
        when(eventRepository.findByTimeFrame(0, 1)).thenReturn(Flowable.just(new Event()));
        TestObserver testObserver = eventService.findByTimeFrame(0, 1).test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindByTimeFrame_technicalException() {
        when(eventRepository.findByTimeFrame(0, 1)).thenReturn(Flowable.error(TechnicalException::new));
        TestObserver testObserver = eventService.findByTimeFrame(0, 1).test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        Event newEvent = Mockito.mock(Event.class);
        when(eventRepository.create(any(Event.class))).thenReturn(Single.just(newEvent));

        TestObserver testObserver = eventService.create(newEvent).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(eventRepository, times(1)).create(any(Event.class));
    }

    @Test
    public void shouldNotCreate_technicalException() {
        Event newEvent = Mockito.mock(Event.class);
        when(eventRepository.create(any(Event.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = eventService.create(newEvent).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }
}
