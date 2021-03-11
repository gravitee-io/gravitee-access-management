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

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.service.exception.AlertTriggerNotFoundException;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.model.alert.AlertTriggerType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.AlertTriggerRepository;
import io.gravitee.am.repository.management.api.search.AlertTriggerCriteria;
import io.gravitee.am.service.impl.AlertTriggerServiceImpl;
import io.gravitee.am.service.model.PatchAlertTrigger;
import io.gravitee.am.service.reporter.builder.management.AlertTriggerAuditBuilder;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AlertTriggerServiceTest {

    private static final String ALERT_TRIGGER_ID = "alertTrigger#1";
    private static final String DOMAIN_ID = "domain#1";
    private static final String USERNAME = "user#1";
    @Mock
    private AlertTriggerRepository alertTriggerRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private EventService eventService;

    private AlertTriggerService cut;

    @Before
    public void before() {
        cut = new AlertTriggerServiceImpl(alertTriggerRepository, auditService, eventService);
    }

    @Test
    public void getById() {
        final AlertTrigger alertTrigger = new AlertTrigger();
        when(alertTriggerRepository.findById(ALERT_TRIGGER_ID)).thenReturn(Maybe.just(alertTrigger));
        final TestObserver<AlertTrigger> obs = cut.getById(ALERT_TRIGGER_ID).test();

        obs.awaitTerminalEvent();
        obs.assertValue(alertTrigger);
    }

    @Test
    public void getByIdNotFound() {
        when(alertTriggerRepository.findById(ALERT_TRIGGER_ID)).thenReturn(Maybe.empty());
        final TestObserver<AlertTrigger> obs = cut.getById(ALERT_TRIGGER_ID).test();

        obs.awaitTerminalEvent();
        obs.assertError(AlertTriggerNotFoundException.class);
    }

    @Test
    public void findByDomainAndCriteria() {
        final AlertTrigger alertTrigger = new AlertTrigger();
        final AlertTriggerCriteria criteria = new AlertTriggerCriteria();
        when(alertTriggerRepository.findByCriteria(ReferenceType.DOMAIN, DOMAIN_ID, criteria)).thenReturn(Flowable.just(alertTrigger));
        final TestSubscriber<AlertTrigger> obs = cut.findByDomainAndCriteria(DOMAIN_ID, criteria).test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(alertTrigger);
    }

    @Test
    public void create() {
        final AlertTriggerCriteria criteria = new AlertTriggerCriteria();
        criteria.setType(AlertTriggerType.TOO_MANY_LOGIN_FAILURES);

        final List<String> alertNotifierIds = Collections.singletonList("alertNotifier#1");
        final PatchAlertTrigger patchAlertTrigger = new PatchAlertTrigger();
        patchAlertTrigger.setType(AlertTriggerType.TOO_MANY_LOGIN_FAILURES);
        patchAlertTrigger.setEnabled(Optional.of(true));
        patchAlertTrigger.setAlertNotifiers(Optional.of(alertNotifierIds));

        when(alertTriggerRepository.findByCriteria(ReferenceType.DOMAIN, DOMAIN_ID, criteria)).thenReturn(Flowable.empty());
        when(alertTriggerRepository.create(any(AlertTrigger.class))).thenAnswer(i-> Single.just(i.getArgument(0)));
        when(eventService.create(any(Event.class))).thenAnswer(i-> Single.just(i.getArgument(0)));

        final TestObserver<AlertTrigger> obs = cut.createOrUpdate(ReferenceType.DOMAIN, DOMAIN_ID, patchAlertTrigger, new DefaultUser(USERNAME)).test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(alertTrigger -> {
            assertNotNull(alertTrigger.getId());
            assertEquals(ReferenceType.DOMAIN, alertTrigger.getReferenceType());
            assertEquals(DOMAIN_ID, alertTrigger.getReferenceId());
            assertEquals(AlertTriggerType.TOO_MANY_LOGIN_FAILURES, alertTrigger.getType());
            assertEquals(alertNotifierIds, alertTrigger.getAlertNotifiers());
            assertNotNull(alertTrigger.getCreatedAt());
            assertNotNull(alertTrigger.getUpdatedAt());

            return true;
        });

        verify(auditService, times(1)).report(any(AlertTriggerAuditBuilder.class));
    }

    @Test
    public void update() {
        final AlertTriggerCriteria criteria = new AlertTriggerCriteria();
        criteria.setType(AlertTriggerType.TOO_MANY_LOGIN_FAILURES);

        final List<String> alertNotifierIds = Collections.singletonList("alertNotifier#1");
        final PatchAlertTrigger patchAlertTrigger = new PatchAlertTrigger();
        patchAlertTrigger.setType(AlertTriggerType.TOO_MANY_LOGIN_FAILURES);
        patchAlertTrigger.setEnabled(Optional.of(true));
        patchAlertTrigger.setAlertNotifiers(Optional.of(alertNotifierIds));

        final AlertTrigger existingAlertTrigger = new AlertTrigger();
        existingAlertTrigger.setId(ALERT_TRIGGER_ID);
        existingAlertTrigger.setType(AlertTriggerType.TOO_MANY_LOGIN_FAILURES);
        when(alertTriggerRepository.findByCriteria(ReferenceType.DOMAIN, DOMAIN_ID, criteria)).thenReturn(Flowable.just(existingAlertTrigger));
        when(alertTriggerRepository.update(any(AlertTrigger.class))).thenAnswer(i-> Single.just(i.getArgument(0)));
        when(eventService.create(any(Event.class))).thenAnswer(i-> Single.just(i.getArgument(0)));

        final TestObserver<AlertTrigger> obs = cut.createOrUpdate(ReferenceType.DOMAIN, DOMAIN_ID, patchAlertTrigger, new DefaultUser(USERNAME)).test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(alertTrigger -> {
            assertEquals(AlertTriggerType.TOO_MANY_LOGIN_FAILURES, alertTrigger.getType());
            assertEquals(alertNotifierIds, alertTrigger.getAlertNotifiers());
            assertNotNull(alertTrigger.getUpdatedAt());

            return true;
        });

        verify(auditService, times(1)).report(any(AlertTriggerAuditBuilder.class));
    }
}