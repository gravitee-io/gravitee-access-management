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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.EntityStore;
import io.gravitee.am.model.EntityStoreVersion;
import io.gravitee.am.repository.management.api.EntityStoreRepository;
import io.gravitee.am.repository.management.api.EntityStoreVersionRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.EntityStoreNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewEntityStore;
import io.gravitee.am.service.model.UpdateEntityStore;
import io.gravitee.am.service.reporter.builder.management.EntityStoreAuditBuilder;
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
class EntityStoreServiceImplTest {

    @Mock
    private EntityStoreRepository entityStoreRepository;

    @Mock
    private EntityStoreVersionRepository entityStoreVersionRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private EntityStoreServiceImpl service;

    private final User principal = new DefaultUser("test-user");

    private Domain createMockDomain(String domainId) {
        Domain domain = new Domain();
        domain.setId(domainId);
        return domain;
    }

    // --- findByDomain ---

    @Test
    void shouldFindByDomain() {
        EntityStore es = new EntityStore();
        es.setId("es-1");
        es.setDomainId("domain-1");

        when(entityStoreRepository.findByDomain("domain-1"))
                .thenReturn(Flowable.just(es));

        TestSubscriber<EntityStore> testSubscriber = service.findByDomain("domain-1").test();
        testSubscriber.awaitDone(5, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(e -> e.getId().equals("es-1"));
    }

    @Test
    void shouldWrapFindByDomainError() {
        when(entityStoreRepository.findByDomain("domain-1"))
                .thenReturn(Flowable.error(new RuntimeException("db error")));

        TestSubscriber<EntityStore> testSubscriber = service.findByDomain("domain-1").test();
        testSubscriber.awaitDone(5, TimeUnit.SECONDS);
        testSubscriber.assertError(TechnicalManagementException.class);
    }

    // --- findById ---

    @Test
    void shouldFindById() {
        EntityStore es = new EntityStore();
        es.setId("es-1");

        when(entityStoreRepository.findById("es-1"))
                .thenReturn(Maybe.just(es));

        TestObserver<EntityStore> testObserver = service.findById("es-1").test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValue(e -> e.getId().equals("es-1"));
    }

    // --- findByDomainAndId ---

    @Test
    void shouldFindByDomainAndId() {
        EntityStore es = new EntityStore();
        es.setId("es-1");
        es.setDomainId("domain-1");

        when(entityStoreRepository.findByDomainAndId("domain-1", "es-1"))
                .thenReturn(Maybe.just(es));

        TestObserver<EntityStore> testObserver = service.findByDomainAndId("domain-1", "es-1").test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValue(e -> e.getId().equals("es-1"));
    }

    // --- create ---

    @Test
    void shouldCreate() {
        Domain domain = createMockDomain("domain-1");
        NewEntityStore request = new NewEntityStore();
        request.setName("my-entity-store");
        request.setContent("[{\"uid\": \"user1\"}]");
        request.setCommitMessage("Initial version");

        when(entityStoreRepository.create(any(EntityStore.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(entityStoreVersionRepository.create(any(EntityStoreVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<EntityStore> testObserver = service.create(domain, request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValueCount(1);

        testObserver.assertValue(e -> {
            assertEquals("my-entity-store", e.getName());
            assertEquals("domain-1", e.getDomainId());
            assertEquals(1, e.getLatestVersion());
            assertNotNull(e.getId());
            assertNotNull(e.getCreatedAt());
            assertEquals(e.getCreatedAt(), e.getUpdatedAt());
            return true;
        });

        verify(entityStoreVersionRepository).create(any(EntityStoreVersion.class));
        verify(auditService).report(isA(EntityStoreAuditBuilder.class));
    }

    // --- update ---

    @Test
    void shouldUpdate() {
        Domain domain = createMockDomain("domain-1");
        EntityStore existing = new EntityStore();
        existing.setId("es-1");
        existing.setDomainId("domain-1");
        existing.setName("old-name");
        existing.setLatestVersion(2);
        existing.setCreatedAt(new Date());

        UpdateEntityStore request = new UpdateEntityStore();
        request.setName("new-name");
        request.setContent("new-content");
        request.setCommitMessage("Updated entities");

        when(entityStoreRepository.findByDomainAndId("domain-1", "es-1"))
                .thenReturn(Maybe.just(existing));
        when(entityStoreRepository.update(any(EntityStore.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(entityStoreVersionRepository.create(any(EntityStoreVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<EntityStore> testObserver = service.update(domain, "es-1", request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(e -> {
            assertEquals("new-name", e.getName());
            assertEquals(3, e.getLatestVersion());
            assertNotNull(e.getUpdatedAt());
            return true;
        });

        verify(entityStoreVersionRepository).create(any(EntityStoreVersion.class));
        verify(auditService).report(isA(EntityStoreAuditBuilder.class));
    }

    @Test
    void shouldUpdateNameOnlyAndCopyPreviousContent() {
        Domain domain = createMockDomain("domain-1");
        EntityStore existing = new EntityStore();
        existing.setId("es-1");
        existing.setDomainId("domain-1");
        existing.setName("old-name");
        existing.setLatestVersion(1);
        existing.setCreatedAt(new Date());

        EntityStoreVersion previousVersion = new EntityStoreVersion();
        previousVersion.setContent("previous-content");

        UpdateEntityStore request = new UpdateEntityStore();
        request.setName("new-name-only");
        request.setCommitMessage("Renamed");
        // content is null => should copy from previous version

        when(entityStoreRepository.findByDomainAndId("domain-1", "es-1"))
                .thenReturn(Maybe.just(existing));
        when(entityStoreRepository.update(any(EntityStore.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(entityStoreVersionRepository.findLatestByEntityStoreId("es-1"))
                .thenReturn(Maybe.just(previousVersion));
        when(entityStoreVersionRepository.create(any(EntityStoreVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<EntityStore> testObserver = service.update(domain, "es-1", request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(e -> {
            assertEquals("new-name-only", e.getName());
            assertEquals(2, e.getLatestVersion());
            return true;
        });

        verify(entityStoreVersionRepository).findLatestByEntityStoreId("es-1");
        verify(entityStoreVersionRepository).create(any(EntityStoreVersion.class));
    }

    @Test
    void shouldNotUpdateWhenNotFound() {
        Domain domain = createMockDomain("domain-1");
        UpdateEntityStore request = new UpdateEntityStore();
        request.setName("new-name");
        request.setCommitMessage("msg");

        when(entityStoreRepository.findByDomainAndId("domain-1", "unknown"))
                .thenReturn(Maybe.empty());

        TestObserver<EntityStore> testObserver = service.update(domain, "unknown", request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertError(EntityStoreNotFoundException.class);

        verify(entityStoreRepository, never()).update(any());
    }

    // --- delete ---

    @Test
    void shouldDelete() {
        Domain domain = createMockDomain("domain-1");
        EntityStore es = new EntityStore();
        es.setId("es-1");
        es.setDomainId("domain-1");

        when(entityStoreRepository.findByDomainAndId("domain-1", "es-1"))
                .thenReturn(Maybe.just(es));
        when(entityStoreVersionRepository.deleteByEntityStoreId("es-1"))
                .thenReturn(Completable.complete());
        when(entityStoreRepository.delete("es-1"))
                .thenReturn(Completable.complete());

        TestObserver<Void> testObserver = service.delete(domain, "es-1", principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        verify(entityStoreVersionRepository).deleteByEntityStoreId("es-1");
        verify(entityStoreRepository).delete("es-1");
        verify(auditService).report(isA(EntityStoreAuditBuilder.class));
    }

    @Test
    void shouldNotDeleteWhenNotFound() {
        Domain domain = createMockDomain("domain-1");

        when(entityStoreRepository.findByDomainAndId("domain-1", "unknown"))
                .thenReturn(Maybe.empty());

        TestObserver<Void> testObserver = service.delete(domain, "unknown", principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertError(EntityStoreNotFoundException.class);

        verify(entityStoreRepository, never()).delete(any());
    }

    // --- deleteByDomain ---

    @Test
    void shouldDeleteByDomain() {
        EntityStore es = new EntityStore();
        es.setId("es-1");

        when(entityStoreRepository.findByDomain("domain-1"))
                .thenReturn(Flowable.just(es));
        when(entityStoreVersionRepository.deleteByEntityStoreId("es-1"))
                .thenReturn(Completable.complete());
        when(entityStoreRepository.deleteByDomain("domain-1"))
                .thenReturn(Completable.complete());

        TestObserver<Void> testObserver = service.deleteByDomain("domain-1").test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        verify(entityStoreVersionRepository).deleteByEntityStoreId("es-1");
        verify(entityStoreRepository).deleteByDomain("domain-1");
    }

    @Test
    void shouldWrapDeleteByDomainError() {
        when(entityStoreRepository.findByDomain("domain-1"))
                .thenReturn(Flowable.error(new RuntimeException("db error")));
        when(entityStoreRepository.deleteByDomain("domain-1"))
                .thenReturn(Completable.complete());

        TestObserver<Void> testObserver = service.deleteByDomain("domain-1").test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertError(TechnicalManagementException.class);
    }

    // --- getVersions ---

    @Test
    void shouldGetVersions() {
        EntityStoreVersion v1 = new EntityStoreVersion();
        v1.setVersion(1);
        EntityStoreVersion v2 = new EntityStoreVersion();
        v2.setVersion(2);

        when(entityStoreVersionRepository.findByEntityStoreId("es-1"))
                .thenReturn(Flowable.just(v1, v2));

        TestSubscriber<EntityStoreVersion> testSubscriber = service.getVersions("es-1").test();
        testSubscriber.awaitDone(5, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertValueCount(2);
    }

    // --- getVersion ---

    @Test
    void shouldGetVersion() {
        EntityStoreVersion v = new EntityStoreVersion();
        v.setVersion(3);
        v.setContent("[{\"uid\": \"user1\"}]");

        when(entityStoreVersionRepository.findByEntityStoreIdAndVersion("es-1", 3))
                .thenReturn(Maybe.just(v));

        TestObserver<EntityStoreVersion> testObserver = service.getVersion("es-1", 3).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValue(ver -> ver.getVersion() == 3 && ver.getContent().equals("[{\"uid\": \"user1\"}]"));
    }

    // --- restoreVersion ---

    @Test
    void shouldRestoreVersion() {
        Domain domain = createMockDomain("domain-1");

        EntityStoreVersion oldVersion = new EntityStoreVersion();
        oldVersion.setVersion(1);
        oldVersion.setContent("old-entities");

        EntityStore existing = new EntityStore();
        existing.setId("es-1");
        existing.setDomainId("domain-1");
        existing.setName("my-es");
        existing.setLatestVersion(3);
        existing.setCreatedAt(new Date());

        when(entityStoreVersionRepository.findByEntityStoreIdAndVersion("es-1", 1))
                .thenReturn(Maybe.just(oldVersion));
        when(entityStoreRepository.findByDomainAndId("domain-1", "es-1"))
                .thenReturn(Maybe.just(existing));
        when(entityStoreRepository.update(any(EntityStore.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(entityStoreVersionRepository.create(any(EntityStoreVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<EntityStore> testObserver = service.restoreVersion(domain, "es-1", 1, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(e -> {
            assertEquals(4, e.getLatestVersion());
            return true;
        });

        verify(entityStoreVersionRepository).create(any(EntityStoreVersion.class));
    }
}
