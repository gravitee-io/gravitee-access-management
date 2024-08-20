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

import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.gateway.api.ScopeApprovalRepository;
import io.gravitee.am.repository.junit.gateway.MemoryScopeApprovalRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.ScopeApprovalService;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.impl.ScopeApprovalServiceImpl;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScopeApprovalAdapterImplTest {


    public static final String TEST_DOMAIN = "test-domain";
    private final DomainService domainService = mock();
    private final UserService userService = mock();
    private final ScopeApprovalRepository scopeApprovalRepository = new MemoryScopeApprovalRepository();
    private final ScopeApprovalService scopeApprovalService = new ScopeApprovalServiceImpl(scopeApprovalRepository, mock(), mock(), userService, mock());
    private final ApplicationService appService = mock();
    private final ScopeService scopeService = mock();

    private final ScopeApprovalAdapterImpl underTest = new ScopeApprovalAdapterImpl(domainService, scopeApprovalService, appService, scopeService, userService);

    @Test
    void shouldFindConsents() {
        when(domainService.findById(TEST_DOMAIN)).thenReturn(Maybe.just(Domain.builder().id(TEST_DOMAIN).build()));
        when(userService.findById(ReferenceType.DOMAIN, TEST_DOMAIN, "internal-user-id"))
                .thenReturn(Single.just(new User()));
        when(appService.findByDomainAndClientId(TEST_DOMAIN, "client-id")).thenReturn(Maybe.just(new Application()));
        when(scopeService.findByDomainAndKey(TEST_DOMAIN, "test-scope")).thenReturn(Maybe.just(new Scope()));

        Completable.mergeArray(
                        scopeApprovalRepository.create(approvalWithUser("internal-user-id")).ignoreElement(),
                        scopeApprovalRepository.create(approvalWithUser("some-other-user")).ignoreElement())
                .andThen(underTest.getUserConsents(TEST_DOMAIN, "internal-user-id", "client-id"))
                .test()
                .awaitDone(5, TimeUnit.SECONDS)
                .assertComplete();

    }

    private ScopeApproval approvalWithUser(String userId) {
        var approval = new ScopeApproval();
        approval.setUserId(UserId.internal(userId));
        approval.setDomain(TEST_DOMAIN);
        approval.setClientId("client-id");
        approval.setScope("test-scope");
        return approval;
    }
}
