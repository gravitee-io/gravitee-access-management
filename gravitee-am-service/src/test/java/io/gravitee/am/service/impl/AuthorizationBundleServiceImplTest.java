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
package io.gravitee.am.service.impl;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.AuthorizationBundle;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.AuthorizationBundleRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AuthorizationBundleNotFoundException;
import io.gravitee.am.service.model.NewAuthorizationBundle;
import io.gravitee.am.service.model.UpdateAuthorizationBundle;
import io.gravitee.am.service.reporter.builder.management.AuthorizationBundleAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationBundleServiceImplTest {

    @Mock
    private AuthorizationBundleRepository authorizationBundleRepository;

    @Mock
    private EventService eventService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthorizationBundleServiceImpl service;

    private final User principal = new DefaultUser("test-user");

    private Domain createMockDomain(String domainId) {
        Domain domain = new Domain();
        domain.setId(domainId);
        return domain;
    }

    // --- findByDomain ---

    @Test
    void shouldFindByDomain() {
        AuthorizationBundle bundle = new AuthorizationBundle();
        bundle.setId("bundle-1");
        bundle.setDomainId("domain-1");

        when(authorizationBundleRepository.findByDomain("domain-1"))
                .thenReturn(Flowable.just(bundle));

        TestSubscriber<AuthorizationBundle> testSubscriber = service.findByDomain("domain-1").test();
        testSubscriber.awaitDone(5, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(b -> b.getId().equals("bundle-1"));
    }

    // --- findByDomainAndId ---

    @Test
    void shouldFindByDomainAndId() {
        AuthorizationBundle bundle = new AuthorizationBundle();
        bundle.setId("bundle-1");
        bundle.setDomainId("domain-1");

        when(authorizationBundleRepository.findByDomainAndId("domain-1", "bundle-1"))
                .thenReturn(Maybe.just(bundle));

        TestObserver<AuthorizationBundle> testObserver = service.findByDomainAndId("domain-1", "bundle-1").test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValue(b -> b.getId().equals("bundle-1"));
    }

    // --- create ---

    @Test
    void shouldCreate() {
        Domain domain = createMockDomain("domain-1");
        NewAuthorizationBundle request = new NewAuthorizationBundle();
        request.setName("my-bundle");
        request.setEngineType("cedar");
        request.setPolicySetId("ps-1");
        request.setPolicySetVersion(1);
        request.setSchemaId("schema-1");
        request.setSchemaVersion(1);
        request.setEntityStoreId("es-1");
        request.setEntityStoreVersion(1);

        when(authorizationBundleRepository.create(any(AuthorizationBundle.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(Event.class), any(Domain.class)))
                .thenReturn(Single.just(new Event()));

        TestObserver<AuthorizationBundle> testObserver = service.create(domain, request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValueCount(1);

        testObserver.assertValue(b -> {
            assertEquals("my-bundle", b.getName());
            assertEquals("cedar", b.getEngineType());
            assertEquals("ps-1", b.getPolicySetId());
            assertEquals(1, b.getPolicySetVersion());
            assertEquals("schema-1", b.getSchemaId());
            assertEquals("es-1", b.getEntityStoreId());
            assertNotNull(b.getId());
            assertNotNull(b.getCreatedAt());
            return true;
        });

        verify(eventService).create(any(Event.class), any(Domain.class));
        verify(auditService).report(isA(AuthorizationBundleAuditBuilder.class));
    }

    @Test
    void shouldCreateWithPinToLatest() {
        Domain domain = createMockDomain("domain-1");
        NewAuthorizationBundle request = new NewAuthorizationBundle();
        request.setName("my-bundle");
        request.setEngineType("cedar");
        request.setPolicySetId("ps-1");
        request.setPolicySetVersion(1);
        request.setPolicySetPinToLatest(true);
        request.setSchemaId("schema-1");
        request.setSchemaVersion(1);
        request.setSchemaPinToLatest(true);
        request.setEntityStoreId("es-1");
        request.setEntityStoreVersion(1);
        request.setEntityStorePinToLatest(false);

        when(authorizationBundleRepository.create(any(AuthorizationBundle.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(Event.class), any(Domain.class)))
                .thenReturn(Single.just(new Event()));

        TestObserver<AuthorizationBundle> testObserver = service.create(domain, request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(b -> {
            assertTrue(b.isPolicySetPinToLatest());
            assertTrue(b.isSchemaPinToLatest());
            assertFalse(b.isEntityStorePinToLatest());
            return true;
        });
    }

    // --- update ---

    @Test
    void shouldUpdate() {
        Domain domain = createMockDomain("domain-1");
        AuthorizationBundle existingBundle = new AuthorizationBundle();
        existingBundle.setId("bundle-1");
        existingBundle.setDomainId("domain-1");
        existingBundle.setName("old-name");
        existingBundle.setEngineType("cedar");
        existingBundle.setPolicySetId("ps-1");
        existingBundle.setPolicySetVersion(1);
        existingBundle.setSchemaId("schema-1");
        existingBundle.setSchemaVersion(1);
        existingBundle.setEntityStoreId("es-1");
        existingBundle.setEntityStoreVersion(1);
        existingBundle.setCreatedAt(new Date());

        UpdateAuthorizationBundle request = new UpdateAuthorizationBundle();
        request.setName("new-name");
        request.setPolicySetId("ps-2");
        request.setPolicySetVersion(3);

        when(authorizationBundleRepository.findByDomainAndId("domain-1", "bundle-1"))
                .thenReturn(Maybe.just(existingBundle));
        when(authorizationBundleRepository.update(any(AuthorizationBundle.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(Event.class), any(Domain.class)))
                .thenReturn(Single.just(new Event()));

        TestObserver<AuthorizationBundle> testObserver = service.update(domain, "bundle-1", request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(b -> {
            assertEquals("new-name", b.getName());
            assertEquals("ps-2", b.getPolicySetId());
            assertEquals(3, b.getPolicySetVersion());
            // Schema and entity store unchanged (null in request = keep existing)
            assertEquals("schema-1", b.getSchemaId());
            assertEquals(1, b.getSchemaVersion());
            assertEquals("es-1", b.getEntityStoreId());
            return true;
        });

        verify(auditService).report(isA(AuthorizationBundleAuditBuilder.class));
    }

    @Test
    void shouldUpdatePinToLatest() {
        Domain domain = createMockDomain("domain-1");
        AuthorizationBundle existingBundle = new AuthorizationBundle();
        existingBundle.setId("bundle-1");
        existingBundle.setDomainId("domain-1");
        existingBundle.setName("my-bundle");
        existingBundle.setEngineType("cedar");
        existingBundle.setPolicySetId("ps-1");
        existingBundle.setPolicySetVersion(1);
        existingBundle.setPolicySetPinToLatest(false);
        existingBundle.setSchemaPinToLatest(false);
        existingBundle.setEntityStorePinToLatest(false);
        existingBundle.setCreatedAt(new Date());

        UpdateAuthorizationBundle request = new UpdateAuthorizationBundle();
        request.setPolicySetPinToLatest(true);
        request.setSchemaPinToLatest(true);

        when(authorizationBundleRepository.findByDomainAndId("domain-1", "bundle-1"))
                .thenReturn(Maybe.just(existingBundle));
        when(authorizationBundleRepository.update(any(AuthorizationBundle.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(Event.class), any(Domain.class)))
                .thenReturn(Single.just(new Event()));

        TestObserver<AuthorizationBundle> testObserver = service.update(domain, "bundle-1", request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(b -> {
            assertTrue(b.isPolicySetPinToLatest());
            assertTrue(b.isSchemaPinToLatest());
            // entityStorePinToLatest not in request, should remain false
            assertFalse(b.isEntityStorePinToLatest());
            return true;
        });
    }

    @Test
    void shouldNotUpdateWhenBundleNotFound() {
        Domain domain = createMockDomain("domain-1");
        UpdateAuthorizationBundle request = new UpdateAuthorizationBundle();
        request.setPolicySetId("ps-2");

        when(authorizationBundleRepository.findByDomainAndId("domain-1", "unknown"))
                .thenReturn(Maybe.empty());

        TestObserver<AuthorizationBundle> testObserver = service.update(domain, "unknown", request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertError(AuthorizationBundleNotFoundException.class);

        verify(authorizationBundleRepository, never()).update(any());
    }

    // --- delete ---

    @Test
    void shouldDelete() {
        Domain domain = createMockDomain("domain-1");
        AuthorizationBundle bundle = new AuthorizationBundle();
        bundle.setId("bundle-1");
        bundle.setDomainId("domain-1");

        when(authorizationBundleRepository.findByDomainAndId("domain-1", "bundle-1"))
                .thenReturn(Maybe.just(bundle));
        when(authorizationBundleRepository.delete("bundle-1"))
                .thenReturn(Completable.complete());
        when(eventService.create(any(Event.class), any(Domain.class)))
                .thenReturn(Single.just(new Event()));

        TestObserver<Void> testObserver = service.delete(domain, "bundle-1", principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        verify(authorizationBundleRepository).delete("bundle-1");
        verify(eventService).create(any(Event.class), any(Domain.class));
        verify(auditService).report(isA(AuthorizationBundleAuditBuilder.class));
    }

    @Test
    void shouldNotDeleteWhenBundleNotFound() {
        Domain domain = createMockDomain("domain-1");

        when(authorizationBundleRepository.findByDomainAndId("domain-1", "unknown"))
                .thenReturn(Maybe.empty());

        TestObserver<Void> testObserver = service.delete(domain, "unknown", principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertError(AuthorizationBundleNotFoundException.class);

        verify(authorizationBundleRepository, never()).delete(any());
    }
}
