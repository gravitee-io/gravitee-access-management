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

import io.gravitee.am.model.AuthenticationDeviceNotifier;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.AuthenticationDeviceNotifierRepository;
import io.gravitee.am.service.exception.AuthenticationDeviceNotifierNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.AuthenticationDeviceNotifierServiceImpl;
import io.gravitee.am.service.model.NewAuthenticationDeviceNotifier;
import io.gravitee.am.service.model.UpdateAuthenticationDeviceNotifier;
import io.gravitee.am.service.reporter.builder.management.AuthDeviceNotifierAuditBuilder;
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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationDeviceNotifierServiceTest {

    @InjectMocks
    private AuthenticationDeviceNotifierService authDeviceNotifierService = new AuthenticationDeviceNotifierServiceImpl();

    @Mock
    private EventService eventService;

    @Mock
    private AuthenticationDeviceNotifierRepository authDeviceNotifierRepository;

    @Mock
    private AuditService auditService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(authDeviceNotifierRepository.findById("auth-dev-notifier")).thenReturn(Maybe.just(new AuthenticationDeviceNotifier()));
        TestObserver testObserver = authDeviceNotifierService.findById("auth-dev-notifier").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingAuthenticationDeviceNotifier() {
        when(authDeviceNotifierRepository.findById("auth-dev-notifier")).thenReturn(Maybe.empty());
        TestObserver testObserver = authDeviceNotifierService.findById("auth-dev-notifier").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(authDeviceNotifierRepository.findById("auth-dev-notifier")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        authDeviceNotifierService.findById("auth-dev-notifier").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomain() {
        when(authDeviceNotifierRepository.findByReference(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.just(new AuthenticationDeviceNotifier()));
        TestSubscriber<AuthenticationDeviceNotifier> testSubscriber = authDeviceNotifierService.findByDomain(DOMAIN).test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(authDeviceNotifierRepository.findByReference(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = authDeviceNotifierService.findByDomain(DOMAIN).test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewAuthenticationDeviceNotifier newAuthenticationDeviceNotifier = Mockito.mock(NewAuthenticationDeviceNotifier.class);
        when(authDeviceNotifierRepository.create(any(AuthenticationDeviceNotifier.class))).thenReturn(Single.just(new AuthenticationDeviceNotifier()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = authDeviceNotifierService.create(DOMAIN, newAuthenticationDeviceNotifier).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(eventService).create(any());
        verify(auditService).report(any(AuthDeviceNotifierAuditBuilder.class));
        verify(authDeviceNotifierRepository).create(any(AuthenticationDeviceNotifier.class));
    }

    @Test
    public void shouldCreate_technicalException() {
        NewAuthenticationDeviceNotifier newAuthenticationDeviceNotifier = Mockito.mock(NewAuthenticationDeviceNotifier.class);
        when(authDeviceNotifierRepository.create(any())).thenReturn(Single.error(TechnicalException::new));

        TestObserver<AuthenticationDeviceNotifier> testObserver = new TestObserver<>();
        authDeviceNotifierService.create(DOMAIN, newAuthenticationDeviceNotifier).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(eventService, never()).create(any());
        verify(auditService).report(any(AuthDeviceNotifierAuditBuilder.class));
    }

    @Test
    public void shouldUpdate() {
        UpdateAuthenticationDeviceNotifier updateAuthenticationDeviceNotifier = Mockito.mock(UpdateAuthenticationDeviceNotifier.class);
        when(updateAuthenticationDeviceNotifier.getName()).thenReturn("auth-dev-notifier");
        when(authDeviceNotifierRepository.findById("auth-dev-notifier")).thenReturn(Maybe.just(new AuthenticationDeviceNotifier()));
        when(authDeviceNotifierRepository.update(any(AuthenticationDeviceNotifier.class))).thenReturn(Single.just(new AuthenticationDeviceNotifier()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = authDeviceNotifierService.update(DOMAIN, "auth-dev-notifier", updateAuthenticationDeviceNotifier).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(authDeviceNotifierRepository).findById(anyString());
        verify(auditService).report(any(AuthDeviceNotifierAuditBuilder.class));
        verify(authDeviceNotifierRepository).update(any(AuthenticationDeviceNotifier.class));
    }

    @Test
    public void shouldUpdate_technicalException() {
        UpdateAuthenticationDeviceNotifier updateAuthenticationDeviceNotifier = Mockito.mock(UpdateAuthenticationDeviceNotifier.class);
        when(authDeviceNotifierRepository.findById("auth-dev-notifier")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = authDeviceNotifierService.update(DOMAIN, "auth-dev-notifier", updateAuthenticationDeviceNotifier).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(authDeviceNotifierRepository).findById(anyString());
        verify(authDeviceNotifierRepository, never()).update(any(AuthenticationDeviceNotifier.class));
        verify(auditService, never()).report(any(AuthDeviceNotifierAuditBuilder.class));
    }

    @Test
    public void shouldDelete_notAuthenticationDeviceNotifier() {
        when(authDeviceNotifierRepository.findById("auth-dev-notifier")).thenReturn(Maybe.empty());

        TestObserver testObserver = authDeviceNotifierService.delete(DOMAIN, "auth-dev-notifier").test();

        testObserver.assertError(AuthenticationDeviceNotifierNotFoundException.class);
        testObserver.assertNotComplete();

        verify(authDeviceNotifierRepository, never()).delete(anyString());
        verify(auditService, never()).report(any(AuthDeviceNotifierAuditBuilder.class));
    }

    @Test
    public void shouldDelete_technicalException() {
        when(authDeviceNotifierRepository.findById("auth-dev-notifier")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = authDeviceNotifierService.delete(DOMAIN, "auth-dev-notifier").test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        AuthenticationDeviceNotifier deviceNotifier = new AuthenticationDeviceNotifier();
        deviceNotifier.setId("deviceNotifier-id");
        when(authDeviceNotifierRepository.findById(deviceNotifier.getId())).thenReturn(Maybe.just(deviceNotifier));
        when(authDeviceNotifierRepository.delete(deviceNotifier.getId())).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = authDeviceNotifierService.delete(DOMAIN, deviceNotifier.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(authDeviceNotifierRepository).delete(deviceNotifier.getId());
        verify(auditService).report(any(AuthDeviceNotifierAuditBuilder.class));
    }
}
