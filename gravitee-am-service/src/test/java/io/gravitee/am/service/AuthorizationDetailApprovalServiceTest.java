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
package io.gravitee.am.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.dataplane.api.repository.AuthorizationDetailApprovalRepository;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.oauth2.AuthorizationDetailApproval;
import io.gravitee.am.model.oauth2.AuthorizationDetailApprovalCursorRequest;
import io.gravitee.am.model.oauth2.AuthorizationDetails;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.token.RevokeToken;
import io.gravitee.am.model.token.RevokeType;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.exception.AuthorizationDetailApprovalNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.impl.AuthorizationDetailApprovalServiceImpl;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationDetailApprovalServiceTest {

    private static final Domain DOMAIN = new Domain("domain-id");
    private static final AuthorizationDetails REQUESTED = AuthorizationDetails.of(List.of(
            Map.of("type", "payment_initiation", "amount", "10"),
            Map.of("type", "account_information")));

    @InjectMocks
    private AuthorizationDetailApprovalServiceImpl service;

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    @Mock
    private AuthorizationDetailApprovalRepository repository;

    @Mock
    private AuditService auditService;

    @Mock
    private UserRepository userRepository;

    private final List<RevokeToken> revokedTokens = new ArrayList<>();

    private final BiFunction<Domain, RevokeToken, Completable> revokeTokens = (domain, revokeToken) ->
            Completable.fromAction(() -> revokedTokens.add(revokeToken));

    private final DefaultUser admin = new DefaultUser("admin");

    private Client client;

    @BeforeEach
    void setUp() {
        when(dataPlaneRegistry.getAuthorizationDetailApprovalRepository(DOMAIN)).thenReturn(repository);
        client = new Client();
        client.setId("client-internal-id");
        client.setClientId("client-id");
        admin.setId("admin-id");
    }

    @Test
    void shouldStoreAndAuditAnApproval() {
        AuthorizationDetailApproval approval = approval(ScopeApproval.ApprovalStatus.APPROVED);
        when(repository.upsert(approval)).thenReturn(Single.just(approval));

        service.saveConsent(DOMAIN, client, new DefaultUser("user"), REQUESTED, List.of(approval), true).test().assertComplete();

        verify(repository).upsert(approval);
        Audit audit = reportedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.USER_CONSENT_CONSENTED);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(recordedConsent(audit).get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void shouldAuditTheFullRequestedDetails() throws Exception {
        service.saveConsent(DOMAIN, client, new DefaultUser("user"), REQUESTED, List.of(), true).test().assertComplete();

        assertThat(recordedConsent(reportedAudit()).get("authorization_details"))
                .isEqualTo(new ObjectMapper().valueToTree(REQUESTED.getRaw()));
    }

    @Test
    void shouldStoreAndAuditARejection() {
        AuthorizationDetailApproval denial = approval(ScopeApproval.ApprovalStatus.DENIED);
        when(repository.upsert(denial)).thenReturn(Single.just(denial));

        service.saveConsent(DOMAIN, client, new DefaultUser("user"), REQUESTED, List.of(denial), false)
                .test().assertComplete();

        verify(repository).upsert(denial);
        assertThat(recordedConsent(reportedAudit()).get("status").asText()).isEqualTo("DENIED");
    }

    @Test
    void shouldRejectAnApprovalWithoutATypeOrAStatus() {
        AuthorizationDetailApproval withoutType = approval(ScopeApproval.ApprovalStatus.APPROVED);
        withoutType.setDetail(Map.of("actions", List.of("initiate")));
        AuthorizationDetailApproval withoutStatus = approval(null);

        for (AuthorizationDetailApproval invalid : List.of(withoutType, withoutStatus)) {
            service.saveConsent(DOMAIN, client, new DefaultUser("user"), REQUESTED, List.of(approval(ScopeApproval.ApprovalStatus.APPROVED), invalid), true)
                    .test().assertError(TechnicalManagementException.class);
        }

        verify(repository, never()).upsert(any());
    }

    @Test
    void shouldAuditAFailureToStore() {
        AuthorizationDetailApproval approval = approval(ScopeApproval.ApprovalStatus.APPROVED);
        when(repository.upsert(approval)).thenReturn(Single.error(new IllegalStateException("unavailable")));

        service.saveConsent(DOMAIN, client, new DefaultUser("user"), REQUESTED, List.of(approval), true)
                .test().assertError(TechnicalManagementException.class);

        Audit audit = reportedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.USER_CONSENT_CONSENTED);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
    }

    @Test
    void shouldFindTheApprovalsOfTheUserForTheClient() {
        UserId userId = UserId.internal("user-id");
        AuthorizationDetailApproval approval = new AuthorizationDetailApproval();
        when(repository.findByDomainAndUserAndClient("domain-id", userId, "client-id")).thenReturn(Flowable.just(approval));

        service.findByDomainAndUserAndClient(DOMAIN, userId, "client-id").test().assertValues(approval);
    }

    @Test
    void shouldFindTheApprovalsOfTheUser() {
        UserId userId = UserId.internal("user-id");
        AuthorizationDetailApproval approval = new AuthorizationDetailApproval();
        var cursor = AuthorizationDetailApprovalCursorRequest.initialCursor("updatedAt", "DESC", 0, null, null);
        var page = new CursorPage<AuthorizationDetailApproval, AuthorizationDetailApprovalCursorRequest>(List.of(approval), null, 1L);
        when(repository.findByDomainAndUser("domain-id", userId, cursor, 10)).thenReturn(Single.just(page));

        service.findByDomainAndUser(DOMAIN, userId, cursor, 10).test().assertValue(page);
    }

    @Test
    void shouldWrapAFailureToFindTheApprovalsOfTheUser() {
        UserId userId = UserId.internal("user-id");
        var cursor = AuthorizationDetailApprovalCursorRequest.initialCursor("updatedAt", "DESC", 0, null, null);
        when(repository.findByDomainAndUser("domain-id", userId, cursor, 10)).thenReturn(Single.error(new IllegalStateException("unavailable")));

        service.findByDomainAndUser(DOMAIN, userId, cursor, 10).test().assertError(TechnicalManagementException.class);
    }

    @Test
    void shouldRevokeAConsent() {
        User user = knownUser();
        AuthorizationDetailApproval consent = consent(UserId.internal("user-id"), ScopeApproval.ApprovalStatus.APPROVED);
        when(repository.findById("consent-id")).thenReturn(Maybe.just(consent));
        when(repository.delete("consent-id")).thenReturn(Completable.complete());

        service.revokeByConsent(DOMAIN, UserId.internal("user-id"), "consent-id", revokeTokens, admin).test().assertComplete();

        verify(repository).delete("consent-id");
        Audit audit = reportedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.USER_CONSENT_REVOKED);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(audit.getActor().getId()).isEqualTo("admin-id");
        assertThat(audit.getTarget().getId()).isEqualTo(user.getId());
        assertThat(recordedConsent(audit).get("status").asText()).isEqualTo("APPROVED");
        assertThat(recordedConsent(audit).get("authorization_details"))
                .isEqualTo(new ObjectMapper().valueToTree(List.of(consent.getDetail())));
        assertThat(revokedTokens).singleElement().satisfies(revokeToken -> {
            assertThat(revokeToken.getRevokeType()).isEqualTo(RevokeType.BY_USER_AND_CLIENT);
            assertThat(revokeToken.getApplication().getClientId()).isEqualTo("client-id");
            assertThat(revokeToken.getUser().getUserId()).isEqualTo("user-id");
        });
    }

    @Test
    void shouldRevokeAConsentOwnedThroughTheExternalId() {
        User user = knownUser();
        user.setExternalId("external-id");
        user.setSource("idp-id");
        when(repository.findById("consent-id"))
                .thenReturn(Maybe.just(consent(new UserId(null, "external-id", "idp-id"), ScopeApproval.ApprovalStatus.APPROVED)));
        when(repository.delete("consent-id")).thenReturn(Completable.complete());

        service.revokeByConsent(DOMAIN, UserId.internal("user-id"), "consent-id", revokeTokens, admin).test().assertComplete();

        verify(repository).delete("consent-id");
    }

    @Test
    void shouldNotRevokeTheConsentOfAnotherUser() {
        knownUser();
        when(repository.findById("consent-id"))
                .thenReturn(Maybe.just(consent(UserId.internal("another-user"), ScopeApproval.ApprovalStatus.APPROVED)));

        service.revokeByConsent(DOMAIN, UserId.internal("user-id"), "consent-id", revokeTokens, admin)
                .test().assertError(AuthorizationDetailApprovalNotFoundException.class);

        verify(repository, never()).delete(anyString());
        verify(auditService, never()).report(any());
        assertThat(revokedTokens).isEmpty();
    }

    @Test
    void shouldNotRevokeAMissingOrExpiredConsent() {
        knownUser();
        when(repository.findById("consent-id")).thenReturn(Maybe.empty());

        service.revokeByConsent(DOMAIN, UserId.internal("user-id"), "consent-id", revokeTokens, admin)
                .test().assertError(AuthorizationDetailApprovalNotFoundException.class);

        verify(repository, never()).delete(anyString());
        verify(auditService, never()).report(any());
        assertThat(revokedTokens).isEmpty();
    }

    @Test
    void shouldNotRevokeAConsentOfAnUnknownUser() {
        when(dataPlaneRegistry.getUserRepository(DOMAIN)).thenReturn(userRepository);
        when(userRepository.findById(DOMAIN.asReference(), UserId.internal("user-id"))).thenReturn(Maybe.empty());

        service.revokeByConsent(DOMAIN, UserId.internal("user-id"), "consent-id", revokeTokens, admin)
                .test().assertError(UserNotFoundException.class);

        verify(repository, never()).delete(anyString());
        assertThat(revokedTokens).isEmpty();
    }

    @Test
    void shouldAuditAFailureToRevokeAConsent() {
        knownUser();
        when(repository.findById("consent-id"))
                .thenReturn(Maybe.just(consent(UserId.internal("user-id"), ScopeApproval.ApprovalStatus.APPROVED)));
        when(repository.delete("consent-id")).thenReturn(Completable.error(new IllegalStateException("unavailable")));

        service.revokeByConsent(DOMAIN, UserId.internal("user-id"), "consent-id", revokeTokens, admin)
                .test().assertError(TechnicalManagementException.class);

        Audit audit = reportedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.USER_CONSENT_REVOKED);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
        assertThat(revokedTokens).isEmpty();
    }

    @Test
    void shouldRevokeTheConsentsOfAUserForAClient() {
        User user = knownUser();
        AuthorizationDetailApproval approved = consent(user.getFullId(), ScopeApproval.ApprovalStatus.APPROVED);
        AuthorizationDetailApproval denied = consent(user.getFullId(), ScopeApproval.ApprovalStatus.DENIED);
        denied.setDetail(Map.of("type", "account_information"));
        when(repository.findByDomainAndUserAndClient("domain-id", user.getFullId(), "client-id")).thenReturn(Flowable.just(approved, denied));
        when(repository.deleteByDomainAndUserAndClient("domain-id", user.getFullId(), "client-id")).thenReturn(Completable.complete());

        service.revokeByUserAndClient(DOMAIN, UserId.internal("user-id"), "client-id", revokeTokens, admin).test().assertComplete();

        verify(repository).deleteByDomainAndUserAndClient("domain-id", user.getFullId(), "client-id");
        Audit audit = reportedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.USER_CONSENT_REVOKED);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(recordedConsents(audit)).extracting(consent -> consent.get("status").asText()).containsExactly("APPROVED", "DENIED");
        assertThat(recordedConsents(audit).get(1).get("authorization_details"))
                .isEqualTo(new ObjectMapper().valueToTree(List.of(denied.getDetail())));
        assertThat(revokedTokens).singleElement().satisfies(revokeToken -> {
            assertThat(revokeToken.getRevokeType()).isEqualTo(RevokeType.BY_USER_AND_CLIENT);
            assertThat(revokeToken.getApplication().getClientId()).isEqualTo("client-id");
        });
    }

    @Test
    void shouldRevokeTheConsentsOfAUserForEveryClient() {
        User user = knownUser();
        AuthorizationDetailApproval consent = consent(user.getFullId(), ScopeApproval.ApprovalStatus.APPROVED);
        when(repository.findByDomainAndUser("domain-id", user.getFullId())).thenReturn(Flowable.just(consent));
        when(repository.deleteByDomainAndUser("domain-id", user.getFullId())).thenReturn(Completable.complete());

        service.revokeByUser(DOMAIN, UserId.internal("user-id"), revokeTokens, admin).test().assertComplete();

        verify(repository).deleteByDomainAndUser("domain-id", user.getFullId());
        Audit audit = reportedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.USER_CONSENT_REVOKED);
        assertThat(audit.getTarget().getId()).isEqualTo("user-id");
        assertThat(recordedConsents(audit)).hasSize(1);
        assertThat(revokedTokens).singleElement()
                .satisfies(revokeToken -> assertThat(revokeToken.getRevokeType()).isEqualTo(RevokeType.BY_USER));
    }

    @Test
    void shouldRevokeTokensWithoutAuditWhenAUserHasNoConsents() {
        User user = knownUser();
        when(repository.findByDomainAndUser("domain-id", user.getFullId())).thenReturn(Flowable.empty());
        when(repository.deleteByDomainAndUser("domain-id", user.getFullId())).thenReturn(Completable.complete());

        service.revokeByUser(DOMAIN, UserId.internal("user-id"), revokeTokens, admin).test().assertComplete();

        verify(auditService, never()).report(any());
        assertThat(revokedTokens).singleElement()
                .satisfies(revokeToken -> assertThat(revokeToken.getRevokeType()).isEqualTo(RevokeType.BY_USER));
    }

    @Test
    void shouldNotRevokeTheConsentsOfAnUnknownUser() {
        when(dataPlaneRegistry.getUserRepository(DOMAIN)).thenReturn(userRepository);
        when(userRepository.findById(DOMAIN.asReference(), UserId.internal("user-id"))).thenReturn(Maybe.empty());

        service.revokeByUser(DOMAIN, UserId.internal("user-id"), revokeTokens, admin).test().assertError(UserNotFoundException.class);
        service.revokeByUserAndClient(DOMAIN, UserId.internal("user-id"), "client-id", revokeTokens, admin)
                .test().assertError(UserNotFoundException.class);

        assertThat(revokedTokens).isEmpty();
    }

    @Test
    void shouldAuditAFailureToRevokeTheConsentsOfAUser() {
        User user = knownUser();
        when(repository.findByDomainAndUser("domain-id", user.getFullId()))
                .thenReturn(Flowable.just(consent(user.getFullId(), ScopeApproval.ApprovalStatus.APPROVED)));
        when(repository.deleteByDomainAndUser("domain-id", user.getFullId())).thenReturn(Completable.error(new IllegalStateException("unavailable")));

        service.revokeByUser(DOMAIN, UserId.internal("user-id"), revokeTokens, admin).test().assertError(TechnicalManagementException.class);

        Audit audit = reportedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.USER_CONSENT_REVOKED);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
        assertThat(revokedTokens).isEmpty();
    }

    @Test
    void shouldPurgeTheDomain() {
        when(repository.deleteByDomain("domain-id")).thenReturn(Completable.complete());

        service.purgeDataPlane(DOMAIN, new DefaultUser("admin")).test().assertComplete();

        verify(repository).deleteByDomain("domain-id");
    }

    private User knownUser() {
        User user = User.builder().id("user-id").username("user").build();
        when(dataPlaneRegistry.getUserRepository(DOMAIN)).thenReturn(userRepository);
        when(userRepository.findById(DOMAIN.asReference(), UserId.internal("user-id"))).thenReturn(Maybe.just(user));
        return user;
    }

    private static AuthorizationDetailApproval consent(UserId owner, ScopeApproval.ApprovalStatus status) {
        AuthorizationDetailApproval consent = approval(status);
        consent.setId("consent-id");
        consent.setUserId(owner);
        consent.setClientId("client-id");
        consent.setDetail(Map.of("type", "payment_initiation", "amount", "10"));
        return consent;
    }

    private static AuthorizationDetailApproval approval(ScopeApproval.ApprovalStatus status) {
        AuthorizationDetailApproval approval = new AuthorizationDetailApproval();
        approval.setDetail(Map.of("type", "payment_initiation"));
        approval.setStatus(status);
        return approval;
    }

    private Audit reportedAudit() {
        ArgumentCaptor<AuditBuilder> builder = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(builder.capture());
        return builder.getValue().build(new ObjectMapper());
    }

    private static JsonNode recordedConsent(Audit audit) {
        return recordedConsents(audit).get(0);
    }

    private static List<JsonNode> recordedConsents(Audit audit) {
        try {
            JsonNode patch = new ObjectMapper().readTree(audit.getOutcome().getMessage());
            List<JsonNode> consents = new ArrayList<>();
            patch.forEach(operation -> consents.add(operation.get("value")));
            return consents;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
