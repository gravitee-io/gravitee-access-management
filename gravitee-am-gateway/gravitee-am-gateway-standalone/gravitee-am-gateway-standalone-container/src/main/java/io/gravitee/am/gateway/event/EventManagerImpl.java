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
package io.gravitee.am.gateway.event;

import com.google.common.collect.Lists;
import io.gravitee.am.common.event.DomainEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.ReporterEvent;
import io.gravitee.am.gateway.handler.common.auth.AuthenticationDetails;
import io.gravitee.am.gateway.handler.common.service.DomainAwareListener;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.impl.SimpleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * Override default event manager to enable concurrent access
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EventManagerImpl implements EventManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(io.gravitee.common.event.impl.EventManagerImpl.class);

    private ConcurrentMap<ComparableEventType, List<EventListenerWrapper>> listenersMap = new ConcurrentHashMap<>();

    @Override
    public void publishEvent(Enum type, Object content) {
        this.publishEvent(new SimpleEvent(type, content));
    }

    @Override
    public void publishEvent(Event event) {
        if(event == null){
            LOGGER.debug("Cannot publish event with event null");
            return;
        }

        if(event.type() == null){
            LOGGER.debug("Cannot publish event with event.type() null");
            return;
        }

        LOGGER.debug("Publish event {} - {}", event.type(), event.content());

        String domain = null;
        if (event.content() != null) {
            Object content = event.content();
            if (content instanceof Payload && ((Payload)content).getReferenceType() == ReferenceType.DOMAIN) {
                domain = ((Payload)content).getReferenceId();
            } else if (content instanceof Domain) {
                domain = ((Domain)content).getId();
            } else if (content instanceof AuthenticationDetails && ((AuthenticationDetails)content).getDomain() != null) {
                domain = ((AuthenticationDetails)content).getDomain().getId();
            }
        }
        List<EventListenerWrapper> listeners = getEventListeners(event.type().getClass(), domain);
        List<EventListenerWrapper> safeConcurrentListeners = Lists.newArrayList(listeners.iterator());

        if (isEmpty(safeConcurrentListeners)) {
            LOGGER.warn("Event received but no listeners available (Domain: {}, contentClass: {}, eventType: {})",
                    domain,
                    ofNullable(event.content()).map(obj -> obj.getClass().getSimpleName()).orElse("null content"),
                    event.type());
        }

        for(EventListenerWrapper listener : safeConcurrentListeners) {
            listener.eventListener().onEvent(event);
        }
    }

    @Override
    public <T extends Enum> void subscribeForEvents(EventListener<T, ?> eventListener, T... events) {
        for( T event : events) {
            addEventListener(eventListener, (Class<T>) event.getClass(), Arrays.asList(events), null);
        }
    }

    @Override
    public <T extends Enum> void subscribeForEvents(EventListener<T, ?> eventListener, Class<T> events) {
        addEventListener(eventListener, events, EnumSet.allOf(events), null);
    }

    @Override
    public <T extends Enum> void subscribeForEvents(EventListener<T, ?> eventListener, Class<T> events, String domain) {
        addEventListener(eventListener, events, EnumSet.allOf(events), domain);
    }

    @Override
    public <T extends Enum> void unsubscribeForEvents(EventListener<T, ?> eventListener, Class<T> eventType, String domainId) {
        if (eventType.equals(DomainEvent.class) || eventType.equals(ReporterEvent.class)) {
            unsubscribeForEvents(eventType, domainId);
        } else {
            this.listenersMap.remove(new ComparableEventType(eventType, domainId));
        }
    }

    private <T extends Enum> void unsubscribeForEvents(Class<T> eventType, String domainId) {
        getEventListeners(eventType, null)
                .removeIf(wrapper -> {
                    if (wrapper.eventListener() instanceof DomainAwareListener del) {
                        return domainId.equals(del.getDomainId());
                    } else {
                        return false;
                    }
                });
    }

    @Override
    public <T extends Enum> void unsubscribeForCrossEvents(EventListener<T, ?> eventListener, Class<T> events, String domain) {
        List<EventListenerWrapper> listeners = this.listenersMap.get(new ComparableEventType(events, domain));
        if (listeners != null) {
            List<EventListenerWrapper> filteredList = listeners
                    .stream()
                    .filter(listenerWrapper -> !eventListener.equals(listenerWrapper.eventListener()))
                    .collect(Collectors.toList());
            this.listenersMap.put(new ComparableEventType(events, domain), filteredList);
        }
    }

    private <T extends Enum> void addEventListener(EventListener<T, ?> eventListener, Class<T> enumClass, Collection<T> events, String domain) {
        LOGGER.info("Register new listener {} for event type {}", eventListener.getClass().getSimpleName(), enumClass);

        List<EventListenerWrapper> listeners = getEventListeners(enumClass, domain);
        listeners.add(new EventListenerWrapper(eventListener, events));
    }

    private <T extends Enum> List<EventListenerWrapper> getEventListeners(Class<T> eventType, String domain) {
        ComparableEventType key = eventType.equals(DomainEvent.class) || eventType.equals(ReporterEvent.class) ?
                new ComparableEventType(eventType, null) :
                new ComparableEventType(eventType, domain) ;
        List<EventListenerWrapper> listeners = this.listenersMap.get(key);

        if (listeners == null) {
            listeners = Collections.synchronizedList(new ArrayList<>());
            this.listenersMap.put(new ComparableEventType(eventType, domain), listeners);
        }

        return listeners;
    }

    private class EventListenerWrapper<T extends Enum> {
        private final EventListener<T, ?> eventListener;
        private final Set<T> events;

        public EventListenerWrapper(EventListener<T, ?> eventListener, Collection<T> events) {
            this.eventListener = eventListener;
            this.events = new HashSet(events);
        }

        public EventListener<T, ?> eventListener() {
            return eventListener;
        }

        public Set<T> events() {
            return events;
        }
    }

    private class ComparableEventType<T> implements Comparable<ComparableEventType<T>> {

        private static final int HASH = 7 * 89;
        private final Class<? extends T> wrappedClass;
        private final String domain;

        public ComparableEventType(Class<? extends T> wrappedClass, String domain) {
            this.wrappedClass = wrappedClass;
            this.domain = domain;
        }

        @Override
        public int compareTo(ComparableEventType<T> o) {
            if (domain != null) {
                return (wrappedClass.getCanonicalName() + domain).compareTo(o.wrappedClass.getCanonicalName() + o.domain);
            }

            return wrappedClass.getCanonicalName().compareTo(o.wrappedClass.getCanonicalName());
        }

        @Override
        public int hashCode() {
            return HASH + (this.wrappedClass != null ? this.wrappedClass.hashCode() : 0);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ComparableEventType)) {
                return false;
            }

            return compareTo((ComparableEventType<T>) o) == 0;
        }
    }
}
