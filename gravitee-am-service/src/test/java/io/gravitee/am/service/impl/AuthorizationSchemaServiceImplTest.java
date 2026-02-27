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
import io.gravitee.am.model.AuthorizationSchema;
import io.gravitee.am.model.AuthorizationSchemaVersion;
import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.management.api.AuthorizationSchemaRepository;
import io.gravitee.am.repository.management.api.AuthorizationSchemaVersionRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.AuthorizationSchemaNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewAuthorizationSchema;
import io.gravitee.am.service.model.UpdateAuthorizationSchema;
import io.gravitee.am.service.reporter.builder.management.AuthorizationSchemaAuditBuilder;
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
class AuthorizationSchemaServiceImplTest {

    @Mock
    private AuthorizationSchemaRepository authorizationSchemaRepository;

    @Mock
    private AuthorizationSchemaVersionRepository authorizationSchemaVersionRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthorizationSchemaServiceImpl service;

    private final User principal = new DefaultUser("test-user");

    private Domain createMockDomain(String domainId) {
        Domain domain = new Domain();
        domain.setId(domainId);
        return domain;
    }

    // --- findByDomain ---

    @Test
    void shouldFindByDomain() {
        AuthorizationSchema schema = new AuthorizationSchema();
        schema.setId("schema-1");
        schema.setDomainId("domain-1");

        when(authorizationSchemaRepository.findByDomain("domain-1"))
                .thenReturn(Flowable.just(schema));

        TestSubscriber<AuthorizationSchema> testSubscriber = service.findByDomain("domain-1").test();
        testSubscriber.awaitDone(5, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(s -> s.getId().equals("schema-1"));
    }

    @Test
    void shouldWrapFindByDomainError() {
        when(authorizationSchemaRepository.findByDomain("domain-1"))
                .thenReturn(Flowable.error(new RuntimeException("db error")));

        TestSubscriber<AuthorizationSchema> testSubscriber = service.findByDomain("domain-1").test();
        testSubscriber.awaitDone(5, TimeUnit.SECONDS);
        testSubscriber.assertError(TechnicalManagementException.class);
    }

    // --- findById ---

    @Test
    void shouldFindById() {
        AuthorizationSchema schema = new AuthorizationSchema();
        schema.setId("schema-1");

        when(authorizationSchemaRepository.findById("schema-1"))
                .thenReturn(Maybe.just(schema));

        TestObserver<AuthorizationSchema> testObserver = service.findById("schema-1").test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValue(s -> s.getId().equals("schema-1"));
    }

    // --- findByDomainAndId ---

    @Test
    void shouldFindByDomainAndId() {
        AuthorizationSchema schema = new AuthorizationSchema();
        schema.setId("schema-1");
        schema.setDomainId("domain-1");

        when(authorizationSchemaRepository.findByDomainAndId("domain-1", "schema-1"))
                .thenReturn(Maybe.just(schema));

        TestObserver<AuthorizationSchema> testObserver = service.findByDomainAndId("domain-1", "schema-1").test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValue(s -> s.getId().equals("schema-1"));
    }

    // --- create ---

    @Test
    void shouldCreate() {
        Domain domain = createMockDomain("domain-1");
        NewAuthorizationSchema request = new NewAuthorizationSchema();
        request.setName("my-schema");
        request.setContent("entity User;");
        request.setCommitMessage("Initial version");

        when(authorizationSchemaRepository.create(any(AuthorizationSchema.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(authorizationSchemaVersionRepository.create(any(AuthorizationSchemaVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<AuthorizationSchema> testObserver = service.create(domain, request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValueCount(1);

        testObserver.assertValue(s -> {
            assertEquals("my-schema", s.getName());
            assertEquals("domain-1", s.getDomainId());
            assertEquals(1, s.getLatestVersion());
            assertNotNull(s.getId());
            assertNotNull(s.getCreatedAt());
            assertEquals(s.getCreatedAt(), s.getUpdatedAt());
            return true;
        });

        verify(authorizationSchemaVersionRepository).create(any(AuthorizationSchemaVersion.class));
        verify(auditService).report(isA(AuthorizationSchemaAuditBuilder.class));
    }

    // --- update ---

    @Test
    void shouldUpdate() {
        Domain domain = createMockDomain("domain-1");
        AuthorizationSchema existing = new AuthorizationSchema();
        existing.setId("schema-1");
        existing.setDomainId("domain-1");
        existing.setName("old-name");
        existing.setLatestVersion(2);
        existing.setCreatedAt(new Date());

        UpdateAuthorizationSchema request = new UpdateAuthorizationSchema();
        request.setName("new-name");
        request.setContent("new-content");
        request.setCommitMessage("Updated schema");

        when(authorizationSchemaRepository.findByDomainAndId("domain-1", "schema-1"))
                .thenReturn(Maybe.just(existing));
        when(authorizationSchemaRepository.update(any(AuthorizationSchema.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(authorizationSchemaVersionRepository.create(any(AuthorizationSchemaVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<AuthorizationSchema> testObserver = service.update(domain, "schema-1", request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(s -> {
            assertEquals("new-name", s.getName());
            assertEquals(3, s.getLatestVersion());
            assertNotNull(s.getUpdatedAt());
            return true;
        });

        verify(authorizationSchemaVersionRepository).create(any(AuthorizationSchemaVersion.class));
        verify(auditService).report(isA(AuthorizationSchemaAuditBuilder.class));
    }

    @Test
    void shouldUpdateNameOnlyAndCopyPreviousContent() {
        Domain domain = createMockDomain("domain-1");
        AuthorizationSchema existing = new AuthorizationSchema();
        existing.setId("schema-1");
        existing.setDomainId("domain-1");
        existing.setName("old-name");
        existing.setLatestVersion(1);
        existing.setCreatedAt(new Date());

        AuthorizationSchemaVersion previousVersion = new AuthorizationSchemaVersion();
        previousVersion.setContent("previous-content");

        UpdateAuthorizationSchema request = new UpdateAuthorizationSchema();
        request.setName("new-name-only");
        request.setCommitMessage("Renamed");
        // content is null => should copy from previous version

        when(authorizationSchemaRepository.findByDomainAndId("domain-1", "schema-1"))
                .thenReturn(Maybe.just(existing));
        when(authorizationSchemaRepository.update(any(AuthorizationSchema.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(authorizationSchemaVersionRepository.findLatestBySchemaId("schema-1"))
                .thenReturn(Maybe.just(previousVersion));
        when(authorizationSchemaVersionRepository.create(any(AuthorizationSchemaVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<AuthorizationSchema> testObserver = service.update(domain, "schema-1", request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(s -> {
            assertEquals("new-name-only", s.getName());
            assertEquals(2, s.getLatestVersion());
            return true;
        });

        verify(authorizationSchemaVersionRepository).findLatestBySchemaId("schema-1");
        verify(authorizationSchemaVersionRepository).create(any(AuthorizationSchemaVersion.class));
    }

    @Test
    void shouldNotUpdateWhenNotFound() {
        Domain domain = createMockDomain("domain-1");
        UpdateAuthorizationSchema request = new UpdateAuthorizationSchema();
        request.setName("new-name");
        request.setCommitMessage("msg");

        when(authorizationSchemaRepository.findByDomainAndId("domain-1", "unknown"))
                .thenReturn(Maybe.empty());

        TestObserver<AuthorizationSchema> testObserver = service.update(domain, "unknown", request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertError(AuthorizationSchemaNotFoundException.class);

        verify(authorizationSchemaRepository, never()).update(any());
    }

    // --- delete ---

    @Test
    void shouldDelete() {
        Domain domain = createMockDomain("domain-1");
        AuthorizationSchema schema = new AuthorizationSchema();
        schema.setId("schema-1");
        schema.setDomainId("domain-1");

        when(authorizationSchemaRepository.findByDomainAndId("domain-1", "schema-1"))
                .thenReturn(Maybe.just(schema));
        when(authorizationSchemaVersionRepository.deleteBySchemaId("schema-1"))
                .thenReturn(Completable.complete());
        when(authorizationSchemaRepository.delete("schema-1"))
                .thenReturn(Completable.complete());

        TestObserver<Void> testObserver = service.delete(domain, "schema-1", principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        verify(authorizationSchemaVersionRepository).deleteBySchemaId("schema-1");
        verify(authorizationSchemaRepository).delete("schema-1");
        verify(auditService).report(isA(AuthorizationSchemaAuditBuilder.class));
    }

    @Test
    void shouldNotDeleteWhenNotFound() {
        Domain domain = createMockDomain("domain-1");

        when(authorizationSchemaRepository.findByDomainAndId("domain-1", "unknown"))
                .thenReturn(Maybe.empty());

        TestObserver<Void> testObserver = service.delete(domain, "unknown", principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertError(AuthorizationSchemaNotFoundException.class);

        verify(authorizationSchemaRepository, never()).delete(any());
    }

    // --- deleteByDomain ---

    @Test
    void shouldDeleteByDomain() {
        AuthorizationSchema schema = new AuthorizationSchema();
        schema.setId("schema-1");

        when(authorizationSchemaRepository.findByDomain("domain-1"))
                .thenReturn(Flowable.just(schema));
        when(authorizationSchemaVersionRepository.deleteBySchemaId("schema-1"))
                .thenReturn(Completable.complete());
        when(authorizationSchemaRepository.deleteByDomain("domain-1"))
                .thenReturn(Completable.complete());

        TestObserver<Void> testObserver = service.deleteByDomain("domain-1").test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        verify(authorizationSchemaVersionRepository).deleteBySchemaId("schema-1");
        verify(authorizationSchemaRepository).deleteByDomain("domain-1");
    }

    @Test
    void shouldWrapDeleteByDomainError() {
        when(authorizationSchemaRepository.findByDomain("domain-1"))
                .thenReturn(Flowable.error(new RuntimeException("db error")));
        when(authorizationSchemaRepository.deleteByDomain("domain-1"))
                .thenReturn(Completable.complete());

        TestObserver<Void> testObserver = service.deleteByDomain("domain-1").test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertError(TechnicalManagementException.class);
    }

    // --- getVersions ---

    @Test
    void shouldGetVersions() {
        AuthorizationSchemaVersion v1 = new AuthorizationSchemaVersion();
        v1.setVersion(1);
        AuthorizationSchemaVersion v2 = new AuthorizationSchemaVersion();
        v2.setVersion(2);

        when(authorizationSchemaVersionRepository.findBySchemaId("schema-1"))
                .thenReturn(Flowable.just(v1, v2));

        TestSubscriber<AuthorizationSchemaVersion> testSubscriber = service.getVersions("schema-1").test();
        testSubscriber.awaitDone(5, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertValueCount(2);
    }

    // --- getVersion ---

    @Test
    void shouldGetVersion() {
        AuthorizationSchemaVersion v = new AuthorizationSchemaVersion();
        v.setVersion(3);
        v.setContent("entity User;");

        when(authorizationSchemaVersionRepository.findBySchemaIdAndVersion("schema-1", 3))
                .thenReturn(Maybe.just(v));

        TestObserver<AuthorizationSchemaVersion> testObserver = service.getVersion("schema-1", 3).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValue(ver -> ver.getVersion() == 3 && ver.getContent().equals("entity User;"));
    }

    // --- restoreVersion ---

    @Test
    void shouldRestoreVersion() {
        Domain domain = createMockDomain("domain-1");

        AuthorizationSchemaVersion oldVersion = new AuthorizationSchemaVersion();
        oldVersion.setVersion(1);
        oldVersion.setContent("old-entity User;");

        AuthorizationSchema existing = new AuthorizationSchema();
        existing.setId("schema-1");
        existing.setDomainId("domain-1");
        existing.setName("my-schema");
        existing.setLatestVersion(3);
        existing.setCreatedAt(new Date());

        when(authorizationSchemaVersionRepository.findBySchemaIdAndVersion("schema-1", 1))
                .thenReturn(Maybe.just(oldVersion));
        when(authorizationSchemaRepository.findByDomainAndId("domain-1", "schema-1"))
                .thenReturn(Maybe.just(existing));
        when(authorizationSchemaRepository.update(any(AuthorizationSchema.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(authorizationSchemaVersionRepository.create(any(AuthorizationSchemaVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<AuthorizationSchema> testObserver = service.restoreVersion(domain, "schema-1", 1, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(s -> {
            assertEquals(4, s.getLatestVersion());
            return true;
        });

        verify(authorizationSchemaVersionRepository).create(any(AuthorizationSchemaVersion.class));
    }
}
