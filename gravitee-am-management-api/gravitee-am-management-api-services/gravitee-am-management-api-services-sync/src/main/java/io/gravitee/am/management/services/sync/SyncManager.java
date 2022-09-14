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

import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.service.EventService;
import io.gravitee.common.event.EventManager;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BinaryOperator;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SyncManager implements InitializingBean, Subscriber<SyncContext> {

    public static final int ONE_MINUTE = 60_000;
    private final Logger logger = LoggerFactory.getLogger(SyncManager.class);

    @Autowired
    private EventService eventService;

    @Autowired
    private EventManager eventManager;

    private final PublishProcessor<SyncContext> offsetUpdater = PublishProcessor.create();
    private final PublishProcessor<Long> synchronizer = PublishProcessor.create();
    private Subscription syncSubscription;

    private SyncContext lastSyncContext = new SyncContext();

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeSyncFlow();
    }

    private void initializeSyncFlow() {
        // listen to the synchronization events
        // this synchronizer listen event received by refresh call
        // and merge this event with the last execution state
        synchronizer
                .observeOn(Schedulers.io())
                .onBackpressureBuffer()
                .zipWith(offsetUpdater, (time, context) -> {
                    context.setNextLastRefreshAt(time);
                    return context;
                })
                .flatMap(context -> {
                    try {
                        return eventService.findByTimeFrame(context.computeStartOffset(), context.getNextLastRefreshAt())
                                .map(events -> {
                                    if (events != null && !events.isEmpty()) {
                                        logger.debug("Received {} events to synchronize", events.size());
                                        // Extract only the latest events by type and id
                                        Map<AbstractMap.SimpleEntry, Event> sortedEvents = events
                                                .stream()
                                                .collect(
                                                        toMap(
                                                                event -> new AbstractMap.SimpleEntry<>(event.getType(), event.getPayload().getId()),
                                                                event -> event, BinaryOperator.maxBy(comparing(Event::getCreatedAt)), LinkedHashMap::new));
                                        computeEvents(sortedEvents.values());
                                    }
                                    return events;
                                })
                                .map(e -> context.toNextOffset())
                                .toFlowable();
                    } catch (Exception ex) {
                        logger.error("An error has occurred during synchronization", ex);
                        return Flowable.just(context);
                    }
                })
                .onErrorResumeNext(Flowable.just(this.lastSyncContext))
                .subscribe(this);

        // provide initial value for the domain synchronization
        offsetUpdater.onNext(new SyncContext(System.currentTimeMillis(), 0));
    }

    @Override
    public void onSubscribe(Subscription s) {
        this.syncSubscription = s;
        this.syncSubscription.request(1);
    }

    @Override
    public void onNext(SyncContext context) {
        logger.debug("Events synchronization successful");
        offsetUpdater.onNext(context);
        this.lastSyncContext = context;
        this.syncSubscription.request(1);
    }

    @Override
    public void onError(Throwable error) {
        logger.error("[FATAL] An error has occurred during synchronization, management synchronization is stopped!", error);
        this.syncSubscription.request(1);
    }

    @Override
    public void onComplete() {
        logger.info("Events synchronization finalized");
    }

    public void refresh() {
        logger.debug("Refreshing sync state...");
        long nextLastRefreshAt = System.currentTimeMillis();
        if (this.lastSyncContext.getLastRefreshAt() > 0 && nextLastRefreshAt - this.lastSyncContext.getLastRefreshAt() > ONE_MINUTE) {
            logger.warn("SyncContext not updated since 60s, restart the sync flow");
            if (this.syncSubscription != null) {
                this.syncSubscription.cancel();
            }
            this.initializeSyncFlow();
        }
        this.synchronizer.onNext(nextLastRefreshAt);
    }

    private void computeEvents(Collection<Event> events) {
        events.forEach(event -> {
            logger.debug("Compute event id : {}, with type : {} and timestamp : {} and payload : {}", event.getId(), event.getType(), event.getCreatedAt(), event.getPayload());
            eventManager.publishEvent(io.gravitee.am.common.event.Event.valueOf(event.getType(), event.getPayload().getAction()), event.getPayload());
        });
    }
}
