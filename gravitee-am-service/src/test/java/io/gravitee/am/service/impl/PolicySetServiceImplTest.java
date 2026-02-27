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
import io.gravitee.am.model.PolicySet;
import io.gravitee.am.model.PolicySetVersion;
import io.gravitee.am.repository.management.api.PolicySetRepository;
import io.gravitee.am.repository.management.api.PolicySetVersionRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.PolicySetNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewPolicySet;
import io.gravitee.am.service.model.UpdatePolicySet;
import io.gravitee.am.service.reporter.builder.management.PolicySetAuditBuilder;
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
class PolicySetServiceImplTest {

    @Mock
    private PolicySetRepository policySetRepository;

    @Mock
    private PolicySetVersionRepository policySetVersionRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PolicySetServiceImpl service;

    private final User principal = new DefaultUser("test-user");

    private Domain createMockDomain(String domainId) {
        Domain domain = new Domain();
        domain.setId(domainId);
        return domain;
    }

    // --- findByDomain ---

    @Test
    void shouldFindByDomain() {
        PolicySet ps = new PolicySet();
        ps.setId("ps-1");
        ps.setDomainId("domain-1");

        when(policySetRepository.findByDomain("domain-1"))
                .thenReturn(Flowable.just(ps));

        TestSubscriber<PolicySet> testSubscriber = service.findByDomain("domain-1").test();
        testSubscriber.awaitDone(5, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(p -> p.getId().equals("ps-1"));
    }

    @Test
    void shouldWrapFindByDomainError() {
        when(policySetRepository.findByDomain("domain-1"))
                .thenReturn(Flowable.error(new RuntimeException("db error")));

        TestSubscriber<PolicySet> testSubscriber = service.findByDomain("domain-1").test();
        testSubscriber.awaitDone(5, TimeUnit.SECONDS);
        testSubscriber.assertError(TechnicalManagementException.class);
    }

    // --- findById ---

    @Test
    void shouldFindById() {
        PolicySet ps = new PolicySet();
        ps.setId("ps-1");

        when(policySetRepository.findById("ps-1"))
                .thenReturn(Maybe.just(ps));

        TestObserver<PolicySet> testObserver = service.findById("ps-1").test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValue(p -> p.getId().equals("ps-1"));
    }

    // --- findByDomainAndId ---

    @Test
    void shouldFindByDomainAndId() {
        PolicySet ps = new PolicySet();
        ps.setId("ps-1");
        ps.setDomainId("domain-1");

        when(policySetRepository.findByDomainAndId("domain-1", "ps-1"))
                .thenReturn(Maybe.just(ps));

        TestObserver<PolicySet> testObserver = service.findByDomainAndId("domain-1", "ps-1").test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValue(p -> p.getId().equals("ps-1"));
    }

    // --- create ---

    @Test
    void shouldCreate() {
        Domain domain = createMockDomain("domain-1");
        NewPolicySet request = new NewPolicySet();
        request.setName("my-policy-set");
        request.setContent("permit(principal, action, resource);");
        request.setCommitMessage("Initial version");

        when(policySetRepository.create(any(PolicySet.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(policySetVersionRepository.create(any(PolicySetVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<PolicySet> testObserver = service.create(domain, request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValueCount(1);

        testObserver.assertValue(p -> {
            assertEquals("my-policy-set", p.getName());
            assertEquals("domain-1", p.getDomainId());
            assertEquals(1, p.getLatestVersion());
            assertNotNull(p.getId());
            assertNotNull(p.getCreatedAt());
            assertEquals(p.getCreatedAt(), p.getUpdatedAt());
            return true;
        });

        verify(policySetVersionRepository).create(any(PolicySetVersion.class));
        verify(auditService).report(isA(PolicySetAuditBuilder.class));
    }

    // --- update ---

    @Test
    void shouldUpdate() {
        Domain domain = createMockDomain("domain-1");
        PolicySet existing = new PolicySet();
        existing.setId("ps-1");
        existing.setDomainId("domain-1");
        existing.setName("old-name");
        existing.setLatestVersion(2);
        existing.setCreatedAt(new Date());

        UpdatePolicySet request = new UpdatePolicySet();
        request.setName("new-name");
        request.setContent("new-content");
        request.setCommitMessage("Updated policies");

        when(policySetRepository.findByDomainAndId("domain-1", "ps-1"))
                .thenReturn(Maybe.just(existing));
        when(policySetRepository.update(any(PolicySet.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(policySetVersionRepository.create(any(PolicySetVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<PolicySet> testObserver = service.update(domain, "ps-1", request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(p -> {
            assertEquals("new-name", p.getName());
            assertEquals(3, p.getLatestVersion());
            assertNotNull(p.getUpdatedAt());
            return true;
        });

        verify(policySetVersionRepository).create(any(PolicySetVersion.class));
        verify(auditService).report(isA(PolicySetAuditBuilder.class));
    }

    @Test
    void shouldUpdateNameOnlyAndCopyPreviousContent() {
        Domain domain = createMockDomain("domain-1");
        PolicySet existing = new PolicySet();
        existing.setId("ps-1");
        existing.setDomainId("domain-1");
        existing.setName("old-name");
        existing.setLatestVersion(1);
        existing.setCreatedAt(new Date());

        PolicySetVersion previousVersion = new PolicySetVersion();
        previousVersion.setContent("previous-content");

        UpdatePolicySet request = new UpdatePolicySet();
        request.setName("new-name-only");
        request.setCommitMessage("Renamed");
        // content is null => should copy from previous version

        when(policySetRepository.findByDomainAndId("domain-1", "ps-1"))
                .thenReturn(Maybe.just(existing));
        when(policySetRepository.update(any(PolicySet.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(policySetVersionRepository.findLatestByPolicySetId("ps-1"))
                .thenReturn(Maybe.just(previousVersion));
        when(policySetVersionRepository.create(any(PolicySetVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<PolicySet> testObserver = service.update(domain, "ps-1", request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(p -> {
            assertEquals("new-name-only", p.getName());
            assertEquals(2, p.getLatestVersion());
            return true;
        });

        verify(policySetVersionRepository).findLatestByPolicySetId("ps-1");
        verify(policySetVersionRepository).create(any(PolicySetVersion.class));
    }

    @Test
    void shouldNotUpdateWhenNotFound() {
        Domain domain = createMockDomain("domain-1");
        UpdatePolicySet request = new UpdatePolicySet();
        request.setName("new-name");
        request.setCommitMessage("msg");

        when(policySetRepository.findByDomainAndId("domain-1", "unknown"))
                .thenReturn(Maybe.empty());

        TestObserver<PolicySet> testObserver = service.update(domain, "unknown", request, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertError(PolicySetNotFoundException.class);

        verify(policySetRepository, never()).update(any());
    }

    // --- delete ---

    @Test
    void shouldDelete() {
        Domain domain = createMockDomain("domain-1");
        PolicySet ps = new PolicySet();
        ps.setId("ps-1");
        ps.setDomainId("domain-1");

        when(policySetRepository.findByDomainAndId("domain-1", "ps-1"))
                .thenReturn(Maybe.just(ps));
        when(policySetVersionRepository.deleteByPolicySetId("ps-1"))
                .thenReturn(Completable.complete());
        when(policySetRepository.delete("ps-1"))
                .thenReturn(Completable.complete());

        TestObserver<Void> testObserver = service.delete(domain, "ps-1", principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        verify(policySetVersionRepository).deleteByPolicySetId("ps-1");
        verify(policySetRepository).delete("ps-1");
        verify(auditService).report(isA(PolicySetAuditBuilder.class));
    }

    @Test
    void shouldNotDeleteWhenNotFound() {
        Domain domain = createMockDomain("domain-1");

        when(policySetRepository.findByDomainAndId("domain-1", "unknown"))
                .thenReturn(Maybe.empty());

        TestObserver<Void> testObserver = service.delete(domain, "unknown", principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertError(PolicySetNotFoundException.class);

        verify(policySetRepository, never()).delete(any());
    }

    // --- deleteByDomain ---

    @Test
    void shouldDeleteByDomain() {
        PolicySet ps = new PolicySet();
        ps.setId("ps-1");

        when(policySetRepository.findByDomain("domain-1"))
                .thenReturn(Flowable.just(ps));
        when(policySetVersionRepository.deleteByPolicySetId("ps-1"))
                .thenReturn(Completable.complete());
        when(policySetRepository.deleteByDomain("domain-1"))
                .thenReturn(Completable.complete());

        TestObserver<Void> testObserver = service.deleteByDomain("domain-1").test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        verify(policySetVersionRepository).deleteByPolicySetId("ps-1");
        verify(policySetRepository).deleteByDomain("domain-1");
    }

    @Test
    void shouldWrapDeleteByDomainError() {
        when(policySetRepository.findByDomain("domain-1"))
                .thenReturn(Flowable.error(new RuntimeException("db error")));
        when(policySetRepository.deleteByDomain("domain-1"))
                .thenReturn(Completable.complete());

        TestObserver<Void> testObserver = service.deleteByDomain("domain-1").test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertError(TechnicalManagementException.class);
    }

    // --- getVersions ---

    @Test
    void shouldGetVersions() {
        PolicySetVersion v1 = new PolicySetVersion();
        v1.setVersion(1);
        PolicySetVersion v2 = new PolicySetVersion();
        v2.setVersion(2);

        when(policySetVersionRepository.findByPolicySetId("ps-1"))
                .thenReturn(Flowable.just(v1, v2));

        TestSubscriber<PolicySetVersion> testSubscriber = service.getVersions("ps-1").test();
        testSubscriber.awaitDone(5, TimeUnit.SECONDS);
        testSubscriber.assertComplete();
        testSubscriber.assertValueCount(2);
    }

    // --- getVersion ---

    @Test
    void shouldGetVersion() {
        PolicySetVersion v = new PolicySetVersion();
        v.setVersion(3);
        v.setContent("permit();");

        when(policySetVersionRepository.findByPolicySetIdAndVersion("ps-1", 3))
                .thenReturn(Maybe.just(v));

        TestObserver<PolicySetVersion> testObserver = service.getVersion("ps-1", 3).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertValue(ver -> ver.getVersion() == 3 && ver.getContent().equals("permit();"));
    }

    // --- restoreVersion ---

    @Test
    void shouldRestoreVersion() {
        Domain domain = createMockDomain("domain-1");

        PolicySetVersion oldVersion = new PolicySetVersion();
        oldVersion.setVersion(1);
        oldVersion.setContent("old-permit();");

        PolicySet existing = new PolicySet();
        existing.setId("ps-1");
        existing.setDomainId("domain-1");
        existing.setName("my-ps");
        existing.setLatestVersion(3);
        existing.setCreatedAt(new Date());

        when(policySetVersionRepository.findByPolicySetIdAndVersion("ps-1", 1))
                .thenReturn(Maybe.just(oldVersion));
        when(policySetRepository.findByDomainAndId("domain-1", "ps-1"))
                .thenReturn(Maybe.just(existing));
        when(policySetRepository.update(any(PolicySet.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(policySetVersionRepository.create(any(PolicySetVersion.class)))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<PolicySet> testObserver = service.restoreVersion(domain, "ps-1", 1, principal).test();
        testObserver.awaitDone(5, TimeUnit.SECONDS);
        testObserver.assertComplete();

        testObserver.assertValue(p -> {
            assertEquals(4, p.getLatestVersion());
            return true;
        });

        verify(policySetVersionRepository).create(any(PolicySetVersion.class));
    }
}
