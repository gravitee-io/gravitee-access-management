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
import io.gravitee.am.model.BundleComponentRef;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        request.setPolicySets(List.of(new BundleComponentRef("ps-1", 1, true)));
        request.setEntityStores(List.of(new BundleComponentRef("es-1", 1, false)));

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
            assertEquals(1, b.getPolicySets().size());
            assertEquals("ps-1", b.getPolicySets().get(0).getId());
            assertEquals(1, b.getEntityStores().size());
            assertEquals("es-1", b.getEntityStores().get(0).getId());
            assertNotNull(b.getId());
            assertNotNull(b.getCreatedAt());
            return true;
        });

        verify(eventService).create(any(Event.class), any(Domain.class));
        verify(auditService).report(isA(AuthorizationBundleAuditBuilder.class));
    }

    @Test
    void shouldCreateWithEmptyLists() {
        Domain domain = createMockDomain("domain-1");
        NewAuthorizationBundle request = new NewAuthorizationBundle();
        request.setName("my-bundle");
        request.setEngineType("cedar");
        // No policySets or entityStores set — should default to empty lists

        when(authorizationBundleRepository.create(any(AuthorizationBundle.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(Event.class), any(Domain.class)))
                .thenReturn(Single.just(new Event()));

        TestObserver<AuthorizationBundle> testObserver = service.create(domain, request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(b -> {
            assertNotNull(b.getPolicySets());
            assertEquals(0, b.getPolicySets().size());
            assertNotNull(b.getEntityStores());
            assertEquals(0, b.getEntityStores().size());
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
        existingBundle.setPolicySets(List.of(new BundleComponentRef("ps-1", 1, true)));
        existingBundle.setEntityStores(List.of(new BundleComponentRef("es-1", 1, false)));
        existingBundle.setCreatedAt(new Date());

        UpdateAuthorizationBundle request = new UpdateAuthorizationBundle();
        request.setName("new-name");
        request.setPolicySets(List.of(
                new BundleComponentRef("ps-1", 1, true),
                new BundleComponentRef("ps-2", 3, false)));

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
            assertEquals(2, b.getPolicySets().size());
            assertEquals("ps-2", b.getPolicySets().get(1).getId());
            // Entity stores unchanged (null in request = keep existing)
            assertEquals(1, b.getEntityStores().size());
            assertEquals("es-1", b.getEntityStores().get(0).getId());
            return true;
        });

        verify(auditService).report(isA(AuthorizationBundleAuditBuilder.class));
    }

    @Test
    void shouldNotUpdateWhenBundleNotFound() {
        Domain domain = createMockDomain("domain-1");
        UpdateAuthorizationBundle request = new UpdateAuthorizationBundle();
        request.setPolicySets(List.of(new BundleComponentRef("ps-2", 1, true)));

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
