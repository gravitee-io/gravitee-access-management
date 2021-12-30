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
import io.gravitee.am.service.exception.AlertNotifierNotFoundException;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.AlertNotifierRepository;
import io.gravitee.am.repository.management.api.search.AlertNotifierCriteria;
import io.gravitee.am.service.impl.AlertNotifierServiceImpl;
import io.gravitee.am.service.model.NewAlertNotifier;
import io.gravitee.am.service.model.PatchAlertNotifier;
import io.gravitee.am.service.reporter.builder.management.AlertNotifierAuditBuilder;
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

import java.util.Date;
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
public class AlertNotifierServiceTest {

    private static final String ALERT_NOTIFIER_ID = "alertNotifier#1";
    private static final String DOMAIN_ID = "domain#1";
    private static final String USERNAME = "user#1";
    private static final String NAME = "name";
    private static final String CONFIGURATION = "{}";
    private static final String TYPE = "webhook";

    @Mock
    private AlertNotifierRepository alertNotifierRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private EventService eventService;

    private AlertNotifierService cut;

    @Before
    public void before() {
        cut = new AlertNotifierServiceImpl(alertNotifierRepository, auditService, eventService);
    }

    @Test
    public void getById() {
        final AlertNotifier alertNotifier = new AlertNotifier();
        alertNotifier.setReferenceType(ReferenceType.DOMAIN);
        alertNotifier.setReferenceId(DOMAIN_ID);
        when(alertNotifierRepository.findById(ALERT_NOTIFIER_ID)).thenReturn(Maybe.just(alertNotifier));
        final TestObserver<AlertNotifier> obs = cut.getById(ReferenceType.DOMAIN, DOMAIN_ID, ALERT_NOTIFIER_ID).test();

        obs.awaitTerminalEvent();
        obs.assertValue(alertNotifier);
    }

    @Test
    public void getByIdNotFound() {
        when(alertNotifierRepository.findById(ALERT_NOTIFIER_ID)).thenReturn(Maybe.empty());
        final TestObserver<AlertNotifier> obs = cut.getById(ReferenceType.DOMAIN, DOMAIN_ID, ALERT_NOTIFIER_ID).test();

        obs.awaitTerminalEvent();
        obs.assertError(AlertNotifierNotFoundException.class);
    }

    @Test
    public void findByDomainCriteria() {
        final AlertNotifier alertNotifier = new AlertNotifier();
        final AlertNotifierCriteria criteria = new AlertNotifierCriteria();
        when(alertNotifierRepository.findByCriteria(ReferenceType.DOMAIN, DOMAIN_ID, criteria)).thenReturn(Flowable.just(alertNotifier));
        final TestSubscriber<AlertNotifier> obs = cut.findByDomainAndCriteria(DOMAIN_ID, criteria).test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(alertNotifier);
    }

    @Test
    public void findByReferenceAndCriteria() {
        final AlertNotifier alertNotifier = new AlertNotifier();
        final AlertNotifierCriteria criteria = new AlertNotifierCriteria();
        when(alertNotifierRepository.findByCriteria(ReferenceType.DOMAIN, DOMAIN_ID, criteria)).thenReturn(Flowable.just(alertNotifier));
        final TestSubscriber<AlertNotifier> obs = cut.findByReferenceAndCriteria(ReferenceType.DOMAIN, DOMAIN_ID, criteria).test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(alertNotifier);
    }

    @Test
    public void create() {
        final AlertNotifierCriteria criteria = new AlertNotifierCriteria();

        final NewAlertNotifier newAlertNotifier = new NewAlertNotifier();
        newAlertNotifier.setEnabled(true);
        newAlertNotifier.setName(NAME);
        newAlertNotifier.setConfiguration(CONFIGURATION);
        newAlertNotifier.setType(TYPE);

        when(alertNotifierRepository.create(any(AlertNotifier.class))).thenAnswer(i -> Single.just(i.getArgument(0)));
        when(eventService.create(any(Event.class))).thenAnswer(i -> Single.just(i.getArgument(0)));

        final TestObserver<AlertNotifier> obs = cut.create(ReferenceType.DOMAIN, DOMAIN_ID, newAlertNotifier, new DefaultUser(USERNAME)).test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(alertNotifier -> {
            assertNotNull(alertNotifier.getId());
            assertEquals(NAME, alertNotifier.getName());
            assertEquals(CONFIGURATION, alertNotifier.getConfiguration());
            assertEquals(TYPE, alertNotifier.getType());
            assertEquals(ReferenceType.DOMAIN, alertNotifier.getReferenceType());
            assertEquals(DOMAIN_ID, alertNotifier.getReferenceId());
            assertNotNull(alertNotifier.getCreatedAt());
            assertNotNull(alertNotifier.getUpdatedAt());

            return true;
        });

        verify(auditService, never()).report(any(AlertNotifierAuditBuilder.class));
    }

    @Test
    public void update() {

        final PatchAlertNotifier patchAlertNotifier = new PatchAlertNotifier();
        patchAlertNotifier.setEnabled(Optional.of(true));
        patchAlertNotifier.setName(Optional.of(NAME));
        patchAlertNotifier.setConfiguration(Optional.of(CONFIGURATION));

        final Date createdAt = new Date();
        final AlertNotifier alertNotifierToUpdate = new AlertNotifier();
        alertNotifierToUpdate.setId(ALERT_NOTIFIER_ID);
        alertNotifierToUpdate.setType(TYPE);
        alertNotifierToUpdate.setReferenceType(ReferenceType.DOMAIN);
        alertNotifierToUpdate.setReferenceId(DOMAIN_ID);
        alertNotifierToUpdate.setCreatedAt(createdAt);

        when(alertNotifierRepository.findById(ALERT_NOTIFIER_ID)).thenReturn(Maybe.just(alertNotifierToUpdate));
        when(alertNotifierRepository.update(any(AlertNotifier.class))).thenAnswer(i -> Single.just(i.getArgument(0)));
        when(eventService.create(any(Event.class))).thenAnswer(i -> Single.just(i.getArgument(0)));

        final TestObserver<AlertNotifier> obs = cut.update(ReferenceType.DOMAIN, DOMAIN_ID, ALERT_NOTIFIER_ID, patchAlertNotifier, new DefaultUser(USERNAME)).test();

        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertValue(alertNotifier -> {
            assertNotNull(alertNotifier.getId());
            assertEquals(NAME, alertNotifier.getName());
            assertEquals(CONFIGURATION, alertNotifier.getConfiguration());
            assertEquals(TYPE, alertNotifier.getType());
            assertEquals(ReferenceType.DOMAIN, alertNotifier.getReferenceType());
            assertEquals(DOMAIN_ID, alertNotifier.getReferenceId());
            assertEquals(createdAt, alertNotifier.getCreatedAt());
            assertNotNull(alertNotifier.getUpdatedAt());

            return true;
        });

        verify(auditService, never()).report(any(AlertNotifierAuditBuilder.class));
    }

    @Test
    public void updateNotFound() {

        final PatchAlertNotifier patchAlertNotifier = new PatchAlertNotifier();
        patchAlertNotifier.setEnabled(Optional.of(true));
        patchAlertNotifier.setName(Optional.of(NAME));
        patchAlertNotifier.setConfiguration(Optional.of(CONFIGURATION));


        when(alertNotifierRepository.findById(ALERT_NOTIFIER_ID)).thenReturn(Maybe.empty());

        final TestObserver<AlertNotifier> obs = cut.update(ReferenceType.DOMAIN, DOMAIN_ID, ALERT_NOTIFIER_ID, patchAlertNotifier, new DefaultUser(USERNAME)).test();

        obs.awaitTerminalEvent();
        obs.assertError(AlertNotifierNotFoundException.class);

        verifyZeroInteractions(auditService, eventService);
    }
}