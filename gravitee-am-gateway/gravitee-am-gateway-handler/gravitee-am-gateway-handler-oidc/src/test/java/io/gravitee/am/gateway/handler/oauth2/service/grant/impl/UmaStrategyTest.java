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
package io.gravitee.am.gateway.handler.oauth2.service.grant.impl;

import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.exception.uma.UmaException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.common.service.uma.UMAPermissionTicketService;
import io.gravitee.am.gateway.handler.common.service.uma.UMAResourceGatewayService;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantData;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.model.uma.PermissionTicket;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.model.uma.UMASettings;
import io.gravitee.am.service.exception.InvalidPermissionTicketException;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.function.Predicate;

import static io.gravitee.am.common.oauth2.Parameters.*;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.ACCESS_TOKEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class UmaStrategyTest {

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private UMAPermissionTicketService permissionTicketService;

    @Mock
    private UMAResourceGatewayService resourceService;

    @Mock
    private JWTService jwtService;

    @Mock
    private SubjectManager subjectManager;

    @Mock
    private RulesEngine rulesEngine;

    @Mock
    private ExecutionContextFactory executionContextFactory;

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private JWT jwt;

    @Mock
    private JWT rpt;

    private UmaStrategy strategy;
    private Domain domain;
    private Client client;
    private User user;
    private TokenRequest tokenRequest;
    private MultiValueMap<String, String> parameters;

    private static final String USER_ID = "userId";
    private static final String CLIENT_ID = "clientId";
    private static final String TICKET_ID = "ticketId";
    private static final String RQP_ID_TOKEN = "requesting_party_id_token";
    private static final String RS_ONE = "rs_one";
    private static final String RS_TWO = "rs_two";
    private static final String RPT_OLD_TOKEN = "rpt_old_token";

    @BeforeEach
    void setUp() {
        strategy = new UmaStrategy(
                userAuthenticationManager,
                permissionTicketService,
                resourceService,
                jwtService,
                subjectManager,
                rulesEngine,
                executionContextFactory
        );

        domain = new Domain();
        domain.setId("domain-id");
        domain.setUma(new UMASettings().setEnabled(true));

        client = new Client();
        client.setClientId(CLIENT_ID);
        client.setAuthorizedGrantTypes(List.of(GrantType.UMA, GrantType.REFRESH_TOKEN));
        client.setScopeSettings(List.of(
                new ApplicationScopeSettings("scopeA"),
                new ApplicationScopeSettings("scopeB"),
                new ApplicationScopeSettings("scopeC"),
                new ApplicationScopeSettings("scopeD")
        ));

        user = new User();
        user.setId(USER_ID);

        parameters = new LinkedMultiValueMap<>();
        parameters.add(TICKET, TICKET_ID);
        parameters.add(CLAIM_TOKEN, RQP_ID_TOKEN);
        parameters.add(CLAIM_TOKEN_FORMAT, TokenType.ID_TOKEN);

        tokenRequest = new TokenRequest();
        tokenRequest.setParameters(parameters);
        tokenRequest.setClientId(CLIENT_ID);
    }

    @Test
    void shouldSupportUmaGrantType() {
        assertTrue(strategy.supports(GrantType.UMA, client, domain));
    }

    @Test
    void shouldNotSupportWhenUmaDisabled() {
        domain.setUma(new UMASettings().setEnabled(false));
        assertFalse(strategy.supports(GrantType.UMA, client, domain));
    }

    @Test
    void shouldNotSupportWhenUmaNull() {
        domain.setUma(null);
        assertFalse(strategy.supports(GrantType.UMA, client, domain));
    }

    @Test
    void shouldNotSupportOtherGrantTypes() {
        assertFalse(strategy.supports(GrantType.CLIENT_CREDENTIALS, client, domain));
        assertFalse(strategy.supports(GrantType.PASSWORD, client, domain));
    }

    @Test
    void shouldNotSupportWhenClientDoesNotHaveGrantType() {
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));
        assertFalse(strategy.supports(GrantType.UMA, client, domain));
    }

    @Test
    void shouldFailWhenTicketMissing() {
        parameters.clear();

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(e -> e.getMessage().contains("ticket"));
    }

    @Test
    void shouldFailWhenClaimTokenFormatMissing() {
        parameters.remove(CLAIM_TOKEN_FORMAT);

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(UmaException.class)
                .assertError(this::assertNeedInfoMissingClaim);
    }

    @Test
    void shouldFailWhenClaimTokenMissing() {
        parameters.remove(CLAIM_TOKEN);

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(UmaException.class)
                .assertError(this::assertNeedInfoMissingClaim);
    }

    @Test
    void shouldFailWhenBadClaimTokenFormat() {
        parameters.set(CLAIM_TOKEN_FORMAT, "unsupported_format");

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(UmaException.class)
                .assertError(this::assertNeedInfoBadClaimTokenFormat);
    }

    @Test
    void shouldFailWhenUserNotFound() {
        when(jwtService.decodeAndVerify(eq(RQP_ID_TOKEN), eq(client), eq(ACCESS_TOKEN))).thenReturn(Single.just(jwt));
        when(userAuthenticationManager.loadPreAuthenticatedUserBySub(any(), any())).thenReturn(Maybe.empty());

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(UmaException.class)
                .assertError(this::assertNeedInfo);
    }

    @Test
    void shouldFailWhenTicketNotFound() {
        when(jwtService.decodeAndVerify(eq(RQP_ID_TOKEN), eq(client), eq(ACCESS_TOKEN))).thenReturn(Single.just(jwt));
        when(userAuthenticationManager.loadPreAuthenticatedUserBySub(any(), any())).thenReturn(Maybe.just(user));
        when(permissionTicketService.remove(TICKET_ID)).thenReturn(Single.error(new InvalidPermissionTicketException()));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidPermissionTicketException.class);
    }

    @Test
    void shouldFailWhenScopesNotPreregistered() {
        tokenRequest.setScopes(Set.of("not-pre-registered-scope"));
        client.setScopeSettings(Collections.emptyList());

        // Scope validation happens before permission ticket is retrieved
        when(jwtService.decodeAndVerify(eq(RQP_ID_TOKEN), eq(client), eq(ACCESS_TOKEN))).thenReturn(Single.just(jwt));
        when(userAuthenticationManager.loadPreAuthenticatedUserBySub(any(), any())).thenReturn(Maybe.just(user));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidScopeException.class);
    }

    @Test
    void shouldFailWhenUnboundResourceScope() {
        client.setScopeSettings(List.of(new ApplicationScopeSettings("not-resource-scope")));
        tokenRequest.setScopes(Set.of("not-resource-scope"));

        setupDefaultMocks();

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidScopeException.class);
    }

    @Test
    void shouldFailWhenRptExpiredOrMalformed() {
        parameters.add(RPT, RPT_OLD_TOKEN);

        setupDefaultMocks();
        when(jwtService.decodeAndVerify(eq(RPT_OLD_TOKEN), eq(client), eq(ACCESS_TOKEN)))
                .thenReturn(Single.error(new InvalidTokenException()));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class);
    }

    @Test
    void shouldFailWhenRptNotBelongToSameClient() {
        parameters.add(RPT, RPT_OLD_TOKEN);

        setupDefaultMocks();
        when(jwtService.decodeAndVerify(eq(RPT_OLD_TOKEN), eq(client), eq(ACCESS_TOKEN))).thenReturn(Single.just(rpt));
        when(rpt.getSub()).thenReturn(USER_ID);
        when(rpt.getAud()).thenReturn("notExpectedClientId");
        when(subjectManager.generateSubFrom(any(UserId.class))).thenReturn(USER_ID);

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class);
    }

    @Test
    void shouldFailWhenRptNotBelongToSameUser() {
        parameters.add(RPT, RPT_OLD_TOKEN);

        setupDefaultMocks();
        when(jwtService.decodeAndVerify(eq(RPT_OLD_TOKEN), eq(client), eq(ACCESS_TOKEN))).thenReturn(Single.just(rpt));
        when(rpt.getSub()).thenReturn("notExpectedUserId");
        // rpt.getAud() not stubbed because || short-circuits when sub doesn't match
        when(subjectManager.generateSubFrom(any(UserId.class))).thenReturn(USER_ID);

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class);
    }

    @Test
    void shouldProcessSuccessfullyWithUser() {
        setupDefaultMocks();

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertEquals(CLIENT_ID, result.clientId());
        assertEquals(GrantType.UMA, result.grantType());
        assertEquals(user, result.resourceOwner());
        assertTrue(result.supportRefreshToken());

        assertInstanceOf(GrantData.UmaData.class, result.grantData());
        GrantData.UmaData data = (GrantData.UmaData) result.grantData();
        assertEquals(TICKET_ID, data.ticket());
        assertFalse(data.upgraded());
        assertTrue(assertNominalPermissions(data.permissions()));
    }

    @Test
    void shouldProcessSuccessfullyWithAdditionalScopes() {
        tokenRequest.setScopes(Set.of("scopeB", "scopeC"));

        setupDefaultMocks();

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertInstanceOf(GrantData.UmaData.class, result.grantData());
        GrantData.UmaData data = (GrantData.UmaData) result.grantData();
        assertTrue(assertAdditionalScopePermissions(data.permissions()));
    }

    @Test
    void shouldProcessSuccessfullyWithExtendedRpt() {
        parameters.add(RPT, RPT_OLD_TOKEN);
        tokenRequest.setScopes(Set.of("scopeD"));

        setupDefaultMocks();
        setupRptMocks();

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertInstanceOf(GrantData.UmaData.class, result.grantData());
        GrantData.UmaData data = (GrantData.UmaData) result.grantData();
        assertTrue(data.upgraded());
        assertTrue(assertExtendedRptPermissions(data.permissions()));
    }

    @Test
    void shouldProcessSuccessfullyWithoutUser() {
        // Client-only request (no claim_token)
        parameters.remove(CLAIM_TOKEN);
        parameters.remove(CLAIM_TOKEN_FORMAT);

        setupPermissionMocks();

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertNull(result.resourceOwner());
        assertFalse(result.supportRefreshToken()); // No refresh for client-only
        assertTrue(assertNominalPermissions(((GrantData.UmaData) result.grantData()).permissions()));
    }

    @Test
    void shouldProcessSuccessfullyClientWithExtendedRpt() {
        parameters.remove(CLAIM_TOKEN);
        parameters.remove(CLAIM_TOKEN_FORMAT);
        parameters.add(RPT, RPT_OLD_TOKEN);
        tokenRequest.setScopes(Set.of("scopeD"));

        setupPermissionMocks();
        setupRptMocksForClient();

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertNull(result.resourceOwner());
        assertFalse(result.supportRefreshToken());
        assertInstanceOf(GrantData.UmaData.class, result.grantData());
        GrantData.UmaData data = (GrantData.UmaData) result.grantData();
        assertTrue(data.upgraded());
    }

    @Test
    void shouldFailWhenAccessPoliciesReject() {
        setupDefaultMocks();

        // Override with a policy that will cause the rules engine to fail
        io.gravitee.am.model.uma.policy.AccessPolicy accessPolicy =
                mock(io.gravitee.am.model.uma.policy.AccessPolicy.class);
        when(accessPolicy.getResource()).thenReturn(RS_ONE);
        when(accessPolicy.getType()).thenReturn(io.gravitee.am.model.uma.policy.AccessPolicyType.GROOVY);
        when(accessPolicy.isEnabled()).thenReturn(true);
        when(resourceService.findAccessPoliciesByResources(any())).thenReturn(Flowable.just(accessPolicy));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        when(rulesEngine.fire(any(List.class), any(ExecutionContext.class)))
                .thenReturn(Completable.error(new RuntimeException("Policy denied")));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(e -> e.getMessage().contains("Policy conditions are not met"));
    }

    @Test
    void shouldSucceedWhenAccessPoliciesPass() {
        setupDefaultMocks();

        io.gravitee.am.model.uma.policy.AccessPolicy accessPolicy =
                mock(io.gravitee.am.model.uma.policy.AccessPolicy.class);
        when(accessPolicy.getResource()).thenReturn(RS_ONE);
        when(accessPolicy.getType()).thenReturn(io.gravitee.am.model.uma.policy.AccessPolicyType.GROOVY);
        when(accessPolicy.isEnabled()).thenReturn(true);
        when(resourceService.findAccessPoliciesByResources(any())).thenReturn(Flowable.just(accessPolicy));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        when(rulesEngine.fire(any(List.class), any(ExecutionContext.class)))
                .thenReturn(Completable.complete());

        TokenCreationRequest result = strategy.process(tokenRequest, client, domain).blockingGet();

        assertNotNull(result);
        assertEquals(GrantType.UMA, result.grantType());
    }

    @Test
    void shouldFailWhenResourceDeletedAfterTicketIssued() {
        // Permission ticket references two resources, but one was deleted
        List<PermissionRequest> permissions = List.of(
                new PermissionRequest().setResourceId(RS_ONE).setResourceScopes(new ArrayList<>(List.of("scopeA"))),
                new PermissionRequest().setResourceId(RS_TWO).setResourceScopes(new ArrayList<>(List.of("scopeA")))
        );

        when(jwtService.decodeAndVerify(eq(RQP_ID_TOKEN), eq(client), eq(ACCESS_TOKEN))).thenReturn(Single.just(jwt));
        when(userAuthenticationManager.loadPreAuthenticatedUserBySub(any(), any())).thenReturn(Maybe.just(user));
        when(permissionTicketService.remove(TICKET_ID))
                .thenReturn(Single.just(new PermissionTicket().setId(TICKET_ID).setPermissionRequest(permissions)));

        // Only RS_ONE exists - RS_TWO was deleted after ticket was issued
        when(resourceService.findByResources(List.of(RS_ONE, RS_TWO))).thenReturn(Flowable.just(
                new Resource().setId(RS_ONE).setResourceScopes(List.of("scopeA", "scopeB", "scopeC"))
                // RS_TWO not returned - it was deleted
        ));

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(e -> e.getMessage().contains("no longer exist"));
    }

    @Test
    void shouldFailWhenAllResourcesDeleted() {
        // Permission ticket references resources, but all were deleted
        List<PermissionRequest> permissions = List.of(
                new PermissionRequest().setResourceId(RS_ONE).setResourceScopes(new ArrayList<>(List.of("scopeA"))),
                new PermissionRequest().setResourceId(RS_TWO).setResourceScopes(new ArrayList<>(List.of("scopeA")))
        );

        when(jwtService.decodeAndVerify(eq(RQP_ID_TOKEN), eq(client), eq(ACCESS_TOKEN))).thenReturn(Single.just(jwt));
        when(userAuthenticationManager.loadPreAuthenticatedUserBySub(any(), any())).thenReturn(Maybe.just(user));
        when(permissionTicketService.remove(TICKET_ID))
                .thenReturn(Single.just(new PermissionTicket().setId(TICKET_ID).setPermissionRequest(permissions)));

        // No resources returned - all were deleted
        when(resourceService.findByResources(List.of(RS_ONE, RS_TWO))).thenReturn(Flowable.empty());

        strategy.process(tokenRequest, client, domain)
                .test()
                .assertError(InvalidGrantException.class)
                .assertError(e -> e.getMessage().contains("no longer exist"));
    }

    // Helper methods for setting up mocks

    private void setupDefaultMocks() {
        when(jwtService.decodeAndVerify(eq(RQP_ID_TOKEN), eq(client), eq(ACCESS_TOKEN))).thenReturn(Single.just(jwt));
        when(userAuthenticationManager.loadPreAuthenticatedUserBySub(any(), any())).thenReturn(Maybe.just(user));
        setupPermissionMocks();
    }

    private void setupPermissionMocks() {
        List<PermissionRequest> permissions = List.of(
                new PermissionRequest().setResourceId(RS_ONE).setResourceScopes(new ArrayList<>(List.of("scopeA"))),
                new PermissionRequest().setResourceId(RS_TWO).setResourceScopes(new ArrayList<>(List.of("scopeA")))
        );

        when(permissionTicketService.remove(TICKET_ID))
                .thenReturn(Single.just(new PermissionTicket().setId(TICKET_ID).setPermissionRequest(permissions)));
        when(resourceService.findByResources(List.of(RS_ONE, RS_TWO))).thenReturn(Flowable.just(
                new Resource().setId(RS_ONE).setResourceScopes(List.of("scopeA", "scopeB", "scopeC")),
                new Resource().setId(RS_TWO).setResourceScopes(List.of("scopeA", "scopeB", "scopeD"))
        ));

        // No access policies by default (lenient because some tests fail before reaching this step)
        lenient().when(resourceService.findAccessPoliciesByResources(any())).thenReturn(Flowable.empty());
    }

    private void setupRptMocks() {
        Map<String, Object> permission = new HashMap<>();
        permission.put("resourceId", RS_ONE);
        permission.put("resourceScopes", List.of("scopeB"));

        when(jwtService.decodeAndVerify(eq(RPT_OLD_TOKEN), eq(client), eq(ACCESS_TOKEN))).thenReturn(Single.just(rpt));
        when(rpt.getSub()).thenReturn(USER_ID);
        when(rpt.getAud()).thenReturn(CLIENT_ID);
        when(rpt.get("permissions")).thenReturn(new LinkedList<>(List.of(permission)));
        when(subjectManager.generateSubFrom(any(UserId.class))).thenReturn(USER_ID);
    }

    private void setupRptMocksForClient() {
        Map<String, Object> permission = new HashMap<>();
        permission.put("resourceId", RS_ONE);
        permission.put("resourceScopes", List.of("scopeB"));

        when(jwtService.decodeAndVerify(eq(RPT_OLD_TOKEN), eq(client), eq(ACCESS_TOKEN))).thenReturn(Single.just(rpt));
        when(rpt.getSub()).thenReturn(CLIENT_ID); // RPT as client bearer
        when(rpt.getAud()).thenReturn(CLIENT_ID);
        when(rpt.get("permissions")).thenReturn(new LinkedList<>(List.of(permission)));
    }

    // Assertion helpers

    private boolean assertNeedInfoMissingClaim(Throwable throwable) {
        return assertNeedInfoRequiredClaims(throwable, 2);
    }

    private boolean assertNeedInfoBadClaimTokenFormat(Throwable throwable) {
        return assertNeedInfoRequiredClaims(throwable, 1) &&
                CLAIM_TOKEN_FORMAT.equals(((UmaException) throwable).getRequiredClaims().get(0).getName());
    }

    private boolean assertNeedInfoRequiredClaims(Throwable throwable, int requiredClaims) {
        return assertUmaException(throwable, "need_info", 403) &&
                ((UmaException) throwable).getRequiredClaims().size() == requiredClaims;
    }

    private boolean assertNeedInfo(Throwable throwable) {
        return assertUmaException(throwable, "need_info", 403);
    }

    private boolean assertUmaException(Throwable throwable, String expectedError, int expectedStatus) {
        UmaException err = (UmaException) throwable;
        return expectedError.equals(err.getError()) &&
                expectedStatus == err.getStatus() &&
                TICKET_ID.equals(err.getTicket());
    }

    /**
     * Nominal case: [rs_one(A) + rs_two(A)]
     */
    private boolean assertNominalPermissions(List<PermissionRequest> result) {
        return result != null && result.size() == 2 && result.stream()
                .filter(match(List.of("scopeA"), List.of("scopeA")))
                .count() == 2;
    }

    /**
     * Additional scopes case: [rs_one(A,B,C) + rs_two(A,B)]
     */
    private boolean assertAdditionalScopePermissions(List<PermissionRequest> result) {
        return result != null && result.size() == 2 && result.stream()
                .filter(match(List.of("scopeA", "scopeB", "scopeC"), List.of("scopeA", "scopeB")))
                .count() == 2;
    }

    /**
     * Extended RPT case: [rs_one(A,B) + rs_two(A,D)]
     */
    private boolean assertExtendedRptPermissions(List<PermissionRequest> result) {
        return result != null && result.size() == 2 && result.stream()
                .filter(match(List.of("scopeA", "scopeB"), List.of("scopeA", "scopeD")))
                .count() == 2;
    }

    private static Predicate<PermissionRequest> match(List<String> rsOne, List<String> rsTwo) {
        return permission ->
                (permission.getResourceId().equals(RS_ONE) &&
                        permission.getResourceScopes().size() == rsOne.size() &&
                        permission.getResourceScopes().containsAll(rsOne)) ||
                (permission.getResourceId().equals(RS_TWO) &&
                        permission.getResourceScopes().size() == rsTwo.size() &&
                        permission.getResourceScopes().containsAll(rsTwo));
    }
}
