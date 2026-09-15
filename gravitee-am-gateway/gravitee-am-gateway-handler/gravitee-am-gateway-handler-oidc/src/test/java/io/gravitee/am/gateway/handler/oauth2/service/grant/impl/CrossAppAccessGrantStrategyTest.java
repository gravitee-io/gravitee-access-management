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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.ExtensionGrantPluginType;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.extensiongrant.api.ResolvedEndUser;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantData;
import io.gravitee.am.gateway.handler.oauth2.service.grant.IdJagAssertionContext;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.idJagAssertion;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.jwtBearerRequest;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.plainJwtAssertion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrossAppAccessGrantStrategyTest {

    private static final String ORIGIN = "https://gateway.example.com";
    private static final String DOMAIN_ISSUER = "https://gateway.example.com/domain-b/oidc";
    private static final String ENTERPRISE_ISSUER = "https://gateway.example.com/domain-a/oidc";
    private static final String IDENTITY_PROVIDER = "idp-id";

    @Mock
    private ExtensionGrantProvider extensionGrantProvider;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private UserGatewayService userService;

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    private final Logger grantStrategiesLogger = (Logger) LoggerFactory.getLogger(CrossAppAccessGrantStrategy.class.getPackageName());

    private ListAppender<ILoggingEvent> capturedLogs;

    private CrossAppAccessGrantStrategy strategy;
    private ExtensionGrant extensionGrant;
    private Domain domain;
    private Client client;

    @BeforeEach
    void setUp() {
        extensionGrant = new ExtensionGrant();
        extensionGrant.setId("caa-id");
        extensionGrant.setType(ExtensionGrantPluginType.CROSS_APP_ACCESS);
        extensionGrant.setGrantType(GrantType.JWT_BEARER);
        extensionGrant.setCreatedAt(new Date(2000));

        domain = new Domain();
        domain.setId("domain-id");

        client = new Client();
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER + "~caa-id"));

        strategy = new CrossAppAccessGrantStrategy(
                extensionGrantProvider,
                extensionGrant,
                userAuthenticationManager,
                identityProviderManager,
                userService,
                null,
                domain,
                openIDDiscoveryService);
    }

    @AfterEach
    void stopCapturingLogs() {
        if (capturedLogs != null) {
            grantStrategiesLogger.detachAppender(capturedLogs);
            grantStrategiesLogger.setLevel(null);
        }
    }

    @Test
    void shouldSupportIdJagRequestWhenClientAuthorizesSuffixedGrantTypeOfNewerGrant() {
        pluginAcceptsAssertion();
        strategy.setOldestExtensionGrantId("older-caa-id");
        assertTrue(strategy.supports(jwtBearerRequest(idJagAssertion()), client, domain));
    }

    @Test
    void shouldSupportIdJagRequestWhenClientAuthorizesBareGrantTypeAndGrantIsOldestCrossAppAccessGrant() {
        pluginAcceptsAssertion();
        strategy.setOldestExtensionGrantId("caa-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER));
        assertTrue(strategy.supports(jwtBearerRequest(idJagAssertion()), client, domain));
    }

    @Test
    void shouldNotSupportBareGrantTypeWhenAnotherCrossAppAccessGrantIsOldest() {
        strategy.setOldestExtensionGrantId("older-caa-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER));
        assertFalse(strategy.supports(jwtBearerRequest(idJagAssertion()), client, domain));
    }

    @Test
    void shouldNotSupportAssertionThePluginDeclines() {
        when(extensionGrantProvider.supports(any())).thenReturn(false);

        assertFalse(strategy.supports(jwtBearerRequest(plainJwtAssertion()), client, domain));
    }

    @Test
    void shouldNotSupportOtherGrantTypesEvenWithIdJagAssertion() {
        TokenRequest request = new TokenRequest();
        request.setClientId("client-id");
        request.setGrantType(GrantType.CLIENT_CREDENTIALS);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.ASSERTION, idJagAssertion());
        request.setParameters(parameters);
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER, GrantType.CLIENT_CREDENTIALS));

        assertFalse(strategy.supports(request, client, domain));
    }

    @Test
    void shouldNotSupportWhenClientDoesNotAuthorizeJwtBearer() {
        client.setAuthorizedGrantTypes(List.of(GrantType.CLIENT_CREDENTIALS));
        assertFalse(strategy.supports(jwtBearerRequest(idJagAssertion()), client, domain));
    }

    @Test
    void shouldNotSupportWhenClientAuthorizesAnotherJwtBearerGrantOnly() {
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER + "~other-id"));
        assertFalse(strategy.supports(jwtBearerRequest(idJagAssertion()), client, domain));
    }

    @Test
    void shouldNotSupportGrantTypeAlone() {
        strategy.setOldestExtensionGrantId("caa-id");
        assertFalse(strategy.supports(GrantType.JWT_BEARER, client, domain));
    }

    private void pluginAcceptsAssertion() {
        when(extensionGrantProvider.supports(any())).thenReturn(true);
    }

    @Test
    void shouldRedeemVerifiedAssertionForTheAssertionSubjectInTransientMode() {
        String assertion = idJagAssertion();
        givenVerifiedAssertion(verifiedClaims());

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(assertion), client, domain).blockingGet();

        assertEquals("alice", creationRequest.resourceOwner().getId());
        assertEquals("client-id", creationRequest.clientId());
        verify(extensionGrantProvider).resolveEndUser(argThat(pluginRequest ->
                assertion.equals(pluginRequest.getRequestParameters().get(Parameters.ASSERTION))
                        && "client-id".equals(pluginRequest.getClientId())));
        verify(extensionGrantProvider, never()).grant(any());
        verifyNoInteractions(userAuthenticationManager, identityProviderManager, userService);
    }

    @Test
    void shouldAcceptAssertionIssuedByAnotherDomainOnTheSameGateway() {
        givenVerifiedAssertion(verifiedClaims());

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertNoErrors();
    }

    @Test
    void shouldRefuseAssertionIssuedByThisDomainForTheRequestOrigin() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.ISS, DOMAIN_ISSUER);
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion was issued by this domain"));

        verifyNoInteractions(userAuthenticationManager, identityProviderManager, userService);
    }

    @Test
    void shouldRefuseAssertionWhoseAudienceArrayOmitsTheDomainIssuer() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.AUD, List.of("https://other-as.example.com", ENTERPRISE_ISSUER));
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion audience does not include this domain"));
    }

    @Test
    void shouldRefuseAssertionWhoseAudienceStringIsAnotherIssuer() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.AUD, "https://other-as.example.com");
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion audience does not include this domain"));
    }

    @Test
    void shouldRefuseAssertionWithoutAudience() {
        Map<String, Object> claims = verifiedClaims();
        claims.remove(Claims.AUD);
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion audience does not include this domain"));
    }

    @Test
    void shouldAcceptAudienceStringEqualToTheDomainIssuer() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.AUD, DOMAIN_ISSUER);
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertNoErrors();
    }

    @Test
    void shouldAcceptAudienceArrayContainingTheDomainIssuer() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.AUD, List.of("https://other-as.example.com", DOMAIN_ISSUER));
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertNoErrors();
    }

    @Test
    void shouldRefuseAssertionMintedForAnotherClient() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.CLIENT_ID, "other-agent");
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion client_id does not match the authenticated client"));

        verifyNoInteractions(userAuthenticationManager, identityProviderManager, userService);
    }

    @Test
    void shouldRefuseAssertionWithoutClientId() {
        Map<String, Object> claims = verifiedClaims();
        claims.remove(Claims.CLIENT_ID);
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion client_id does not match the authenticated client"));
    }

    @Test
    void shouldRefuseAssertionWithNonStringClientId() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.CLIENT_ID, 42L);
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion client_id does not match the authenticated client"));
    }

    @Test
    void shouldNotLogTheRawAssertion() {
        String assertion = idJagAssertion();
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.CLIENT_ID, "other-agent");
        givenVerifiedAssertion(claims);
        ListAppender<ILoggingEvent> logs = captureLogs();

        strategy.process(redemptionRequest(assertion), client, domain).test().assertError(InvalidGrantException.class);

        assertFalse(logs.list.isEmpty());
        assertTrue(logs.list.stream().noneMatch(event -> event.getFormattedMessage().contains(assertion)));
    }

    @Test
    void shouldCompareAssertionClientIdWithTheResolvedPublicClient() {
        client.setTokenEndpointAuthMethod(ClientAuthenticationMethod.NONE);
        givenVerifiedAssertion(verifiedClaims());

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertNoErrors();
    }

    @Test
    void shouldReportTheFirstFailingCheckInRedemptionOrder() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.AUD, "https://other-as.example.com");
        claims.put(Claims.CLIENT_ID, "other-agent");
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion audience does not include this domain"));
    }

    @Test
    void shouldReportSelfIssuedAssertionBeforeAudienceAndClientChecks() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.ISS, DOMAIN_ISSUER);
        claims.put(Claims.AUD, "https://other-as.example.com");
        claims.put(Claims.CLIENT_ID, "other-agent");
        givenVerifiedAssertion(claims);

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("Assertion was issued by this domain"));
    }

    @Test
    void shouldBindLocalUserByExternalIdSubjectAndResolvedIdentityProviderInCheckUserMode() {
        extensionGrant.setUserExists(true);
        User localUser = new User();
        localUser.setId("local-user-id");
        localUser.setExternalId("alice");
        localUser.setSource(IDENTITY_PROVIDER);
        givenVerifiedAssertion(verifiedClaims());
        when(userService.findByExternalIdAndSource("alice", IDENTITY_PROVIDER)).thenReturn(Maybe.just(localUser));

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertEquals("local-user-id", creationRequest.resourceOwner().getId());
        verifyNoInteractions(identityProviderManager, userAuthenticationManager);
    }

    @Test
    void shouldBindWithTheVerifiedSubjectClaim() {
        extensionGrant.setUserExists(true);
        User localUser = new User();
        localUser.setId("local-user-id");
        when(openIDDiscoveryService.getIssuer(ORIGIN)).thenReturn(DOMAIN_ISSUER);
        DefaultUser projectedEndUser = new DefaultUser("mallory");
        projectedEndUser.setId("mallory");
        when(extensionGrantProvider.resolveEndUser(any()))
                .thenReturn(Maybe.just(new ResolvedEndUser(projectedEndUser, IDENTITY_PROVIDER, verifiedClaims())));
        when(userService.findByExternalIdAndSource("alice", IDENTITY_PROVIDER)).thenReturn(Maybe.just(localUser));

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertEquals("local-user-id", creationRequest.resourceOwner().getId());
    }

    @Test
    void shouldRefuseWhenNoLocalUserMatchesTheAssertionSubjectInCheckUserMode() {
        extensionGrant.setUserExists(true);
        givenVerifiedAssertion(verifiedClaims());
        when(userService.findByExternalIdAndSource("alice", IDENTITY_PROVIDER)).thenReturn(Maybe.empty());

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("No user matches the assertion subject"));

        verifyNoInteractions(identityProviderManager);
    }

    @Test
    void shouldRefuseWithoutLookingUpUsersWhenVerifiedSubjectIsMissingInCheckUserMode() {
        extensionGrant.setUserExists(true);
        Map<String, Object> claims = verifiedClaims();
        claims.remove(Claims.SUB);
        when(openIDDiscoveryService.getIssuer(ORIGIN)).thenReturn(DOMAIN_ISSUER);
        when(extensionGrantProvider.resolveEndUser(any()))
                .thenReturn(Maybe.just(new ResolvedEndUser(new DefaultUser("alice"), IDENTITY_PROVIDER, claims)));

        strategy.process(redemptionRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(refusal("No user matches the assertion subject"));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldPersistOnlyTheProjectedProfileFromTheResolvedIdentityProviderInCreateUserMode() {
        extensionGrant.setCreateUser(true);
        User connectedUser = new User();
        connectedUser.setId("local-user-id");
        givenVerifiedAssertion(verifiedClaims());
        when(userAuthenticationManager.connect(any(), any(), eq(false))).thenReturn(Single.just(connectedUser));

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertEquals("local-user-id", creationRequest.resourceOwner().getId());
        verify(userAuthenticationManager).connect(argThat(persisted -> "alice".equals(persisted.getId())
                && IDENTITY_PROVIDER.equals(persisted.getAdditionalInformation().get("source"))
                && !persisted.getAdditionalInformation().containsKey(Claims.JTI)
                && !persisted.getAdditionalInformation().containsKey(Claims.AUD)
                && !persisted.getAdditionalInformation().containsKey(Claims.SCOPE)), any(), eq(false));
        verifyNoInteractions(userService, identityProviderManager);
    }

    @Test
    void shouldCarryTheAssertionSubjectWithoutAssertionClaimsInTransientMode() {
        givenVerifiedAssertion(verifiedClaims());

        User resourceOwner = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet().resourceOwner();

        assertEquals("alice", resourceOwner.getId());
        assertEquals(Map.of(Claims.SUB, "alice"), resourceOwner.getAdditionalInformation());
    }

    @Test
    void shouldNotSupportRefreshTokenEvenWhenApplicationAllowsRefreshTokenGrant() {
        extensionGrant.setUserExists(true);
        client.setAuthorizedGrantTypes(List.of(GrantType.JWT_BEARER + "~caa-id", GrantType.REFRESH_TOKEN));
        User localUser = new User();
        localUser.setId("local-user-id");
        givenVerifiedAssertion(verifiedClaims());
        when(userService.findByExternalIdAndSource("alice", IDENTITY_PROVIDER)).thenReturn(Maybe.just(localUser));

        TokenCreationRequest creationRequest = strategy.process(redemptionRequest(idJagAssertion()), client, domain).blockingGet();

        assertFalse(creationRequest.supportRefreshToken());
    }

    @Test
    void shouldProjectVerifiedAssertionContextIntoExtensionGrantDataOnSuccess() {
        TokenRequest request = redemptionRequest(idJagAssertion());
        givenVerifiedAssertion(verifiedClaims());

        TokenCreationRequest creationRequest = strategy.process(request, client, domain).blockingGet();

        IdJagAssertionContext expected = new IdJagAssertionContext(ENTERPRISE_ISSUER, IDENTITY_PROVIDER, "jti-1", "client-id");
        assertEquals(expected, request.getIdJagAssertionContext());
        assertEquals(expected, ((GrantData.ExtensionGrantData) creationRequest.grantData()).idJagAssertionContext());
    }

    @Test
    void shouldKeepVerifiedAssertionContextOnTheRequestWhenAGatewayCheckRefuses() {
        TokenRequest request = redemptionRequest(idJagAssertion());
        Map<String, Object> claims = verifiedClaims();
        claims.put(Claims.CLIENT_ID, "other-agent");
        givenVerifiedAssertion(claims);

        strategy.process(request, client, domain).test().assertError(InvalidGrantException.class);

        assertEquals(new IdJagAssertionContext(ENTERPRISE_ISSUER, IDENTITY_PROVIDER, "jti-1", "other-agent"), request.getIdJagAssertionContext());
    }

    @Test
    void shouldAttachNoAssertionContextWhenThePluginRefuses() {
        TokenRequest request = redemptionRequest(idJagAssertion());
        when(extensionGrantProvider.resolveEndUser(any()))
                .thenReturn(Maybe.error(new io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException("Assertion verification failed")));

        strategy.process(request, client, domain).test().assertError(InvalidGrantException.class);

        assertNull(request.getIdJagAssertionContext());
    }

    @Test
    void shouldPropagatePluginRefusalAsInvalidGrantKeepingItsDescription() {
        when(extensionGrantProvider.resolveEndUser(any()))
                .thenReturn(Maybe.error(new io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException("Assertion issuer is not trusted")));

        strategy.process(jwtBearerRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(error -> error instanceof InvalidGrantException && "Assertion issuer is not trusted".equals(error.getMessage()));
    }

    @Test
    void shouldRefuseWhenPluginResolvesNoEndUser() {
        when(extensionGrantProvider.resolveEndUser(any())).thenReturn(Maybe.empty());

        strategy.process(jwtBearerRequest(idJagAssertion()), client, domain)
                .test()
                .assertError(InvalidGrantException.class);

        verifyNoInteractions(userAuthenticationManager, identityProviderManager, userService);
    }

    private ListAppender<ILoggingEvent> captureLogs() {
        capturedLogs = new ListAppender<>();
        capturedLogs.start();
        grantStrategiesLogger.setLevel(Level.TRACE);
        grantStrategiesLogger.addAppender(capturedLogs);
        return capturedLogs;
    }

    private void givenVerifiedAssertion(Map<String, Object> verifiedClaims) {
        when(openIDDiscoveryService.getIssuer(ORIGIN)).thenReturn(DOMAIN_ISSUER);
        DefaultUser endUser = new DefaultUser((String) verifiedClaims.get(Claims.SUB));
        endUser.setId((String) verifiedClaims.get(Claims.SUB));
        endUser.setAdditionalInformation(new HashMap<>(Map.of(Claims.SUB, verifiedClaims.get(Claims.SUB))));
        when(extensionGrantProvider.resolveEndUser(any()))
                .thenReturn(Maybe.just(new ResolvedEndUser(endUser, IDENTITY_PROVIDER, verifiedClaims)));
    }

    private static Predicate<Throwable> refusal(String description) {
        return error -> error instanceof InvalidGrantException && description.equals(error.getMessage());
    }

    private static Map<String, Object> verifiedClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.ISS, ENTERPRISE_ISSUER);
        claims.put(Claims.SUB, "alice");
        claims.put(Claims.AUD, List.of(DOMAIN_ISSUER));
        claims.put(Claims.CLIENT_ID, "client-id");
        claims.put(Claims.JTI, "jti-1");
        claims.put(Claims.SCOPE, "calendar.read");
        return claims;
    }

    private static TokenRequest redemptionRequest(String assertion) {
        TokenRequest request = jwtBearerRequest(assertion);
        request.setOrigin(ORIGIN);
        return request;
    }
}
