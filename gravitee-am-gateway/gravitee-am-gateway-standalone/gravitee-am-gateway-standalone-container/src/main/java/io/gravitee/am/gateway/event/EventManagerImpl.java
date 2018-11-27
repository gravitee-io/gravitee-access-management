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
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.event.impl.SimpleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Override default event manager to enable concurrent access
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EventManagerImpl implements EventManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(io.gravitee.common.event.impl.EventManagerImpl.class);

    private Map<ComparableEventType, List<EventListenerWrapper>> listenersMap = new TreeMap<>();

    public void publishEvent(Enum type, Object content) {
        this.publishEvent(new SimpleEvent(type, content));
    }

    public void publishEvent(Event event) {
        LOGGER.debug("Publish event {} - {}", event.type(), event.content());

        List<EventListenerWrapper> listeners = getEventListeners(event.type().getClass());
        List<EventListenerWrapper> safeConcurrentListeners = Lists.newArrayList(listeners.iterator());

        for(EventListenerWrapper listener : safeConcurrentListeners) {
            listener.eventListener().onEvent(event);
        }
    }

    public <T extends Enum> void subscribeForEvents(EventListener<T, ?> eventListener, T... events) {
        for( T event : events) {
            addEventListener(eventListener, (Class<T>) event.getClass(), Arrays.asList(events));
        }
    }

    public <T extends Enum> void subscribeForEvents(EventListener<T, ?> eventListener, Class<T> events) {
        addEventListener(eventListener, events, EnumSet.allOf(events));
    }

    private <T extends Enum> void addEventListener(EventListener<T, ?> eventListener, Class<T> enumClass, Collection<T> events) {
        LOGGER.info("Register new listener {} for event type {}", eventListener.getClass().getSimpleName(), enumClass);

        List<EventListenerWrapper> listeners = getEventListeners(enumClass);
        listeners.add(new EventListenerWrapper(eventListener, events));
    }

    private <T extends Enum> List<EventListenerWrapper> getEventListeners(Class<T> eventType) {
        List<EventListenerWrapper> listeners = this.listenersMap.get(new ComparableEventType(eventType));

        if (listeners == null) {
            listeners = new ArrayList<>();
            this.listenersMap.put(new ComparableEventType(eventType), listeners);
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

        public ComparableEventType(Class<? extends T> wrappedClass) {
            this.wrappedClass = wrappedClass;
        }

        @Override
        public int compareTo(ComparableEventType<T> o) {
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
