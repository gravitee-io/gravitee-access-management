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

import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.management.handlers.management.api.model.AuthorizationDetailApprovalEntity;
import io.gravitee.am.management.handlers.management.api.model.AuthorizationDetailTypeEntity;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.gravitee.am.model.oauth2.AuthorizationDetailApprovalCursorRequest;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuthorizationDetailApprovalService;
import io.gravitee.am.service.AuthorizationDetailTypeService;
import io.gravitee.am.service.CimdMetadataDocumentService;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationDetailApprovalAdapterImplTest {

    private static final String DOMAIN_ID = "test-domain";
    private static final Domain DOMAIN = new Domain(DOMAIN_ID);
    private static final String USER_ID = "internal-user-id";
    private static final AuthorizationDetailApprovalCursorRequest CURSOR =
            AuthorizationDetailApprovalCursorRequest.initialCursor("updatedAt", "DESC", 0, null, null);

    private final DataPlaneRegistry dataPlaneRegistry = mock();
    private final UserRepository userRepository = mock();
    private final AuthorizationDetailApprovalService approvalService = mock();
    private final AuthorizationDetailTypeService typeService = mock();
    private final ApplicationService applicationService = mock();
    private final CimdMetadataDocumentService cimdMetadataDocumentService = mock();

    private final AuthorizationDetailApprovalAdapterImpl underTest = new AuthorizationDetailApprovalAdapterImpl(approvalService,
            typeService, new ConsentApplicationEntityFactory(applicationService, cimdMetadataDocumentService), dataPlaneRegistry);

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
        when(dataPlaneRegistry.getUserRepository(any())).thenReturn(userRepository);
        when(userRepository.findById(Reference.domain(DOMAIN_ID), UserId.internal(USER_ID))).thenReturn(Maybe.just(user));
        when(typeService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(type("payment_initiation", "Payment", "Initiate a payment")));
        Application application = new Application();
        application.setId("app-id");
        application.setName("My app");
        when(applicationService.findByDomainAndClientId(DOMAIN_ID, "client-id")).thenReturn(Maybe.just(application));
    }

    @Test
    void shouldListAPageOfApprovalsWithTheirApplicationAndType() {
        Map<String, Object> detail = Map.of("type", "payment_initiation", "instructedAmount", Map.of("currency", "EUR", "amount", "10.00"));
        var next = AuthorizationDetailApprovalCursorRequest.initialCursor("updatedAt", "DESC", 1, null, null);
        givenApprovals(next, approval("client-id", detail, ScopeApproval.ApprovalStatus.APPROVED));

        var page = underTest.getUserConsents(DOMAIN, USER_ID, CURSOR, 10).test().assertComplete().values().get(0);

        assertThat(page.getNextCursor()).isSameAs(next);
        assertThat(page.getTotalCount()).isEqualTo(1L);
        AuthorizationDetailApprovalEntity entity = page.getData().get(0);
        assertThat(entity.getType()).isEqualTo("payment_initiation");
        assertThat(entity.getDetail()).isEqualTo(detail);
        assertThat(entity.getStatus()).isEqualTo(ScopeApproval.ApprovalStatus.APPROVED);
        assertThat(entity.getClientEntity().getId()).isEqualTo("app-id");
        assertThat(entity.getClientEntity().getName()).isEqualTo("My app");
        assertThat(entity.getTypeEntity()).isEqualTo(new AuthorizationDetailTypeEntity("payment_initiation", "Payment", "Initiate a payment"));
        verify(approvalService).findByDomainAndUser(DOMAIN, user.getFullId(), CURSOR, 10);
    }

    @Test
    void shouldKeepTheTypeOfAnApprovalWhoseTypeWasDeleted() {
        givenApprovals(null, approval("client-id", Map.of("type", "deleted_type"), ScopeApproval.ApprovalStatus.APPROVED));

        AuthorizationDetailApprovalEntity entity = underTest.getUserConsents(DOMAIN, USER_ID, CURSOR, 10)
                .test().assertComplete().values().get(0).getData().get(0);

        assertThat(entity.getType()).isEqualTo("deleted_type");
        assertThat(entity.getTypeEntity()).isNull();
    }

    @Test
    void shouldResolveAnUnknownClient() {
        when(applicationService.findByDomainAndClientId(DOMAIN_ID, "gone-client")).thenReturn(Maybe.empty());
        givenApprovals(null, approval("gone-client", Map.of("type", "payment_initiation"), ScopeApproval.ApprovalStatus.APPROVED));

        AuthorizationDetailApprovalEntity entity = underTest.getUserConsents(DOMAIN, USER_ID, CURSOR, 10)
                .test().assertComplete().values().get(0).getData().get(0);

        assertThat(entity.getClientEntity().getId()).isEqualTo(ScopeApprovalAdapterImpl.UNKNOWN_ID);
        assertThat(entity.getClientEntity().getName()).isEqualTo("unknown-client-name");
    }

    @Test
    void shouldResolveEachClientOnce() {
        givenApprovals(null,
                approval("client-id", Map.of("type", "payment_initiation"), ScopeApproval.ApprovalStatus.APPROVED),
                approval("client-id", Map.of("type", "account_information"), ScopeApproval.ApprovalStatus.APPROVED));

        underTest.getUserConsents(DOMAIN, USER_ID, CURSOR, 10).test().assertComplete().assertValue(page -> page.getData().size() == 2);

        verify(applicationService, times(1)).findByDomainAndClientId(DOMAIN_ID, "client-id");
    }

    @Test
    void shouldFailForAnUnknownUser() {
        when(userRepository.findById(Reference.domain(DOMAIN_ID), UserId.internal("unknown"))).thenReturn(Maybe.empty());

        underTest.getUserConsents(DOMAIN, "unknown", CURSOR, 10).test().assertError(UserNotFoundException.class);

        verify(approvalService, never()).findByDomainAndUser(any(), any(), any(), anyInt());
    }

    private void givenApprovals(AuthorizationDetailApprovalCursorRequest next, AuthorizationDetailApproval... approvals) {
        when(approvalService.findByDomainAndUser(DOMAIN, user.getFullId(), CURSOR, 10))
                .thenReturn(Single.just(new CursorPage<>(List.of(approvals), next, (long) approvals.length)));
    }

    private static AuthorizationDetailApproval approval(String clientId, Map<String, Object> detail, ScopeApproval.ApprovalStatus status) {
        AuthorizationDetailApproval approval = new AuthorizationDetailApproval();
        approval.setDomain(DOMAIN_ID);
        approval.setClientId(clientId);
        approval.setUserId(UserId.internal(USER_ID));
        approval.setDetail(detail);
        approval.setStatus(status);
        return approval;
    }

    private static AuthorizationDetailType type(String type, String name, String description) {
        return AuthorizationDetailType.builder().domain(DOMAIN_ID).type(type).name(name).description(description).build();
    }
}
