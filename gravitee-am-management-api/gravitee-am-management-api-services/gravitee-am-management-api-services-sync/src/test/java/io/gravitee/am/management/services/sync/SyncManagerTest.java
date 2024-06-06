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
package io.gravitee.am.management.services.sync;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.service.EventService;
import io.gravitee.common.event.EventManager;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class SyncManagerTest {

    @Mock
    private EventService eventService;

    @Mock
    private EventManager eventManager;

    @InjectMocks
    private SyncManager cut;

    @BeforeEach
    public void init() {
        cut.afterPropertiesSet();
    }

    @Test
    void should_publish_valid_event() {
        var event = new Event(Type.APPLICATION, new Payload(Map.of("action", Action.CREATE)));
        event.setId(UUID.randomUUID().toString());
        when(eventService.findByTimeFrame(anyLong(), anyLong())).thenReturn(Single.just(List.of(event)));
        cut.refresh();
        verify(eventManager, times(1)).publishEvent(any(), any());
    }

    @Test
    void should_not_publish_invalid_event() {
        var event = new Event(Type.USER, new Payload(Map.of("action", Action.CREATE)));
        event.setId(UUID.randomUUID().toString());
        when(eventService.findByTimeFrame(anyLong(), anyLong())).thenReturn(Single.just(List.of(event)));
        cut.refresh();
        verify(eventManager, never()).publishEvent(any(), any());
    }

    @Test
    void shouldPublishDistinctEvent() {
        var event = new Event(Type.APPLICATION, new Payload(Map.of("action", Action.CREATE)));
        event.setId(UUID.randomUUID().toString());
        when(eventService.findByTimeFrame(anyLong(), anyLong())).thenReturn(Single.just(List.of(event)));
        cut.refresh();
        cut.refresh();
        verify(eventManager, times(1)).publishEvent(any(), any());
    }

}
