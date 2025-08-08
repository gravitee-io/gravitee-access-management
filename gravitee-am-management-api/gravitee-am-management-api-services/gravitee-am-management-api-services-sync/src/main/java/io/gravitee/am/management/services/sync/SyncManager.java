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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.EventKey;
import io.gravitee.am.monitoring.metrics.Constants;
import io.gravitee.am.monitoring.metrics.GaugeHelper;
import io.gravitee.am.service.EventService;
import io.gravitee.common.event.EventManager;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;

import static io.gravitee.am.common.event.Event.valueOf;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SyncManager implements InitializingBean {
    public static final int TIMEFRAME_BEFORE_DELAY = 30000;
    public static final int TIMEFRAME_AFTER_DELAY = 30000;

    private final Logger logger = LoggerFactory.getLogger(SyncManager.class);

    @Autowired
    private EventService eventService;

    @Autowired
    private EventManager eventManager;

    @Value("${services.sync.timeframeBeforeDelay:" + TIMEFRAME_BEFORE_DELAY + "}")
    private long timeframeBeforeDelay = TIMEFRAME_BEFORE_DELAY;

    @Value("${services.sync.timeframeAfterDelay:" + TIMEFRAME_AFTER_DELAY + "}")
    private long timeframeAfterDelay = TIMEFRAME_AFTER_DELAY;

    private long lastRefreshAt = System.currentTimeMillis();

    private long lastDelay = 0;

    private final GaugeHelper eventsGauge = new GaugeHelper(Constants.METRICS_EVENTS_SYNC);

    @Value("${services.sync.eventsTimeOutMillis:30000}")
    private long eventsTimeOut = 30000;

    private Cache<String, String> processedEventIds;

    @Override
    public void afterPropertiesSet() {
        this.processedEventIds = CacheBuilder.newBuilder()
                .expireAfterWrite(timeframeBeforeDelay + timeframeAfterDelay, TimeUnit.MILLISECONDS)
                .build();
    }

    public void refresh() {
        logger.debug("Refreshing sync state...");
        try {
            processEvents();
        } catch (Exception ex) {
            logger.error("An error occurs while synchronizing organizations", ex);
        }
    }

    private void processEvents() {
        long nextLastRefreshAt = System.currentTimeMillis();
        // search for events and compute them
        logger.debug("Events synchronization");
        final long from = (lastRefreshAt - lastDelay) - timeframeBeforeDelay;
        final long to = nextLastRefreshAt + timeframeAfterDelay;
        Single<List<Event>> eventsProcessing = eventService.findByTimeFrame(from, to);
        if (eventsTimeOut > 0) {
            eventsProcessing = eventsProcessing.timeout(eventsTimeOut, TimeUnit.MILLISECONDS);
        }
        List<Event> events = eventsProcessing.blockingGet();
        if (!events.isEmpty()) {
            eventsGauge.updateValue(events.size());
            // Extract only the latest events by type and id
            var sortedEvents = events
                    .stream()
                    .collect(
                            toMap(
                                    EventKey::new,
                                    event -> event,
                                    BinaryOperator.maxBy(comparing(Event::getCreatedAt)),
                                    LinkedHashMap::new));
            computeEvents(sortedEvents.values());
        } else {
            eventsGauge.updateValue(0);
        }
        lastRefreshAt = nextLastRefreshAt;
        lastDelay = System.currentTimeMillis() - nextLastRefreshAt;
    }

    private void computeEvents(Collection<Event> events) {
        events.forEach(event -> {
            var payload = processedEventIds.getIfPresent(event.getId());
            if (payload == null || !event.getPayload().toString().equals(payload)) {
                logger.debug("Compute event id : {}, with type : {} and timestamp : {} and payload : {}", event.getId(), event.getType(), event.getCreatedAt(), event.getPayload());
                final var commonEvent = valueOf(event.getType(), event.getPayload().getAction());
                if (commonEvent == null) {
                    logger.debug("Cannot publish event {} as type is null", event.getId());
                } else {
                    eventManager.publishEvent(commonEvent, event.getPayload());
                }
                processedEventIds.put(event.getId(), event.getPayload().toString());
            } else {
                logger.debug("Event id {} already processed", event.getId());
            }
        });
    }
}
