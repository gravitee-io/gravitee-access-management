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
package io.gravitee.am.management.handlers.management.api.adapter;

import io.gravitee.am.dataplane.api.repository.ScopeApprovalRepository;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.dataplane.junit.gateway.MemoryScopeApprovalRepository;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.impl.ScopeApprovalServiceImpl;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScopeApprovalAdapterImplTest {


    public static final String TEST_DOMAIN_ID = "test-domain";
    public static final Domain TEST_DOMAIN = new Domain(TEST_DOMAIN_ID);
    private final DataPlaneRegistry dataPlaneRegistry = mock();
    private final UserRepository userRepository = mock();
    private final AccessTokenRepository accessTokenRepository = mock();
    private final RefreshTokenRepository refreshTokenRepository = mock();

    private final ScopeApprovalRepository scopeApprovalRepository = new MemoryScopeApprovalRepository();
    private final ScopeApprovalService scopeApprovalService = new ScopeApprovalServiceImpl(accessTokenRepository, refreshTokenRepository, dataPlaneRegistry, mock());
    private final ApplicationService appService = mock();
    private final ScopeService scopeService = mock();

    private final ScopeApprovalAdapterImpl underTest = new ScopeApprovalAdapterImpl(scopeApprovalService, appService, scopeService, dataPlaneRegistry);

    @BeforeEach
    void setUp() {
        when(dataPlaneRegistry.getUserRepository(any())).thenReturn(userRepository);
        when(dataPlaneRegistry.getScopeApprovalRepository(any())).thenReturn(scopeApprovalRepository);
    }

    @Test
    void shouldFindConsents() {
        when(userRepository.findById(Reference.domain(TEST_DOMAIN_ID), UserId.internal("internal-user-id")))
                .thenReturn(Maybe.just(new User()));
        when(appService.findByDomainAndClientId(TEST_DOMAIN_ID, "client-id")).thenReturn(Maybe.just(new Application()));
        when(scopeService.findByDomainAndKey(TEST_DOMAIN_ID, "test-scope")).thenReturn(Maybe.just(new Scope()));

        var created = Single.mergeArray(
                        scopeApprovalRepository.create(approvalWithUser("internal-user-id")),
                        scopeApprovalRepository.create(approvalWithUser("some-other-user")))
                .ignoreElements()
                .blockingAwait(5, TimeUnit.SECONDS);
        if (!created) {
            Assertions.fail("creating test data failed");
        }
        underTest.getUserConsents(TEST_DOMAIN, "internal-user-id", "client-id")
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete();

    }

    @Test
    void shouldRevokeConsents() {
        when(userRepository.findById(any(UserId.class)))
                .thenReturn(Maybe.just(new User()));
        when(accessTokenRepository.deleteByDomainIdClientIdAndUserId(any(),any(),any())).thenReturn(Completable.complete());
        when(refreshTokenRepository.deleteByDomainIdClientIdAndUserId(any(),any(),any())).thenReturn(Completable.complete());
        var created = Single.mergeArray(
                        scopeApprovalRepository.create(approvalWithUser("internal-user-id")),
                        scopeApprovalRepository.create(approvalWithUser("some-other-user")))
                .ignoreElements()
                .blockingAwait(5, TimeUnit.SECONDS);
        if (!created) {
            Assertions.fail("creating test data failed");
        }
        underTest.revokeUserConsents(TEST_DOMAIN, "internal-user-id", "client-id", null)
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete();
    }

    private ScopeApproval approvalWithUser(String userId) {
        var approval = new ScopeApproval();
        approval.setUserId(UserId.internal(userId));
        approval.setDomain(TEST_DOMAIN_ID);
        approval.setClientId("client-id");
        approval.setScope("test-scope");
        return approval;
    }
}
