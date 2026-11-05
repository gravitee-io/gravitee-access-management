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
package io.gravitee.am.gateway.handler.oauth2.service.consent;

import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailDigest;
import io.gravitee.am.gateway.handler.oauth2.service.authorizationdetails.AuthorizationDetailTypeManager;
import io.gravitee.am.gateway.handler.oauth2.service.consent.impl.AuthorizationDetailsConsentServiceImpl;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationAuthorizationDetailTypeSettings;
import io.gravitee.am.model.oauth2.AuthorizationDetail;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthorizationDetailApprovalService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthorizationDetailsConsentServiceTest {

    private static final AuthorizationDetail PAYMENT = new AuthorizationDetail(Map.of("type", "payment_initiation", "amount", "10"));
    private static final AuthorizationDetail ACCOUNT = new AuthorizationDetail(Map.of("type", "account_information", "actions", List.of("read")));

    @Mock
    private AuthorizationDetailApprovalService authorizationDetailApprovalService;

    @Mock
    private AuthorizationDetailTypeManager authorizationDetailTypeManager;

    @Mock
    private Domain domain;

    @InjectMocks
    private AuthorizationDetailsConsentServiceImpl service = new AuthorizationDetailsConsentServiceImpl(-1);

    private Client client;
    private User user;

    @BeforeEach
    void setUp() {
        when(domain.getId()).thenReturn("domain-id");
        client = new Client();
        client.setClientId("client-id");
        user = new User();
        user.setId("user-id");
    }

    @Test
    void shouldPresentEveryDetailWithoutApprovals() {
        givenApprovals();

        assertThat(service.getDetailsToPresent(client, user, AuthorizationDetails.of(List.of(PAYMENT, ACCOUNT)), false).blockingGet())
                .containsExactly(PAYMENT, ACCOUNT);
    }

    @Test
    void shouldPresentOnlyTheDetailsNotApprovedYet() {
        givenApprovals(approvalOf(PAYMENT, inDays(1)));

        assertThat(service.getDetailsToPresent(client, user, AuthorizationDetails.of(List.of(PAYMENT, ACCOUNT)), false).blockingGet())
                .containsExactly(ACCOUNT);
    }

    @Test
    void shouldPresentNothingWhenEveryDetailIsApproved() {
        givenApprovals(approvalOf(ACCOUNT, inDays(1)), approvalOf(PAYMENT, inDays(1)));

        assertThat(service.getDetailsToPresent(client, user, AuthorizationDetails.of(List.of(PAYMENT, ACCOUNT)), false).blockingGet())
                .isEmpty();
    }

    @Test
    void shouldPresentADetailThatDiffersFromTheApprovedOne() {
        givenApprovals(approvalOf(new AuthorizationDetail(Map.of("type", "payment_initiation", "amount", "11")), inDays(1)));

        assertThat(service.getDetailsToPresent(client, user, AuthorizationDetails.of(List.of(PAYMENT)), false).blockingGet())
                .containsExactly(PAYMENT);
    }

    @Test
    void shouldPresentADeniedDetail() {
        AuthorizationDetailApproval denied = approvalOf(PAYMENT, inDays(1));
        denied.setStatus(ScopeApproval.ApprovalStatus.DENIED);
        givenApprovals(denied);

        assertThat(service.getDetailsToPresent(client, user, AuthorizationDetails.of(List.of(PAYMENT)), false).blockingGet())
                .containsExactly(PAYMENT);
    }

    @Test
    void shouldIgnoreExpiredApprovals() {
        givenApprovals(approvalOf(PAYMENT, inDays(-1)));

        assertThat(service.getDetailsToPresent(client, user, AuthorizationDetails.of(List.of(PAYMENT)), false).blockingGet())
                .containsExactly(PAYMENT);
    }

    @Test
    void shouldPresentEveryDetailWithPromptConsent() {
        givenApprovals(approvalOf(PAYMENT, inDays(1)));

        assertThat(service.getDetailsToPresent(client, user, AuthorizationDetails.of(List.of(PAYMENT, ACCOUNT)), true).blockingGet())
                .containsExactly(PAYMENT, ACCOUNT);
    }

    @Test
    void shouldPresentEveryDetailWithPromptConsentEvenWhenTheClientSkipsConsent() {
        client.setSkipConsent(true);

        assertThat(service.getDetailsToPresent(client, user, AuthorizationDetails.of(List.of(PAYMENT)), true).blockingGet())
                .containsExactly(PAYMENT);
    }

    @Test
    void shouldPresentNothingWhenTheClientSkipsConsent() {
        client.setSkipConsent(true);

        assertThat(service.getDetailsToPresent(client, user, AuthorizationDetails.of(List.of(PAYMENT)), false).blockingGet())
                .isEmpty();
        verify(authorizationDetailApprovalService, never()).findByDomainAndUserAndClient(any(), any(), any());
    }

    @Test
    void shouldNotUseTheAutoApprovedScopesOfTheClient() {
        client.setAutoApproveScopes(List.of("true"));
        givenApprovals();

        assertThat(service.getDetailsToPresent(client, user, AuthorizationDetails.of(List.of(PAYMENT)), false).blockingGet())
                .containsExactly(PAYMENT);
    }

    @Test
    void shouldSaveTheConsentForThePresentedDetails() {
        when(authorizationDetailTypeManager.findByType("account_information")).thenReturn(Optional.empty());
        when(authorizationDetailApprovalService.saveConsent(any(), any(), any(), any(), anyList(), anyBoolean())).thenReturn(Completable.complete());
        AuthorizationDetails requested = AuthorizationDetails.of(List.of(PAYMENT, ACCOUNT));

        service.saveConsent(client, user, new DefaultUser("user"), requested, List.of(ACCOUNT), true).blockingAwait();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuthorizationDetailApproval>> approvals = ArgumentCaptor.forClass(List.class);
        verify(authorizationDetailApprovalService).saveConsent(eq(domain), eq(client), any(), eq(requested), approvals.capture(), eq(true));
        assertThat(approvals.getValue()).singleElement().satisfies(approval -> {
            assertThat(approval.getDetail()).isEqualTo(ACCOUNT);
            assertThat(approval.getDigest()).isEqualTo(AuthorizationDetailDigest.of(ACCOUNT));
            assertThat(approval.getDomain()).isEqualTo("domain-id");
            assertThat(approval.getClientId()).isEqualTo("client-id");
            assertThat(approval.getUserId()).isEqualTo(user.getFullId());
            assertThat(approval.getStatus()).isEqualTo(ScopeApproval.ApprovalStatus.APPROVED);
            assertThat(approval.getExpiresAt()).isAfter(inDays(27)).isBefore(inDays(32));
        });
    }

    @Test
    void shouldSaveARejectionAsDenied() {
        when(authorizationDetailTypeManager.findByType("account_information")).thenReturn(Optional.empty());
        when(authorizationDetailApprovalService.saveConsent(any(), any(), any(), any(), anyList(), anyBoolean())).thenReturn(Completable.complete());

        service.saveConsent(client, user, new DefaultUser("user"), AuthorizationDetails.of(List.of(ACCOUNT)), List.of(ACCOUNT), false).blockingAwait();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuthorizationDetailApproval>> approvals = ArgumentCaptor.forClass(List.class);
        verify(authorizationDetailApprovalService).saveConsent(any(), any(), any(), any(), approvals.capture(), eq(false));
        assertThat(approvals.getValue()).singleElement()
                .extracting(AuthorizationDetailApproval::getStatus).isEqualTo(ScopeApproval.ApprovalStatus.DENIED);
    }

    @Test
    void shouldExpireWithTheApplicationSettingForTheType() {
        AuthorizationDetailType type = new AuthorizationDetailType();
        type.setExpiresIn(7200);
        when(authorizationDetailTypeManager.findByType("account_information")).thenReturn(Optional.of(type));
        ApplicationAuthorizationDetailTypeSettings settings = new ApplicationAuthorizationDetailTypeSettings();
        settings.setType("account_information");
        settings.setExpiresIn(60);
        client.setAuthorizationDetailTypes(List.of(settings));

        assertThat(savedApproval(ACCOUNT).getExpiresAt()).isCloseTo(inSeconds(60), 5000);
    }

    @Test
    void shouldExpireWithTheTypeSetting() {
        AuthorizationDetailType type = new AuthorizationDetailType();
        type.setExpiresIn(7200);
        when(authorizationDetailTypeManager.findByType("account_information")).thenReturn(Optional.of(type));
        ApplicationAuthorizationDetailTypeSettings settings = new ApplicationAuthorizationDetailTypeSettings();
        settings.setType("account_information");
        client.setAuthorizationDetailTypes(List.of(settings));

        assertThat(savedApproval(ACCOUNT).getExpiresAt()).isCloseTo(inSeconds(7200), 5000);
    }

    private AuthorizationDetailApproval savedApproval(AuthorizationDetail detail) {
        when(authorizationDetailApprovalService.saveConsent(any(), any(), any(), any(), anyList(), anyBoolean())).thenReturn(Completable.complete());
        service.saveConsent(client, user, new DefaultUser("user"), AuthorizationDetails.of(List.of(detail)), List.of(detail), true).blockingAwait();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuthorizationDetailApproval>> approvals = ArgumentCaptor.forClass(List.class);
        verify(authorizationDetailApprovalService).saveConsent(any(), any(), any(), any(), approvals.capture(), anyBoolean());
        return approvals.getValue().get(0);
    }

    private static Date inSeconds(int seconds) {
        return Date.from(Instant.now().plusSeconds(seconds));
    }

    private void givenApprovals(AuthorizationDetailApproval... approvals) {
        when(authorizationDetailApprovalService.findByDomainAndUserAndClient(domain, user.getFullId(), "client-id"))
                .thenReturn(Flowable.fromArray(approvals));
    }

    private static AuthorizationDetailApproval approvalOf(Map<String, Object> detail, Date expiresAt) {
        AuthorizationDetailApproval approval = new AuthorizationDetailApproval();
        approval.setDetail(detail);
        approval.setDigest(AuthorizationDetailDigest.of(detail));
        approval.setStatus(ScopeApproval.ApprovalStatus.APPROVED);
        approval.setExpiresAt(expiresAt);
        return approval;
    }

    private static Date inDays(int days) {
        return Date.from(Instant.now().plus(days, ChronoUnit.DAYS));
    }
}
