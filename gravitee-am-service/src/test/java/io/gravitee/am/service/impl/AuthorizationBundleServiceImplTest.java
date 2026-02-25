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
import io.gravitee.am.model.AuthorizationBundleVersion;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.AuthorizationBundleRepository;
import io.gravitee.am.repository.management.api.AuthorizationBundleVersionRepository;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.AuthorizationBundleNotFoundException;
import io.gravitee.am.service.exception.AuthorizationBundleVersionNotFoundException;
import io.gravitee.am.service.model.NewAuthorizationBundle;
import io.gravitee.am.service.model.UpdateAuthorizationBundle;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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
    private AuthorizationBundleVersionRepository authorizationBundleVersionRepository;

    @Mock
    private EventService eventService;

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
        testSubscriber.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
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
        testObserver.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
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
        request.setPolicies("permit(principal, action, resource);");
        request.setSchema("{\"entityTypes\":{}}");
        request.setEntities("[]");

        when(authorizationBundleRepository.create(any(AuthorizationBundle.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(authorizationBundleVersionRepository.create(any(AuthorizationBundleVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(Event.class), any(Domain.class)))
                .thenReturn(Single.just(new Event()));

        TestObserver<AuthorizationBundle> testObserver = service.create(domain, request, principal).test();
        testObserver.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValueCount(1);

        testObserver.assertValue(b -> {
            assertEquals("my-bundle", b.getName());
            assertEquals("cedar", b.getEngineType());
            assertEquals("permit(principal, action, resource);", b.getPolicies());
            assertEquals("{\"entityTypes\":{}}", b.getSchema());
            assertEquals("[]", b.getEntities());
            assertEquals(1, b.getVersion());
            assertNotNull(b.getId());
            assertNotNull(b.getCreatedAt());
            return true;
        });

        // Verify version record was created
        ArgumentCaptor<AuthorizationBundleVersion> versionCaptor = ArgumentCaptor.forClass(AuthorizationBundleVersion.class);
        verify(authorizationBundleVersionRepository).create(versionCaptor.capture());
        AuthorizationBundleVersion versionRecord = versionCaptor.getValue();
        assertEquals(1, versionRecord.getVersion());
        assertEquals("Initial version", versionRecord.getComment());
        assertEquals("permit(principal, action, resource);", versionRecord.getPolicies());

        // Verify event was published
        verify(eventService).create(any(Event.class), any(Domain.class));
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
        existingBundle.setPolicies("old-policy");
        existingBundle.setSchema("old-schema");
        existingBundle.setEntities("old-entities");
        existingBundle.setVersion(2);
        existingBundle.setCreatedAt(new Date());

        UpdateAuthorizationBundle request = new UpdateAuthorizationBundle();
        request.setName("new-name");
        request.setPolicies("new-policy");
        request.setComment("Updated policies");

        when(authorizationBundleRepository.findByDomainAndId("domain-1", "bundle-1"))
                .thenReturn(Maybe.just(existingBundle));
        when(authorizationBundleRepository.update(any(AuthorizationBundle.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(authorizationBundleVersionRepository.create(any(AuthorizationBundleVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(Event.class), any(Domain.class)))
                .thenReturn(Single.just(new Event()));

        TestObserver<AuthorizationBundle> testObserver = service.update(domain, "bundle-1", request, principal).test();
        testObserver.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(b -> {
            assertEquals("new-name", b.getName());
            assertEquals("new-policy", b.getPolicies());
            // Schema and entities unchanged (null in request = keep existing)
            assertEquals("old-schema", b.getSchema());
            assertEquals("old-entities", b.getEntities());
            assertEquals(3, b.getVersion());
            return true;
        });

        ArgumentCaptor<AuthorizationBundleVersion> versionCaptor = ArgumentCaptor.forClass(AuthorizationBundleVersion.class);
        verify(authorizationBundleVersionRepository).create(versionCaptor.capture());
        assertEquals("Updated policies", versionCaptor.getValue().getComment());
        assertEquals(3, versionCaptor.getValue().getVersion());
    }

    @Test
    void shouldNotUpdateWhenBundleNotFound() {
        Domain domain = createMockDomain("domain-1");
        UpdateAuthorizationBundle request = new UpdateAuthorizationBundle();
        request.setPolicies("new-policy");

        when(authorizationBundleRepository.findByDomainAndId("domain-1", "unknown"))
                .thenReturn(Maybe.empty());

        TestObserver<AuthorizationBundle> testObserver = service.update(domain, "unknown", request, principal).test();
        testObserver.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
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
        when(authorizationBundleVersionRepository.deleteByBundleId("bundle-1"))
                .thenReturn(Completable.complete());
        when(authorizationBundleRepository.delete("bundle-1"))
                .thenReturn(Completable.complete());
        when(eventService.create(any(Event.class), any(Domain.class)))
                .thenReturn(Single.just(new Event()));

        TestObserver<Void> testObserver = service.delete(domain, "bundle-1", principal).test();
        testObserver.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        testObserver.assertComplete();

        verify(authorizationBundleVersionRepository).deleteByBundleId("bundle-1");
        verify(authorizationBundleRepository).delete("bundle-1");
        verify(eventService).create(any(Event.class), any(Domain.class));
    }

    @Test
    void shouldNotDeleteWhenBundleNotFound() {
        Domain domain = createMockDomain("domain-1");

        when(authorizationBundleRepository.findByDomainAndId("domain-1", "unknown"))
                .thenReturn(Maybe.empty());

        TestObserver<Void> testObserver = service.delete(domain, "unknown", principal).test();
        testObserver.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        testObserver.assertError(AuthorizationBundleNotFoundException.class);

        verify(authorizationBundleRepository, never()).delete(any());
    }

    // --- rollback ---

    @Test
    void shouldRollback() {
        Domain domain = createMockDomain("domain-1");
        AuthorizationBundle existingBundle = new AuthorizationBundle();
        existingBundle.setId("bundle-1");
        existingBundle.setDomainId("domain-1");
        existingBundle.setName("my-bundle");
        existingBundle.setEngineType("cedar");
        existingBundle.setPolicies("current-policy");
        existingBundle.setSchema("current-schema");
        existingBundle.setEntities("current-entities");
        existingBundle.setVersion(5);
        existingBundle.setCreatedAt(new Date());

        AuthorizationBundleVersion targetVersion = new AuthorizationBundleVersion();
        targetVersion.setVersion(2);
        targetVersion.setPolicies("old-policy-v2");
        targetVersion.setSchema("old-schema-v2");
        targetVersion.setEntities("old-entities-v2");

        when(authorizationBundleRepository.findByDomainAndId("domain-1", "bundle-1"))
                .thenReturn(Maybe.just(existingBundle));
        when(authorizationBundleVersionRepository.findByBundleIdAndVersion("bundle-1", 2))
                .thenReturn(Maybe.just(targetVersion));
        when(authorizationBundleRepository.update(any(AuthorizationBundle.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(authorizationBundleVersionRepository.create(any(AuthorizationBundleVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(eventService.create(any(Event.class), any(Domain.class)))
                .thenReturn(Single.just(new Event()));

        TestObserver<AuthorizationBundle> testObserver = service.rollback(domain, "bundle-1", 2, principal).test();
        testObserver.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(b -> {
            // Content restored from version 2
            assertEquals("old-policy-v2", b.getPolicies());
            assertEquals("old-schema-v2", b.getSchema());
            assertEquals("old-entities-v2", b.getEntities());
            // Version incremented (not replaced)
            assertEquals(6, b.getVersion());
            return true;
        });

        ArgumentCaptor<AuthorizationBundleVersion> versionCaptor = ArgumentCaptor.forClass(AuthorizationBundleVersion.class);
        verify(authorizationBundleVersionRepository).create(versionCaptor.capture());
        assertEquals("Rollback to version 2", versionCaptor.getValue().getComment());
        assertEquals(6, versionCaptor.getValue().getVersion());
    }

    @Test
    void shouldNotRollbackWhenVersionNotFound() {
        Domain domain = createMockDomain("domain-1");
        AuthorizationBundle existingBundle = new AuthorizationBundle();
        existingBundle.setId("bundle-1");
        existingBundle.setDomainId("domain-1");
        existingBundle.setVersion(3);
        existingBundle.setCreatedAt(new Date());

        when(authorizationBundleRepository.findByDomainAndId("domain-1", "bundle-1"))
                .thenReturn(Maybe.just(existingBundle));
        when(authorizationBundleVersionRepository.findByBundleIdAndVersion("bundle-1", 99))
                .thenReturn(Maybe.empty());

        TestObserver<AuthorizationBundle> testObserver = service.rollback(domain, "bundle-1", 99, principal).test();
        testObserver.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        testObserver.assertError(AuthorizationBundleVersionNotFoundException.class);

        verify(authorizationBundleRepository, never()).update(any());
    }

    // --- getVersionHistory ---

    @Test
    void shouldGetVersionHistory() {
        AuthorizationBundleVersion v1 = new AuthorizationBundleVersion();
        v1.setVersion(1);
        AuthorizationBundleVersion v2 = new AuthorizationBundleVersion();
        v2.setVersion(2);

        when(authorizationBundleVersionRepository.findByBundleId("bundle-1"))
                .thenReturn(Flowable.just(v1, v2));

        TestSubscriber<AuthorizationBundleVersion> testSubscriber = service.getVersionHistory("bundle-1").test();
        testSubscriber.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertValueCount(2);
    }

    // --- getVersion ---

    @Test
    void shouldGetSpecificVersion() {
        AuthorizationBundleVersion v2 = new AuthorizationBundleVersion();
        v2.setVersion(2);
        v2.setPolicies("policy-v2");

        when(authorizationBundleVersionRepository.findByBundleIdAndVersion("bundle-1", 2))
                .thenReturn(Maybe.just(v2));

        TestObserver<AuthorizationBundleVersion> testObserver = service.getVersion("bundle-1", 2).test();
        testObserver.awaitDone(5, java.util.concurrent.TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValue(v -> v.getVersion() == 2 && "policy-v2".equals(v.getPolicies()));
    }
}
