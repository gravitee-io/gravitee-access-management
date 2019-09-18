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

import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import io.gravitee.am.service.exception.IdentityProviderWithApplicationsException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.IdentityProviderServiceImpl;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class IdentityProviderServiceTest {

    @InjectMocks
    private IdentityProviderService identityProviderService = new IdentityProviderServiceImpl();

    @Mock
    private IdentityProviderRepository identityProviderRepository;

    @Mock
    private EventService eventService;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private AuditService auditService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(identityProviderRepository.findById("my-identity-provider")).thenReturn(Maybe.just(new IdentityProvider()));
        TestObserver testObserver = identityProviderService.findById("my-identity-provider").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingIdentityProvider() {
        when(identityProviderRepository.findById("my-identity-provider")).thenReturn(Maybe.empty());
        TestObserver testObserver = identityProviderService.findById("my-identity-provider").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(identityProviderRepository.findById("my-identity-provider")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        identityProviderService.findById("my-identity-provider").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomain() {
        when(identityProviderRepository.findByDomain(DOMAIN)).thenReturn(Single.just(Collections.singleton(new IdentityProvider())));
        TestObserver<List<IdentityProvider>> testObserver = identityProviderService.findByDomain(DOMAIN).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(identityProviders -> identityProviders.size() == 1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(identityProviderRepository.findByDomain(DOMAIN)).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        identityProviderService.findByDomain(DOMAIN).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewIdentityProvider newIdentityProvider = Mockito.mock(NewIdentityProvider.class);
        when(identityProviderRepository.create(any(IdentityProvider.class))).thenReturn(Single.just(new IdentityProvider()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = identityProviderService.create(DOMAIN, newIdentityProvider).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(identityProviderRepository, times(1)).create(any(IdentityProvider.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldCreate_technicalException() {
        NewIdentityProvider newIdentityProvider = Mockito.mock(NewIdentityProvider.class);
        when(identityProviderRepository.create(any(IdentityProvider.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver<IdentityProvider> testObserver = new TestObserver<>();
        identityProviderService.create(DOMAIN, newIdentityProvider).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldUpdate() {
        UpdateIdentityProvider updateIdentityProvider = Mockito.mock(UpdateIdentityProvider.class);
        when(identityProviderRepository.findById("my-identity-provider")).thenReturn(Maybe.just(new IdentityProvider()));
        when(identityProviderRepository.update(any(IdentityProvider.class))).thenReturn(Single.just(new IdentityProvider()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = identityProviderService.update(DOMAIN, "my-identity-provider", updateIdentityProvider).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(identityProviderRepository, times(1)).update(any(IdentityProvider.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldUpdate_technicalException() {
        UpdateIdentityProvider updateIdentityProvider = Mockito.mock(UpdateIdentityProvider.class);
        when(identityProviderRepository.findById("my-identity-provider")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver<IdentityProvider> testObserver = new TestObserver<>();
        identityProviderService.update(DOMAIN, "my-identity-provider", updateIdentityProvider).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete_notExistingIdentityProvider() {
        when(identityProviderRepository.findById("my-identity-provider")).thenReturn(Maybe.empty());

        TestObserver testObserver = identityProviderService.delete(DOMAIN, "my-identity-provider").test();

        testObserver.assertError(IdentityProviderNotFoundException.class);
        testObserver.assertNotComplete();

        verify(applicationService, never()).findByIdentityProvider(anyString());
        verify(identityProviderRepository, never()).delete(anyString());
    }

    @Test
    public void shouldDelete_identitiesWithClients() {
        when(identityProviderRepository.findById("my-identity-provider")).thenReturn(Maybe.just(new IdentityProvider()));
        when(applicationService.findByIdentityProvider("my-identity-provider")).thenReturn(Single.just(Collections.singleton(new Application())));

        TestObserver testObserver = identityProviderService.delete(DOMAIN, "my-identity-provider").test();

        testObserver.assertError(IdentityProviderWithApplicationsException.class);
        testObserver.assertNotComplete();

        verify(identityProviderRepository, never()).delete(anyString());
    }


    @Test
    public void shouldDelete_technicalException() {
        when(identityProviderRepository.findById("my-identity-provider")).thenReturn(Maybe.just(new IdentityProvider()));

        TestObserver testObserver = identityProviderService.delete(DOMAIN, "my-identity-provider").test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        IdentityProvider existingIdentityProvider = Mockito.mock(IdentityProvider.class);
        when(identityProviderRepository.findById("my-identity-provider")).thenReturn(Maybe.just(existingIdentityProvider));
        when(identityProviderRepository.delete("my-identity-provider")).thenReturn(Completable.complete());
        when(applicationService.findByIdentityProvider("my-identity-provider")).thenReturn(Single.just(Collections.emptySet()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = identityProviderService.delete(DOMAIN, "my-identity-provider").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(identityProviderRepository, times(1)).delete("my-identity-provider");
        verify(eventService, times(1)).create(any());
    }
}
