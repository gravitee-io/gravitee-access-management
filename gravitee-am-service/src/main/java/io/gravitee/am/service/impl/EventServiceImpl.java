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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.EventRepository;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class EventServiceImpl implements EventService {

    private final Logger LOGGER = LoggerFactory.getLogger(EventServiceImpl.class);

    @Autowired
    private EventRepository eventRepository;

    @Override
    public Single<Event> create(Event event) {
        LOGGER.debug("Create a new event {}", event);

        event.setCreatedAt(new Date());
        event.setUpdatedAt(event.getCreatedAt());
        return eventRepository.create(event)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create an event", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create an event", ex));
                });
    }

    @Override
    public Single<List<Event>> findByTimeFrame(long from, long to) {
        LOGGER.debug("Find events with time frame {} and {}", from, to);
        return eventRepository.findByTimeFrame(from, to)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to find events by time frame", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find events by time frame", ex));
                });
    }
}
