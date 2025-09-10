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

import io.gravitee.am.common.event.*;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class EventManagerTest {

    @Test
    public void must_not_publish_event_null_type() {
        var eventManager = new EventManagerImpl();
        eventManager.publishEvent(null, "Some event");
    }

    @Test
    public void must_not_publish_event_null_instance() {
        var eventManager = new EventManagerImpl();
        eventManager.publishEvent(null);
    }

    @ParameterizedTest
    @MethodSource("params_that_must_publish_event")
    public void must_publish_event(Enum type, Predicate<Payload> expected) {
        var eventManager = new EventManagerImpl();
        final TestEventListener eventListener = new TestEventListener();
        var domainId = UUID.randomUUID().toString();
        eventManager.subscribeForEvents(eventListener, type.getDeclaringClass(), domainId);

        final Payload content = new Payload(UUID.randomUUID().toString(), ReferenceType.DOMAIN, domainId, Action.UPDATE);
        eventManager.publishEvent(type, content);

        assertTrue(expected.test(eventListener.getContent()));
    }

    private static Stream<Arguments> params_that_must_publish_event() {
        Predicate<Object> predicateNonull = Objects::nonNull;
        return Stream.of(
                        stream(AlertNotifierEvent.values()),
                        stream(AlertTriggerEvent.values()),
                        stream(ApplicationEvent.values()),
                        stream(AuthenticationDeviceNotifierEvent.values()),
                        stream(BotDetectionEvent.values()),
                        stream(CertificateEvent.values()),
                        stream(DeviceIdentifierEvent.values()),
                        stream(DomainEvent.values()),
                        stream(EmailEvent.values()),
                        stream(ExtensionGrantEvent.values()),
                        stream(FactorEvent.values()),
                        stream(FlowEvent.values()),
                        stream(FormEvent.values()),
                        stream(GroupEvent.values()),
                        stream(I18nDictionaryEvent.values()),
                        stream(IdentityProviderEvent.values()),
                        stream(MembershipEvent.values()),
                        stream(ReporterEvent.values()),
                        stream(ResourceEvent.values()),
                        stream(RoleEvent.values()),
                        stream(ScopeEvent.values()),
                        stream(ThemeEvent.values())
                )
                .flatMap(Function.identity())
                .map(event -> Arguments.of(event, predicateNonull));
    }

    @ParameterizedTest
    @MethodSource("params_that_must_publish_event_or_not_without_donmain_id")
    public void must_publish_event_or_not_without_donmain_id(Enum type, Predicate<Payload> expected) {
        var eventManager = new EventManagerImpl();
        final TestEventListener eventListener = new TestEventListener();
        eventManager.subscribeForEvents(eventListener, type);
        var domainId = UUID.randomUUID().toString();

        final Payload content = new Payload(UUID.randomUUID().toString(), ReferenceType.DOMAIN, domainId, Action.UPDATE);
        eventManager.publishEvent(type, content);

        assertTrue(expected.test(eventListener.getContent()));
    }

    private static Stream<Arguments> params_that_must_publish_event_or_not_without_donmain_id() {
        Predicate<Object> predicateNonull = Objects::nonNull;
        Predicate<Object> predicateNull = Objects::isNull;
        return Stream.of(
                        stream(AlertNotifierEvent.values()),
                        stream(AlertTriggerEvent.values()),
                        stream(ApplicationEvent.values()),
                        stream(AuthenticationDeviceNotifierEvent.values()),
                        stream(BotDetectionEvent.values()),
                        stream(CertificateEvent.values()),
                        stream(DeviceIdentifierEvent.values()),
                        stream(DomainEvent.values()),
                        stream(EmailEvent.values()),
                        stream(ExtensionGrantEvent.values()),
                        stream(FactorEvent.values()),
                        stream(FlowEvent.values()),
                        stream(FormEvent.values()),
                        stream(GroupEvent.values()),
                        stream(I18nDictionaryEvent.values()),
                        stream(IdentityProviderEvent.values()),
                        stream(MembershipEvent.values()),
                        stream(ReporterEvent.values()),
                        stream(ResourceEvent.values()),
                        stream(RoleEvent.values()),
                        stream(ScopeEvent.values()),
                        stream(ThemeEvent.values())
                )
                .flatMap(Function.identity())
                .map(event -> event instanceof DomainEvent || event instanceof ReporterEvent ?
                        Arguments.of(event, predicateNonull) :
                        Arguments.of(event, predicateNull));
    }

    public static class TestEventListener implements EventListener<Enum, Payload> {
        private Payload content;


        public Payload getContent() {
            return content;
        }

        @Override
        public void onEvent(Event<Enum, Payload> event) {
            this.content = event.content();
        }
    }
}
