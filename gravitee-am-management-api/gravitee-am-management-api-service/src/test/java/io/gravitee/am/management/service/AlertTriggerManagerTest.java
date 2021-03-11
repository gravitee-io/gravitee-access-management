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
package io.gravitee.am.management.service;

import io.gravitee.alert.api.trigger.Trigger;
import io.gravitee.alert.api.trigger.TriggerProvider;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.AlertNotifierEvent;
import io.gravitee.am.common.event.AlertTriggerEvent;
import io.gravitee.am.common.event.DomainEvent;
import io.gravitee.am.management.service.alerts.handlers.AlertNotificationCommandHandler;
import io.gravitee.am.management.service.alerts.handlers.ResolvePropertyCommandHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.model.alert.AlertTriggerType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.search.AlertNotifierCriteria;
import io.gravitee.am.repository.management.api.search.AlertTriggerCriteria;
import io.gravitee.am.repository.management.api.search.DomainCriteria;
import io.gravitee.am.service.AlertNotifierService;
import io.gravitee.am.service.AlertTriggerService;
import io.gravitee.am.service.DomainService;
import io.gravitee.common.event.EventManager;
import io.gravitee.common.event.impl.SimpleEvent;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AlertTriggerManagerTest {

    private static final String DOMAIN_ID = "domain#1";
    private static final String ALERT_NOTIFIER_ID = "alertNotifier#1";
    private static final String DOMAIN_NAME = "DomainName";
    private static final String ALERT_TRIGGER_ID = "alertTrigger#1";
    @Mock
    private TriggerProvider triggerProvider;

    @Mock
    private AlertTriggerService alertTriggerService;

    @Mock
    private AlertNotifierService alertNotifierService;

    @Mock
    private DomainService domainService;

    @Mock
    private EventManager eventManager;

    @Mock
    private ResolvePropertyCommandHandler resolvePropertyCommandHandler;

    @Mock
    private AlertNotificationCommandHandler alertNotificationCommandHandler;

    private AlertTriggerManager cut;

    @Before
    public void before() {
        cut = new AlertTriggerManager(triggerProvider, alertTriggerService, alertNotifierService, domainService, eventManager, new MockEnvironment(),
                resolvePropertyCommandHandler, alertNotificationCommandHandler);
    }

    @Test
    public void doOnConnect() throws Exception {

        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setName(DOMAIN_NAME);

        final AlertTrigger alertTrigger = new AlertTrigger();
        alertTrigger.setEnabled(true);
        alertTrigger.setType(AlertTriggerType.TOO_MANY_LOGIN_FAILURES);
        alertTrigger.setAlertNotifiers(Collections.singletonList(ALERT_NOTIFIER_ID));

        final AlertNotifierCriteria alertNotifierCriteria = new AlertNotifierCriteria();
        alertNotifierCriteria.setIds(Collections.singletonList(ALERT_NOTIFIER_ID));
        alertNotifierCriteria.setEnabled(true);

        final AlertNotifier alertNotifier = new AlertNotifier();
        alertNotifier.setId(ALERT_NOTIFIER_ID);

        when(domainService.findAllByCriteria(new DomainCriteria())).thenReturn(Flowable.just(domain));
        when(alertTriggerService.findByDomainAndCriteria(DOMAIN_ID, new AlertTriggerCriteria())).thenReturn(Flowable.just(alertTrigger));
        when(alertNotifierService.findByReferenceAndCriteria(alertTrigger.getReferenceType(), alertTrigger.getReferenceId(), alertNotifierCriteria)).thenReturn(Flowable.just(alertNotifier));

        this.cut.doOnConnect();

        verify(triggerProvider, times(1)).register(any(Trigger.class));
    }

    @Test
    public void onDomainEvent() {
        final DomainEvent domainEvent = DomainEvent.actionOf(Action.CREATE);
        final Payload payload = new Payload(DOMAIN_ID, ReferenceType.DOMAIN, DOMAIN_ID, Action.CREATE);
        final SimpleEvent<DomainEvent, Payload> event = new SimpleEvent<>(domainEvent, payload);


        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setName(DOMAIN_NAME);

        final AlertTrigger alertTrigger = new AlertTrigger();
        alertTrigger.setEnabled(true);
        alertTrigger.setType(AlertTriggerType.TOO_MANY_LOGIN_FAILURES);
        alertTrigger.setAlertNotifiers(Collections.singletonList(ALERT_NOTIFIER_ID));

        final AlertNotifierCriteria alertNotifierCriteria = new AlertNotifierCriteria();
        alertNotifierCriteria.setIds(Collections.singletonList(ALERT_NOTIFIER_ID));
        alertNotifierCriteria.setEnabled(true);

        final AlertNotifier alertNotifier = new AlertNotifier();
        alertNotifier.setId(ALERT_NOTIFIER_ID);

        when(domainService.findById(domain.getId())).thenReturn(Maybe.just(domain));
        when(alertTriggerService.findByDomainAndCriteria(DOMAIN_ID, new AlertTriggerCriteria())).thenReturn(Flowable.just(alertTrigger));
        when(alertNotifierService.findByReferenceAndCriteria(alertTrigger.getReferenceType(), alertTrigger.getReferenceId(), alertNotifierCriteria)).thenReturn(Flowable.just(alertNotifier));

        this.cut.onDomainEvent(event);

        verify(triggerProvider, times(1)).register(any(Trigger.class));
    }

    @Test
    public void onAlertTriggerEvent() {
        final AlertTriggerEvent alertTriggerEvent = AlertTriggerEvent.actionOf(Action.CREATE);
        final Payload payload = new Payload(ALERT_TRIGGER_ID, ReferenceType.DOMAIN, DOMAIN_ID, Action.CREATE);
        final SimpleEvent<AlertTriggerEvent, Payload> event = new SimpleEvent<>(alertTriggerEvent, payload);

        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setName(DOMAIN_NAME);

        final AlertTrigger alertTrigger = new AlertTrigger();
        alertTrigger.setEnabled(true);
        alertTrigger.setType(AlertTriggerType.TOO_MANY_LOGIN_FAILURES);
        alertTrigger.setAlertNotifiers(Collections.singletonList(ALERT_NOTIFIER_ID));

        final AlertNotifierCriteria alertNotifierCriteria = new AlertNotifierCriteria();
        alertNotifierCriteria.setIds(Collections.singletonList(ALERT_NOTIFIER_ID));
        alertNotifierCriteria.setEnabled(true);

        final AlertNotifier alertNotifier = new AlertNotifier();
        alertNotifier.setId(ALERT_NOTIFIER_ID);

        when(domainService.findById(domain.getId())).thenReturn(Maybe.just(domain));
        when(alertTriggerService.getById(ALERT_TRIGGER_ID)).thenReturn(Single.just(alertTrigger));
        when(alertNotifierService.findByReferenceAndCriteria(alertTrigger.getReferenceType(), alertTrigger.getReferenceId(), alertNotifierCriteria)).thenReturn(Flowable.just(alertNotifier));

        this.cut.onAlertTriggerEvent(event);

        verify(triggerProvider, times(1)).register(any(Trigger.class));
    }

    @Test
    public void onAlertNotifierEvent() {
        final AlertNotifierEvent alertNotifierEvent = AlertNotifierEvent.actionOf(Action.CREATE);
        final Payload payload = new Payload(ALERT_NOTIFIER_ID, ReferenceType.DOMAIN, DOMAIN_ID, Action.CREATE);
        final SimpleEvent<AlertNotifierEvent, Payload> event = new SimpleEvent<>(alertNotifierEvent, payload);

        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setName(DOMAIN_NAME);
        domain.setAlertEnabled(true);
        domain.setEnabled(true);

        final AlertTriggerCriteria alertTriggerCriteria = new AlertTriggerCriteria();
        alertTriggerCriteria.setEnabled(true);
        alertTriggerCriteria.setAlertNotifierIds(Collections.singletonList(ALERT_NOTIFIER_ID));

        final AlertTrigger alertTrigger = new AlertTrigger();
        alertTrigger.setEnabled(true);
        alertTrigger.setType(AlertTriggerType.TOO_MANY_LOGIN_FAILURES);
        alertTrigger.setAlertNotifiers(Collections.singletonList(ALERT_NOTIFIER_ID));

        final AlertNotifierCriteria alertNotifierCriteria = new AlertNotifierCriteria();
        alertNotifierCriteria.setIds(Collections.singletonList(ALERT_NOTIFIER_ID));
        alertNotifierCriteria.setEnabled(true);

        final AlertNotifier alertNotifier = new AlertNotifier();
        alertNotifier.setId(ALERT_NOTIFIER_ID);

        when(domainService.findById(domain.getId())).thenReturn(Maybe.just(domain));
        when(alertTriggerService.findByDomainAndCriteria(DOMAIN_ID, alertTriggerCriteria)).thenReturn(Flowable.just(alertTrigger));
        when(alertNotifierService.findByReferenceAndCriteria(alertTrigger.getReferenceType(), alertTrigger.getReferenceId(), alertNotifierCriteria)).thenReturn(Flowable.just(alertNotifier));

        this.cut.onAlertNotifierEvent(event);

        verify(triggerProvider, times(1)).register(any(Trigger.class));
    }

    @Test
    public void onAlertNotifierEventWithDomainDisabled() {
        final AlertNotifierEvent alertNotifierEvent = AlertNotifierEvent.actionOf(Action.CREATE);
        final Payload payload = new Payload(ALERT_NOTIFIER_ID, ReferenceType.DOMAIN, DOMAIN_ID, Action.CREATE);
        final SimpleEvent<AlertNotifierEvent, Payload> event = new SimpleEvent<>(alertNotifierEvent, payload);

        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setName(DOMAIN_NAME);
        domain.setAlertEnabled(true);
        domain.setEnabled(false);

        when(domainService.findById(domain.getId())).thenReturn(Maybe.just(domain));

        this.cut.onAlertNotifierEvent(event);

        verifyZeroInteractions(triggerProvider);
    }

    @Test
    public void onAlertNotifierEventWithDomainAlertDisabled() {
        final AlertNotifierEvent alertNotifierEvent = AlertNotifierEvent.actionOf(Action.CREATE);
        final Payload payload = new Payload(ALERT_NOTIFIER_ID, ReferenceType.DOMAIN, DOMAIN_ID, Action.CREATE);
        final SimpleEvent<AlertNotifierEvent, Payload> event = new SimpleEvent<>(alertNotifierEvent, payload);

        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setName(DOMAIN_NAME);
        domain.setAlertEnabled(false);
        domain.setEnabled(true);

        when(domainService.findById(domain.getId())).thenReturn(Maybe.just(domain));

        this.cut.onAlertNotifierEvent(event);

        verifyZeroInteractions(triggerProvider);
    }
}