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

import io.gravitee.am.management.core.event.DomainEvent;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.exception.OrganizationNotFoundException;
import io.gravitee.common.event.EventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.function.BinaryOperator;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SyncManager {

    private final Logger logger = LoggerFactory.getLogger(SyncManager.class);

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private EventService eventService;

    @Autowired
    private EventManager eventManager;

    private Organization deployedDefaultOrganization;

    private long lastRefreshAt = System.currentTimeMillis();

    private long lastDelay = 0;

    public void refresh() {
        logger.debug("Refreshing sync state...");

        try {
            if (deployedDefaultOrganization == null) {
                logger.debug("Initial synchronization");
                deployOrganizations();
            }

            processEvents();
        } catch (Exception ex) {
            logger.error("An error occurs while synchronizing organizations", ex);
        }
    }

    private void deployOrganizations() {
        // For now only default organization is used.
        try {
            deployedDefaultOrganization = organizationService.findById(Organization.DEFAULT).blockingGet();
            if (deployedDefaultOrganization != null) {
                eventManager.publishEvent(DomainEvent.DEPLOY, deployedDefaultOrganization);
            }
        } catch (OrganizationNotFoundException onfe) {
            // There is no DEFAULT organization yet. Just wait the next try.
        }
    }


    private void processEvents() {

        if (deployedDefaultOrganization == null) {
            // Default organization not yet deployed, skip processing events for now.
            return;
        }

        long nextLastRefreshAt = System.currentTimeMillis();

        // search for events and compute them
        logger.debug("Events synchronization");
        List<Event> events = eventService.findByTimeFrame(lastRefreshAt - lastDelay, nextLastRefreshAt).blockingGet();

        if (events != null && !events.isEmpty()) {
            // Extract only the latest events by type and id
            Map<AbstractMap.SimpleEntry, Event> sortedEvents = events
                    .stream()
                    .collect(
                            toMap(
                                    event -> new AbstractMap.SimpleEntry<>(event.getType(), event.getPayload().getId()),
                                    event -> event, BinaryOperator.maxBy(comparing(Event::getCreatedAt)), LinkedHashMap::new));
            computeEvents(sortedEvents.values());
        }

        lastRefreshAt = nextLastRefreshAt;
        lastDelay = System.currentTimeMillis() - nextLastRefreshAt;

    }

    private void computeEvents(Collection<Event> events) {
        events.forEach(event -> {
            logger.debug("Compute event id : {}, with type : {} and timestamp : {} and payload : {}", event.getId(), event.getType(), event.getCreatedAt(), event.getPayload());
            eventManager.publishEvent(io.gravitee.am.common.event.Event.valueOf(event.getType(), event.getPayload().getAction()), event.getPayload());
        });
    }
}