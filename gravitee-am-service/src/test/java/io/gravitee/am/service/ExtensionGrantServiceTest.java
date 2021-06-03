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
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.gravitee.am.service.exception.ExtensionGrantAlreadyExistsException;
import io.gravitee.am.service.exception.ExtensionGrantNotFoundException;
import io.gravitee.am.service.exception.ExtensionGrantWithApplicationsException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.ExtensionGrantServiceImpl;
import io.gravitee.am.service.model.NewExtensionGrant;
import io.gravitee.am.service.model.UpdateExtensionGrant;
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
import java.util.Date;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ExtensionGrantServiceTest {

    @InjectMocks
    private ExtensionGrantService extensionGrantService = new ExtensionGrantServiceImpl();

    @Mock
    private EventService eventService;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private ExtensionGrantRepository extensionGrantRepository;

    @Mock
    private AuditService auditService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindById() {
        when(extensionGrantRepository.findById("my-extension-grant")).thenReturn(Maybe.just(new ExtensionGrant()));
        TestObserver testObserver = extensionGrantService.findById("my-extension-grant").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingExtensionGrant() {
        when(extensionGrantRepository.findById("my-extension-grant")).thenReturn(Maybe.empty());
        TestObserver testObserver = extensionGrantService.findById("my-extension-grant").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(extensionGrantRepository.findById("my-extension-grant")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        extensionGrantService.findById("my-extension-grant").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindByDomain() {
        when(extensionGrantRepository.findByDomain(DOMAIN)).thenReturn(Flowable.just(new ExtensionGrant()));
        TestSubscriber<ExtensionGrant> testSubscriber = extensionGrantService.findByDomain(DOMAIN).test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }

    @Test
    public void shouldFindByDomain_technicalException() {
        when(extensionGrantRepository.findByDomain(DOMAIN)).thenReturn(Flowable.error(TechnicalException::new));

        TestSubscriber testSubscriber = extensionGrantService.findByDomain(DOMAIN).test();

        testSubscriber.assertError(TechnicalManagementException.class);
        testSubscriber.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        NewExtensionGrant newExtensionGrant = Mockito.mock(NewExtensionGrant.class);
        when(newExtensionGrant.getName()).thenReturn("my-extension-grant");
        when(extensionGrantRepository.findByDomainAndName(DOMAIN, "my-extension-grant")).thenReturn(Maybe.empty());
        when(extensionGrantRepository.create(any(ExtensionGrant.class))).thenReturn(Single.just(new ExtensionGrant()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = extensionGrantService.create(DOMAIN, newExtensionGrant).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(extensionGrantRepository, times(1)).findByDomainAndName(anyString(), anyString());
        verify(extensionGrantRepository, times(1)).create(any(ExtensionGrant.class));
    }

    @Test
    public void shouldCreate_technicalException() {
        NewExtensionGrant newExtensionGrant = Mockito.mock(NewExtensionGrant.class);
        when(newExtensionGrant.getName()).thenReturn("my-extension-grant");
        when(extensionGrantRepository.findByDomainAndName(DOMAIN, "my-extension-grant")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver<ExtensionGrant> testObserver = new TestObserver<>();
        extensionGrantService.create(DOMAIN, newExtensionGrant).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(extensionGrantRepository, never()).create(any(ExtensionGrant.class));
    }

    @Test
    public void shouldCreate2_technicalException() {
        NewExtensionGrant newExtensionGrant = Mockito.mock(NewExtensionGrant.class);
        when(newExtensionGrant.getName()).thenReturn("my-extension-grant");
        when(extensionGrantRepository.findByDomainAndName(DOMAIN, "my-extension-grant")).thenReturn(Maybe.empty());
        when(extensionGrantRepository.create(any(ExtensionGrant.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver<ExtensionGrant> testObserver = new TestObserver<>();
        extensionGrantService.create(DOMAIN, newExtensionGrant).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(extensionGrantRepository, times(1)).findByDomainAndName(anyString(), anyString());
    }

    @Test
    public void shouldCreate_existingExtensionGrant() {
        NewExtensionGrant newExtensionGrant = Mockito.mock(NewExtensionGrant.class);
        when(newExtensionGrant.getName()).thenReturn("my-extension-grant");
        when(extensionGrantRepository.findByDomainAndName(DOMAIN, "my-extension-grant")).thenReturn(Maybe.just(new ExtensionGrant()));

        TestObserver<ExtensionGrant> testObserver = new TestObserver<>();
        extensionGrantService.create(DOMAIN, newExtensionGrant).subscribe(testObserver);

        testObserver.assertError(ExtensionGrantAlreadyExistsException.class);
        testObserver.assertNotComplete();

        verify(extensionGrantRepository, never()).create(any(ExtensionGrant.class));
    }

    @Test
    public void shouldUpdate() {
        UpdateExtensionGrant updateExtensionGrant = Mockito.mock(UpdateExtensionGrant.class);
        when(updateExtensionGrant.getName()).thenReturn("my-extension-grant");
        when(extensionGrantRepository.findById("my-extension-grant")).thenReturn(Maybe.just(new ExtensionGrant()));
        when(extensionGrantRepository.findByDomainAndName(DOMAIN, "my-extension-grant")).thenReturn(Maybe.empty());
        when(extensionGrantRepository.update(any(ExtensionGrant.class))).thenReturn(Single.just(new ExtensionGrant()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = extensionGrantService.update(DOMAIN, "my-extension-grant", updateExtensionGrant).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(extensionGrantRepository, times(1)).findById(anyString());
        verify(extensionGrantRepository, times(1)).findByDomainAndName(anyString(), anyString());
        verify(extensionGrantRepository, times(1)).update(any(ExtensionGrant.class));
    }

    @Test
    public void shouldUpdate_technicalException() {
        UpdateExtensionGrant updateExtensionGrant = Mockito.mock(UpdateExtensionGrant.class);
        when(extensionGrantRepository.findById("my-extension-grant")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = extensionGrantService.update(DOMAIN, "my-extension-grant", updateExtensionGrant).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(extensionGrantRepository, times(1)).findById(anyString());
        verify(extensionGrantRepository, never()).findByDomainAndName(anyString(), anyString());
        verify(extensionGrantRepository, never()).update(any(ExtensionGrant.class));
    }

    @Test
    public void shouldUpdate2_technicalException() {
        UpdateExtensionGrant updateExtensionGrant = Mockito.mock(UpdateExtensionGrant.class);
        when(updateExtensionGrant.getName()).thenReturn("my-extension-grant");
        when(extensionGrantRepository.findById("my-extension-grant")).thenReturn(Maybe.just(new ExtensionGrant()));
        when(extensionGrantRepository.findByDomainAndName(DOMAIN, "my-extension-grant")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = extensionGrantService.update(DOMAIN, "my-extension-grant", updateExtensionGrant).test();
        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(extensionGrantRepository, times(1)).findById(anyString());
        verify(extensionGrantRepository, times(1)).findByDomainAndName(anyString(), anyString());
        verify(extensionGrantRepository, never()).update(any(ExtensionGrant.class));
    }

    @Test
    public void shouldDelete_notExistingExtensionGrant() {
        when(extensionGrantRepository.findById("my-extension-grant")).thenReturn(Maybe.empty());

        TestObserver testObserver = extensionGrantService.delete(DOMAIN, "my-extension-grant").test();

        testObserver.assertError(ExtensionGrantNotFoundException.class);
        testObserver.assertNotComplete();

        verify(applicationService, never()).findByDomainAndExtensionGrant(eq(DOMAIN), anyString());
        verify(extensionGrantRepository, never()).delete(anyString());
    }

    @Test
    public void shouldNotDelete_extensionGrantWithClients() {
        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setId("extension-grant-id");
        extensionGrant.setGrantType("extension-grant-type");
        when(extensionGrantRepository.findById(extensionGrant.getId())).thenReturn(Maybe.just(extensionGrant));
        when(applicationService.findByDomainAndExtensionGrant(DOMAIN, extensionGrant.getGrantType() + "~" + extensionGrant.getId())).thenReturn(Single.just(Collections.singleton(new Application())));

        TestObserver testObserver = extensionGrantService.delete(DOMAIN, extensionGrant.getId()).test();

        testObserver.assertError(ExtensionGrantWithApplicationsException.class);
        testObserver.assertNotComplete();

        verify(extensionGrantRepository, never()).delete(anyString());
    }

    @Test
    public void shouldNotDelete_extensionGrantWithClients_backward_compatibility() {
        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setId("extension-grant-id");
        extensionGrant.setGrantType("extension-grant-type");
        extensionGrant.setCreatedAt(new Date());

        ExtensionGrant extensionGrant2 = new ExtensionGrant();
        extensionGrant2.setId("extension-grant-id-2");
        extensionGrant2.setGrantType("extension-grant-type");
        extensionGrant2.setCreatedAt(new Date());

        when(extensionGrantRepository.findById(extensionGrant.getId())).thenReturn(Maybe.just(extensionGrant));
        when(applicationService.findByDomainAndExtensionGrant(DOMAIN, extensionGrant.getGrantType() + "~" + extensionGrant.getId())).thenReturn(Single.just(Collections.emptySet()));
        when(applicationService.findByDomainAndExtensionGrant(DOMAIN, extensionGrant.getGrantType())).thenReturn(Single.just(Collections.singleton(new Application())));
        when(extensionGrantRepository.findByDomain(DOMAIN)).thenReturn(Flowable.just(extensionGrant, extensionGrant2));
        TestObserver testObserver = extensionGrantService.delete(DOMAIN, extensionGrant.getId()).test();

        testObserver.assertError(ExtensionGrantWithApplicationsException.class);
        testObserver.assertNotComplete();

        verify(extensionGrantRepository, never()).delete(anyString());
    }

    @Test
    public void shouldDelete_extensionGrantWithClients_backward_compatibility() {
        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setId("extension-grant-id");
        extensionGrant.setGrantType("extension-grant-type");
        extensionGrant.setCreatedAt(new Date(System.currentTimeMillis() - 60 * 1000));

        ExtensionGrant extensionGrant2 = new ExtensionGrant();
        extensionGrant2.setId("extension-grant-id-2");
        extensionGrant2.setGrantType("extension-grant-type");
        extensionGrant2.setCreatedAt(new Date());

        when(extensionGrantRepository.findById(extensionGrant2.getId())).thenReturn(Maybe.just(extensionGrant2));
        when(extensionGrantRepository.delete(extensionGrant2.getId())).thenReturn(Completable.complete());
        when(applicationService.findByDomainAndExtensionGrant(DOMAIN, extensionGrant2.getGrantType() + "~" + extensionGrant2.getId())).thenReturn(Single.just(Collections.emptySet()));
        when(applicationService.findByDomainAndExtensionGrant(DOMAIN, extensionGrant2.getGrantType())).thenReturn(Single.just(Collections.singleton(new Application())));
        when(extensionGrantRepository.findByDomain(DOMAIN)).thenReturn(Flowable.just(extensionGrant, extensionGrant2));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        TestObserver testObserver = extensionGrantService.delete(DOMAIN, extensionGrant2.getId()).test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(extensionGrantRepository, times(1)).delete(extensionGrant2.getId());
    }

    @Test
    public void shouldDelete_technicalException() {
        when(extensionGrantRepository.findById("my-extension-grant")).thenReturn(Maybe.just(new ExtensionGrant()));

        TestObserver testObserver = extensionGrantService.delete(DOMAIN, "my-extension-grant").test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldDelete() {
        ExtensionGrant existingExtensionGrant = Mockito.mock(ExtensionGrant.class);
        when(existingExtensionGrant.getId()).thenReturn("my-extension-grant");
        when(existingExtensionGrant.getGrantType()).thenReturn("my-extension-grant");
        when(extensionGrantRepository.findById("my-extension-grant")).thenReturn(Maybe.just(existingExtensionGrant));
        when(extensionGrantRepository.delete("my-extension-grant")).thenReturn(Completable.complete());
        when(extensionGrantRepository.findByDomain(DOMAIN)).thenReturn(Flowable.just(existingExtensionGrant));
        when(applicationService.findByDomainAndExtensionGrant(DOMAIN, "my-extension-grant~my-extension-grant")).thenReturn(Single.just(Collections.emptySet()));
        when(applicationService.findByDomainAndExtensionGrant(DOMAIN, "my-extension-grant")).thenReturn(Single.just(Collections.emptySet()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = extensionGrantService.delete(DOMAIN, "my-extension-grant").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(extensionGrantRepository, times(1)).delete("my-extension-grant");
    }
}
