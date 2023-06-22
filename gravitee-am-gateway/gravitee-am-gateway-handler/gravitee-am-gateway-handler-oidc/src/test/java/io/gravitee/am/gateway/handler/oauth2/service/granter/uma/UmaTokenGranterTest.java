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
package io.gravitee.am.gateway.handler.oauth2.service.granter.uma;

import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.exception.uma.UmaException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.model.uma.PermissionTicket;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.model.uma.UMASettings;
import io.gravitee.am.model.uma.policy.AccessPolicy;
import io.gravitee.am.model.uma.policy.AccessPolicyType;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.PermissionTicketService;
import io.gravitee.am.service.ResourceService;
import io.gravitee.am.service.exception.InvalidPermissionTicketException;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;
import java.util.function.Predicate;

import static io.gravitee.am.common.oauth2.Parameters.*;
import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.ACCESS_TOKEN;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UmaTokenGranterTest {

    @Mock
    private Domain domain;

    @Mock
    private Client client;

    @Mock
    private User user;

    @Mock
    private TokenService tokenService;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private PermissionTicketService permissionTicketService;

    @Mock
    private ResourceService resourceService;

    @Mock
    private RulesEngine rulesEngine;

    @Mock
    private ExecutionContextFactory executionContextFactory;

    @Mock
    private JWTService jwtService;

    @Mock
    private JWT jwt;

    @Mock
    private JWT rpt;

    @InjectMocks
    private UMATokenGranter umaTokenGranter = new UMATokenGranter(
            tokenService, userAuthenticationManager,
            permissionTicketService, resourceService,
            jwtService, domain, executionContextFactory, rulesEngine
    );

    private TokenRequest tokenRequest;
    private MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
    private ArgumentCaptor<OAuth2Request> oauth2RequestCaptor = ArgumentCaptor.forClass(OAuth2Request.class);
    private static final String USER_ID = "userId";
    private static final String CLIENT_ID = "clientId";
    private static final String TICKET_ID = "ticketId";
    private static final String RQP_ID_TOKEN = "requesting_party_id_token";
    private static final String RS_ONE = "rs_one";
    private static final String RS_TWO = "rs_two";
    private static final String RPT_OLD_TOKEN = "rpt_old_token";

    @Before
    public void setUp() {
        //Init parameters
        parameters.add(TICKET, TICKET_ID);
        parameters.add(CLAIM_TOKEN, RQP_ID_TOKEN);
        parameters.add(CLAIM_TOKEN_FORMAT, TokenType.ID_TOKEN);
        tokenRequest = new TokenRequest();
        tokenRequest.setParameters(parameters);
        List<PermissionRequest> permissions = Arrays.asList(
                new PermissionRequest().setResourceId(RS_ONE).setResourceScopes(new ArrayList<>(Arrays.asList("scopeA"))),
                new PermissionRequest().setResourceId(RS_TWO).setResourceScopes(new ArrayList<>(Arrays.asList("scopeA")))
        );
        Map permission = new HashMap();
        permission.put("resourceId",RS_ONE);
        permission.put("resourceScopes",Arrays.asList("scopeB"));

        //Init mocks
        when(domain.getUma()).thenReturn(new UMASettings().setEnabled(true));
        when(client.getClientId()).thenReturn(CLIENT_ID);
        when(client.getScopeSettings()).thenReturn(Arrays.asList(new ApplicationScopeSettings("scopeA"), new ApplicationScopeSettings("scopeB"), new ApplicationScopeSettings("scopeC"), new ApplicationScopeSettings("scopeD")));
        when(client.getAuthorizedGrantTypes()).thenReturn(Arrays.asList(GrantType.UMA, GrantType.REFRESH_TOKEN));
        when(user.getId()).thenReturn(USER_ID);
        when(jwt.getSub()).thenReturn(USER_ID);
        when(rpt.getSub()).thenReturn(USER_ID);
        when(rpt.getAud()).thenReturn(CLIENT_ID);
        when(rpt.get("permissions")).thenReturn(new LinkedList(Arrays.asList(permission)));
        when(jwtService.decodeAndVerify(RQP_ID_TOKEN, client, ACCESS_TOKEN)).thenReturn(Single.just(jwt));
        when(jwtService.decodeAndVerify(RPT_OLD_TOKEN, client, ACCESS_TOKEN)).thenReturn(Single.just(rpt));
        when(userAuthenticationManager.loadPreAuthenticatedUser(USER_ID, tokenRequest)).thenReturn(Maybe.just(user));
        when(permissionTicketService.remove(TICKET_ID)).thenReturn(Single.just(new PermissionTicket().setId(TICKET_ID).setPermissionRequest(permissions)));
        when(resourceService.findByResources(Arrays.asList(RS_ONE, RS_TWO))).thenReturn(Flowable.just(
                new Resource().setId(RS_ONE).setResourceScopes(Arrays.asList("scopeA", "scopeB", "scopeC")),
                new Resource().setId(RS_TWO).setResourceScopes(Arrays.asList("scopeA", "scopeB", "scopeD"))
        ));
        when(tokenService.create(oauth2RequestCaptor.capture(), eq(client), any())).thenReturn(Single.just(new AccessToken("success")));
        when(resourceService.findAccessPoliciesByResources(anyList())).thenReturn(Flowable.empty());
    }

    @Test
    public void handle_nonUmaGrant() {
        assertFalse(umaTokenGranter.handle("any",client));
    }

    @Test
    public void handle_umaDisabled() {
        when(domain.getUma()).thenReturn(new UMASettings().setEnabled(false));
        assertFalse(umaTokenGranter.handle(GrantType.UMA,client));
    }

    @Test
    public void handle_ok() {
        assertTrue(umaTokenGranter.handle(GrantType.UMA,client));
    }

    @Test
    public void grant_missingTicket() {
        parameters.clear();//erase default setup
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver
                .assertNotComplete()
                .assertError(InvalidGrantException.class)
                .assertError(err -> "Missing parameter: ticket".equals(err.getMessage()));
    }

    @Test
    public void grant_missingClaimTokenFormat() {
        parameters.remove(CLAIM_TOKEN_FORMAT);
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertNotComplete().assertError(UmaException.class).assertError(this::assertNeedInfoMissingClaim);
    }

    @Test
    public void grant_missingClaimToken() {
        parameters.remove(CLAIM_TOKEN);
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertNotComplete().assertError(UmaException.class).assertError(this::assertNeedInfoMissingClaim);
    }

    @Test
    public void grant_badClaimTokenFormat() {
        parameters.replace(CLAIM_TOKEN_FORMAT, Arrays.asList("claim_token_format"));
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertNotComplete().assertError(UmaException.class).assertError(this::assertNeedInfoBadClaimTokenFormat);
    }

    @Test
    public void grant_user_technicalException() {
        when(userAuthenticationManager.loadPreAuthenticatedUser(USER_ID, tokenRequest)).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertNotComplete().assertError(UmaException.class).assertError(this::assertNeedInfo);
    }

    @Test
    public void grant_user_notFound() {
        when(userAuthenticationManager.loadPreAuthenticatedUser(USER_ID, tokenRequest)).thenReturn(Maybe.empty());
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertNotComplete().assertError(UmaException.class).assertError(this::assertNeedInfo);
    }

    @Test
    public void grant_ticketNotFound() {
        when(permissionTicketService.remove(TICKET_ID)).thenReturn(Single.error(InvalidPermissionTicketException::new));
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertNotComplete().assertError(InvalidPermissionTicketException.class);
    }

    @Test
    public void grant_scopesNotPreregistered() {
        //Here request scope has not been pre-registered on client.
        tokenRequest.setScopes(new HashSet<>(Arrays.asList("not-pre-registered-scope")));
        when(client.getScopeSettings()).thenReturn(Collections.emptyList());
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertNotComplete().assertError(InvalidScopeException.class);
    }

    @Test
    public void grant_unboundResourceScope() {
        //Here request scope is well pre-registered on client, but does not match any resources
        when(client.getScopeSettings()).thenReturn(Arrays.asList(new ApplicationScopeSettings("not-resource-scope")));
        tokenRequest.setScopes(new HashSet<>(Arrays.asList("not-resource-scope")));
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertNotComplete().assertError(InvalidScopeException.class);
    }

    @Test
    public void grant_rptExpiredOrMalformed() {
        parameters.add(RPT, RPT_OLD_TOKEN);
        when(jwtService.decodeAndVerify(RPT_OLD_TOKEN, client, ACCESS_TOKEN)).thenReturn(Single.error(InvalidTokenException::new));
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertNotComplete().assertError(InvalidGrantException.class);
    }

    @Test
    public void grant_rptNotBelongToSameClient() {
        parameters.add(RPT, RPT_OLD_TOKEN);
        when(rpt.getAud()).thenReturn("notExpectedClientId");
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertNotComplete().assertError(InvalidGrantException.class);
    }

    @Test
    public void grant_rptNotBelongToSameUser() {
        parameters.add(RPT, RPT_OLD_TOKEN);
        when(rpt.getSub()).thenReturn("notExpectedUserId");
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertNotComplete().assertError(InvalidGrantException.class);
    }

    @Test
    public void grant_user_nominalCase() {
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertComplete().assertNoErrors().assertValue(token -> "success".equals(token.getValue()));
        OAuth2Request result = oauth2RequestCaptor.getValue();
        assertTrue(USER_ID.equals(result.getSubject()));
        assertTrue(assertNominalPermissions(result.getPermissions()));
        assertTrue(result.isSupportRefreshToken());
    }

    @Test
    public void grant_user_additionalScopeCase() {
        tokenRequest.setScopes(new HashSet<>(Arrays.asList("scopeB", "scopeC")));
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertComplete().assertNoErrors().assertValue(token -> "success".equals(token.getValue()));
        OAuth2Request result = oauth2RequestCaptor.getValue();
        assertTrue(USER_ID.equals(result.getSubject()));
        assertTrue(assertAdditionalScopePermissions(result.getPermissions()));
        assertTrue(result.isSupportRefreshToken());
    }

    @Test
    public void grant_user_RptWithoutPermissionCase() {
        parameters.add(RPT, RPT_OLD_TOKEN);
        when(rpt.get("permissions")).thenReturn(null);
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertComplete().assertNoErrors().assertValue(token -> "success".equals(token.getValue()));
        OAuth2Request result = oauth2RequestCaptor.getValue();
        assertTrue(USER_ID.equals(result.getSubject()));
        assertTrue(assertNominalPermissions(result.getPermissions()));
        assertTrue(result.isSupportRefreshToken());
    }

    @Test
    public void grant_user_extendRptCase() {
        parameters.add(RPT, RPT_OLD_TOKEN);
        tokenRequest.setScopes(new HashSet<>(Arrays.asList("scopeD")));
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertComplete().assertNoErrors().assertValue(token -> "success".equals(token.getValue()) && token.isUpgraded());
        OAuth2Request result = oauth2RequestCaptor.getValue();
        assertTrue(USER_ID.equals(result.getSubject()));
        assertTrue(assertExtendedRptPermissions(result.getPermissions()));
        assertTrue(result.isSupportRefreshToken());
    }

    @Test
    public void grant_client_nominalCase() {
        parameters.remove(CLAIM_TOKEN);
        parameters.remove(CLAIM_TOKEN_FORMAT);
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertComplete().assertNoErrors().assertValue(token -> "success".equals(token.getValue()));
        OAuth2Request result = oauth2RequestCaptor.getValue();
        assertNull(result.getSubject());
        assertTrue(assertNominalPermissions(result.getPermissions()));
        assertFalse(result.isSupportRefreshToken());
    }

    @Test
    public void grant_client_additionalCase() {
        parameters.remove(CLAIM_TOKEN);
        parameters.remove(CLAIM_TOKEN_FORMAT);
        tokenRequest.setScopes(new HashSet<>(Arrays.asList("scopeB", "scopeC")));
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertComplete().assertNoErrors().assertValue(token -> "success".equals(token.getValue()));
        OAuth2Request result = oauth2RequestCaptor.getValue();
        assertNull(result.getSubject());
        assertTrue(assertAdditionalScopePermissions(result.getPermissions()));
        assertFalse(result.isSupportRefreshToken());
    }

    @Test
    public void grant_client_extendRptCase() {
        parameters.remove(CLAIM_TOKEN);
        parameters.remove(CLAIM_TOKEN_FORMAT);
        parameters.add(RPT, RPT_OLD_TOKEN);
        tokenRequest.setScopes(new HashSet<>(Arrays.asList("scopeD")));
        when(rpt.getSub()).thenReturn(CLIENT_ID);//Set RPT as Client bearer.
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertComplete().assertNoErrors().assertValue(token -> "success".equals(token.getValue()) && token.isUpgraded());
        OAuth2Request result = oauth2RequestCaptor.getValue();
        assertNull(result.getSubject());
        assertTrue(assertExtendedRptPermissions(result.getPermissions()));
        assertFalse(result.isSupportRefreshToken());
    }

    @Test
    public void grant_nominalCase_accessPolicy_deny() {
        AccessPolicy policy = mock(AccessPolicy.class);
        when(policy.getType()).thenReturn(AccessPolicyType.GROOVY);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(resourceService.findAccessPoliciesByResources(anyList())).thenReturn(Flowable.just(policy));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        when(rulesEngine.fire(any(), any())).thenReturn(Completable.error(new PolicyChainException("Policy requirements have failed")));
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertNotComplete().assertError(InvalidGrantException.class);
    }

    @Test
    public void grant_user_nominalCase_accessPolicy_grant() {
        AccessPolicy policy = mock(AccessPolicy.class);
        when(policy.getType()).thenReturn(AccessPolicyType.GROOVY);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(resourceService.findAccessPoliciesByResources(anyList())).thenReturn(Flowable.just(policy));
        when(executionContextFactory.create(any())).thenReturn(executionContext);
        when(rulesEngine.fire(any(), any())).thenReturn(Completable.complete());
        TestObserver<Token> testObserver = umaTokenGranter.grant(tokenRequest, client).test();
        testObserver.assertComplete().assertNoErrors().assertValue(token -> "success".equals(token.getValue()));
        OAuth2Request result = oauth2RequestCaptor.getValue();
        assertTrue(USER_ID.equals(result.getSubject()));
        assertTrue(assertNominalPermissions(result.getPermissions()));
        assertTrue(result.isSupportRefreshToken());
    }


    @Test
    public void checkMethodWillNotBeUsed() {
        TestObserver<TokenRequest> testObserver = umaTokenGranter.resolveRequest(tokenRequest, client, null).test();
        testObserver.assertNotComplete().assertError(TechnicalException.class);
    }

    //Assertion utils
    private boolean assertNeedInfoMissingClaim(Throwable throwable) {
        return this.assertNeedInfoRequiredClaims(throwable, 2);
    }

    private boolean assertNeedInfoBadClaimTokenFormat(Throwable throwable) {
        return this.assertNeedInfoRequiredClaims(throwable, 1) &&
                CLAIM_TOKEN_FORMAT.equals(((UmaException) throwable).getRequiredClaims().get(0).getName());
    }

    private boolean assertNeedInfoRequiredClaims(Throwable throwable, int requiredClaims) {
        return assertUmaException(throwable, "need_info", 403) && ((UmaException) throwable).getRequiredClaims().size() == requiredClaims;
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
     * Check formulae
     * RequestedScopes = PermissionTicket        ∪ (ClientRequested ∩ RSRegistered)
     * RequestedScopes = [rs_one(A) + rs_two(A)] ∪ (none            ∩ [rs_one(A,B,C) + rs_two(A,B,D)])
     * RequestedScopes = [rs_one(A) + rs_two(A)]
     */
    private boolean assertNominalPermissions(List<PermissionRequest> result) {
        return result != null && result.size() == 2 && result.stream()
                .filter(match(Arrays.asList("scopeA"), Arrays.asList("scopeA")))
                .count() == 2;
    }

    /**
     * Check formulae
     * RequestedScopes = PermissionTicket        ∪ (ClientRequested ∩ RSRegistered)
     * RequestedScopes = [rs_one(A) + rs_two(A)] ∪ ((B & C)         ∩ [rs_one(A,B,C) + rs_two(A,B,D)])
     * RequestedScopes = [rs_one(A) + rs_two(A)] ∪ [rs_one(B,C) + rs_two(B)])
     * RequestedScopes = [rs_one(A,B,C) + rs_two(A,B)]
     */
    private boolean assertAdditionalScopePermissions(List<PermissionRequest> result) {
        return result != null && result.size() == 2 && result.stream()
                .filter(match(Arrays.asList("scopeA", "scopeB", "scopeC"), Arrays.asList("scopeA", "scopeB")))
                .count() == 2;
    }

    /**
     * Check formulae
     * RequestedScopes = RPT_permission ∪
     * RequestedScopes = PermissionTicket        ∪ (ClientRequested ∩ RSRegistered)
     * RequestedScopes = [rs_one(A) + rs_two(A)] ∪ ((D)             ∩ [rs_one(A,B,C) + rs_two(A,B,D)])
     * RequestedScopes = [rs_one(A) + rs_two(A)] ∪ [rs_one() + rs_two(D)])
     * RequestedScopes = [rs_one(A) + rs_two(A,D)]
     * <p>
     * Adding previous RPT permission [rs_one(B)] such as final result = [rs_one(A,B) + rs_two(A,D)]
     */
    private boolean assertExtendedRptPermissions(List<PermissionRequest> result) {
        return result != null && result.size() == 2 && result.stream()
                .filter(match(Arrays.asList("scopeA", "scopeB"), Arrays.asList("scopeA", "scopeD")))
                .count() == 2;
    }

    private static Predicate<PermissionRequest> match(List<String> rsOne, List<String> rsTwo) {
        return permission ->
                (permission.getResourceId().equals(RS_ONE) && permission.getResourceScopes().size() == rsOne.size() && permission.getResourceScopes().containsAll(rsOne)) ||
                        (permission.getResourceId().equals(RS_TWO) && permission.getResourceScopes().size() == rsTwo.size() && permission.getResourceScopes().containsAll(rsTwo));
    }
}
