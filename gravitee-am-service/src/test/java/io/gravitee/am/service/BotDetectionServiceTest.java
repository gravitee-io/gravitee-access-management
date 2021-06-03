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

import com.google.common.collect.Sets;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.BotDetectionRepository;
import io.gravitee.am.service.exception.BotDetectionNotFoundException;
import io.gravitee.am.service.exception.BotDetectionUsedException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.BotDetectionServiceImpl;
import io.gravitee.am.service.model.NewBotDetection;
import io.gravitee.am.service.model.UpdateBotDetection;
import io.gravitee.am.service.reporter.builder.management.BotDetectionAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class BotDetectionServiceTest {

    @InjectMocks
    private BotDetectionService botDetectionService = new BotDetectionServiceImpl();

    @Mock
    private EventService eventService;

    @Mock
    private BotDetectionRepository botDetectionRepository;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private DomainService domainService;

    @Mock
    private AuditService auditService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(botDetectionRepository.findById("bot-detection")).thenReturn(Maybe.just(new BotDetection()));
        TestObserver testObserver = botDetectionService.findById("bot-detection").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingBotDetection() {
        when(botDetectionRepository.findById("bot-detection")).thenReturn(Maybe.empty());
        TestObserver testObserver = botDetectionService.findById("bot-detection").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(botDetectionRepository.findById("bot-detection")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        botDetectionService.findById("bot-detection").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomain() {
        when(botDetectionRepository.findByReference(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(new BotDetection()));
        TestSubscriber<BotDetection> testSubscriber = botDetectionService.findByDomain(DOMAIN).test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(botDetectionRepository.findByReference(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = botDetectionService.findByDomain(DOMAIN).test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewBotDetection newBotDetection = Mockito.mock(NewBotDetection.class);
        when(newBotDetection.getDetectionType()).thenReturn(BotDetection.DETECTION_TYPE_CAPTCHA);
        when(botDetectionRepository.create(any(BotDetection.class))).thenReturn(Single.just(new BotDetection()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = botDetectionService.create(DOMAIN, newBotDetection).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(eventService).create(any());
        verify(auditService).report(any(BotDetectionAuditBuilder.class));
        verify(botDetectionRepository).create(any(BotDetection.class));
    }

    @Test
    public void shouldCreate_technicalException() {
        NewBotDetection newBotDetection = Mockito.mock(NewBotDetection.class);
        when(newBotDetection.getDetectionType()).thenReturn(BotDetection.DETECTION_TYPE_CAPTCHA);
        when(botDetectionRepository.create(any())).thenReturn(Single.error(TechnicalException::new));

        TestObserver<BotDetection> testObserver = new TestObserver<>();
        botDetectionService.create(DOMAIN, newBotDetection).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(eventService, never()).create(any());
        verify(auditService).report(any(BotDetectionAuditBuilder.class));
    }

    @Test
    public void shouldUpdate() {
        UpdateBotDetection updateBotDetection = Mockito.mock(UpdateBotDetection.class);
        when(updateBotDetection.getName()).thenReturn("bot-detection");
        when(botDetectionRepository.findById("bot-detection")).thenReturn(Maybe.just(new BotDetection()));
        when(botDetectionRepository.update(any(BotDetection.class))).thenReturn(Single.just(new BotDetection()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = botDetectionService.update(DOMAIN, "bot-detection", updateBotDetection).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(botDetectionRepository).findById(anyString());
        verify(auditService).report(any(BotDetectionAuditBuilder.class));
        verify(botDetectionRepository).update(any(BotDetection.class));
    }

    @Test
    public void shouldUpdate_technicalException() {
        UpdateBotDetection updateBotDetection = Mockito.mock(UpdateBotDetection.class);
        when(botDetectionRepository.findById("bot-detection")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = botDetectionService.update(DOMAIN, "bot-detection", updateBotDetection).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(botDetectionRepository).findById(anyString());
        verify(botDetectionRepository, never()).update(any(BotDetection.class));
        verify(auditService, never()).report(any(BotDetectionAuditBuilder.class));
    }

    @Test
    public void shouldDelete_notBotDetection() {
        when(botDetectionRepository.findById("bot-detection")).thenReturn(Maybe.empty());

        TestObserver testObserver = botDetectionService.delete(DOMAIN, "bot-detection").test();

        testObserver.assertError(BotDetectionNotFoundException.class);
        testObserver.assertNotComplete();

        verify(botDetectionRepository, never()).delete(anyString());
        verify(auditService, never()).report(any(BotDetectionAuditBuilder.class));
    }

    @Test
    public void shouldDelete_technicalException() {
        when(botDetectionRepository.findById("bot-detection")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = botDetectionService.delete(DOMAIN, "bot-detection").test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        BotDetection detection = new BotDetection();
        detection.setId("detection-id");
        when(botDetectionRepository.findById(detection.getId())).thenReturn(Maybe.just(detection));
        when(botDetectionRepository.delete(detection.getId())).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        final Domain domain = new Domain();
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(domain));
        when(applicationService.findByDomain(DOMAIN)).thenReturn(Single.just(Collections.emptySet()));

        TestObserver testObserver = botDetectionService.delete(DOMAIN, detection.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(botDetectionRepository).delete(detection.getId());
        verify(auditService).report(any(BotDetectionAuditBuilder.class));
    }

    @Test
    public void shouldNotDelete_UsedByDomain() {
        BotDetection detection = new BotDetection();
        detection.setId("detection-id");
        when(botDetectionRepository.findById(detection.getId())).thenReturn(Maybe.just(detection));
        final Domain domain = new Domain();
        final AccountSettings accountSettings = new AccountSettings();
        accountSettings.setBotDetectionPlugin(detection.getId());
        domain.setAccountSettings(accountSettings);
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(domain));

        TestObserver testObserver = botDetectionService.delete(DOMAIN, detection.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(BotDetectionUsedException.class);

        verify(botDetectionRepository, never()).delete(detection.getId());
    }

    @Test
    public void shouldNotDelete_UsedByApp() {
        BotDetection detection = new BotDetection();
        detection.setId("detection-id");
        when(botDetectionRepository.findById(detection.getId())).thenReturn(Maybe.just(detection));
        final Domain domain = new Domain();
        when(domainService.findById(DOMAIN)).thenReturn(Maybe.just(domain));
        Application app = new Application();
        final AccountSettings accountSettings = new AccountSettings();
        accountSettings.setBotDetectionPlugin(detection.getId());
        final ApplicationSettings settings = new ApplicationSettings();
        settings.setAccount(accountSettings);
        app.setSettings(settings);
        when(applicationService.findByDomain(DOMAIN)).thenReturn(Single.just(Sets.newHashSet(app)));

        TestObserver testObserver = botDetectionService.delete(DOMAIN, detection.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(BotDetectionUsedException.class);

        verify(botDetectionRepository, never()).delete(detection.getId());
    }
}
