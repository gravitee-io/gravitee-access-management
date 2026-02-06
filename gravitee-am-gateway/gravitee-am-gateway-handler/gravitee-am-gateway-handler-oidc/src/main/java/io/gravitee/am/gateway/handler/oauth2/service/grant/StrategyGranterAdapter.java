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
package io.gravitee.am.gateway.handler.oauth2.service.grant;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ActorTokenInfo;
import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.oauth2.service.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.Response;
import io.reactivex.rxjava3.core.Single;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter that wraps a GrantStrategy as a TokenGranter.
 * This allows the new strategy-based architecture to work with the existing
 * CompositeTokenGranter infrastructure.
 *
 * @author GraviteeSource Team
 */
public class StrategyGranterAdapter implements TokenGranter {

    private static final Logger LOGGER = LoggerFactory.getLogger(StrategyGranterAdapter.class);
    private static final Set<String> SCOPE_RESOLUTION_EXCLUDED = Set.of(
            GrantType.TOKEN_EXCHANGE,
            GrantType.AUTHORIZATION_CODE,
            GrantType.REFRESH_TOKEN,
            GrantType.CIBA_GRANT_TYPE,
            GrantType.UMA
    );

    @Getter
    private final GrantStrategy strategy;
    private final Domain domain;
    private final TokenService tokenService;
    private final RulesEngine rulesEngine;
    private final TokenRequestResolver tokenRequestResolver;

    public StrategyGranterAdapter(
            GrantStrategy strategy,
            Domain domain,
            TokenService tokenService,
            RulesEngine rulesEngine,
            TokenRequestResolver tokenRequestResolver) {
        this.strategy = strategy;
        this.domain = domain;
        this.tokenService = tokenService;
        this.rulesEngine = rulesEngine;
        this.tokenRequestResolver = tokenRequestResolver;
    }

    @Override
    public boolean handle(String grantType, Client client) {
        return strategy.supports(grantType, client, domain);
    }

    @Override
    public Single<Token> grant(TokenRequest tokenRequest, Client client) {
        return grant(tokenRequest, null, client);
    }

    @Override
    public Single<Token> grant(TokenRequest tokenRequest, Response response, Client client) {
        LOGGER.debug("Processing grant via strategy adapter for client: {}, grant type: {}",
                client.getClientId(), tokenRequest.getGrantType());

        return strategy.process(tokenRequest, client, domain)
                .flatMap(creationRequest -> resolveScopes(creationRequest, client))
                .flatMap(resolved -> {
                    // Create ONE OAuth2Request that will be reused throughout the entire flow.
                    // This ensures PRE_TOKEN policy modifications (scopes, resources, claims, etc.)
                    // are preserved and passed to token creation and POST_TOKEN policy.
                    OAuth2Request oAuth2Request = toOAuth2Request(resolved);
                    User user = resolved.resourceOwner();

                    return executePreTokenPolicy(oAuth2Request, response, client, user)
                            .flatMap(ignored -> createToken(oAuth2Request, client, resolved))
                            .flatMap(token -> executePostTokenPolicy(oAuth2Request, client, user, token));
                });
    }

    private Single<TokenCreationRequest> resolveScopes(TokenCreationRequest request, Client client) {
        if (tokenRequestResolver == null || SCOPE_RESOLUTION_EXCLUDED.contains(request.grantType())) {
            return Single.just(request);
        }

        TokenRequest resolverRequest = buildResolverRequest(request);
        return tokenRequestResolver.resolve(resolverRequest, client, request.resourceOwner())
                .map(resolved -> request.withScopes(resolved.getScopes()));
    }

    private TokenRequest buildResolverRequest(TokenCreationRequest request) {
        TokenRequest resolverRequest = new TokenRequest();
        resolverRequest.setClientId(request.clientId());
        resolverRequest.setGrantType(request.grantType());
        resolverRequest.setScopes(request.scopes());
        resolverRequest.setResources(request.resources());
        if (request.resourceOwner() != null) {
            resolverRequest.setSubject(request.resourceOwner().getId());
        }
        return resolverRequest;
    }

    private Single<OAuth2Request> executePreTokenPolicy(
            OAuth2Request oAuth2Request,
            Response response,
            Client client,
            User user) {

        return rulesEngine.fire(ExtensionPoint.PRE_TOKEN, oAuth2Request, response, client, user)
                .map(executionContext -> {
                    // Add execution context attributes to OAuth2Request so they're available
                    // for token creation and POST_TOKEN policy
                    oAuth2Request.getExecutionContext().putAll(executionContext.getAttributes());
                    return oAuth2Request;
                });
    }

    private Single<Token> createToken(OAuth2Request oAuth2Request, Client client, TokenCreationRequest request) {
        // Use the same OAuth2Request that was passed through PRE_TOKEN policy
        // so any modifications made by policies are preserved
        return tokenService.create(oAuth2Request, client, request.resourceOwner())
                .map(token -> {
                    if (GrantType.REFRESH_TOKEN.equals(request.grantType())
                            && client.isDisableRefreshTokenRotation()
                            && request.grantData() instanceof GrantData.RefreshTokenData refreshTokenData) {
                        token.setRefreshToken(refreshTokenData.refreshToken());
                    }
                    if (request.grantData() instanceof GrantData.UmaData umaData && umaData.upgraded()) {
                        token.setUpgraded(true);
                    }
                    return token;
                });
    }

    private Single<Token> executePostTokenPolicy(
            OAuth2Request oAuth2Request,
            Client client,
            User user,
            Token token) {

        // Use the same OAuth2Request that was used throughout the flow
        return rulesEngine.fire(ExtensionPoint.POST_TOKEN, oAuth2Request, client, user)
                .map(executionContext -> token);
    }

    private OAuth2Request toOAuth2Request(TokenCreationRequest request) {
        OAuth2Request oAuth2Request = new OAuth2Request();

        oAuth2Request.setClientId(request.clientId());
        oAuth2Request.setGrantType(request.grantType());
        oAuth2Request.setScopes(request.scopes());
        oAuth2Request.setSupportRefreshToken(request.supportRefreshToken());
        oAuth2Request.setResources(request.resources());
        oAuth2Request.setOriginalAuthorizationResources(request.originalAuthorizationResources());
        if (request.context() != null) {
            oAuth2Request.setContext(request.context());
        }

        if (request.resourceOwner() != null) {
            oAuth2Request.setSubject(request.resourceOwner().getId());
        }

        HttpRequestInfo httpInfo = request.httpInfo();
        if (httpInfo != null) {
            oAuth2Request.setId(httpInfo.id());
            oAuth2Request.setTransactionId(httpInfo.transactionId());
            oAuth2Request.setUri(httpInfo.uri());
            oAuth2Request.setPath(httpInfo.path());
            oAuth2Request.setPathInfo(httpInfo.pathInfo());
            oAuth2Request.setContextPath(httpInfo.contextPath());
            oAuth2Request.setOrigin(httpInfo.origin());
            oAuth2Request.setScheme(httpInfo.scheme());
            oAuth2Request.setRemoteAddress(httpInfo.remoteAddress());
            oAuth2Request.setLocalAddress(httpInfo.localAddress());
            oAuth2Request.setHost(httpInfo.host());
            oAuth2Request.setHeaders(httpInfo.headers());
            MultiValueMap<String, String> parameters = httpInfo.parameters();
            MultiValueMap<String, String> safeParameters = parameters == null
                    ? null
                    : new LinkedMultiValueMap<>(parameters);
            if (safeParameters != null) {
                safeParameters.remove(Parameters.PASSWORD);
                safeParameters.remove(Parameters.CLIENT_SECRET);
            }
            oAuth2Request.setParameters(safeParameters);
            oAuth2Request.setMethod(httpInfo.method());
            oAuth2Request.setVersion(httpInfo.version());
            oAuth2Request.setTimestamp(httpInfo.timestamp());
            oAuth2Request.setConfirmationMethodX5S256(httpInfo.confirmationMethodX5S256());
            oAuth2Request.setHttpResponse(httpInfo.httpResponse());
        }

        if (request.additionalParameters() != null) {
            oAuth2Request.setAdditionalParameters(request.additionalParameters());
        }

        if (request.executionContext() != null) {
            oAuth2Request.getExecutionContext().putAll(request.executionContext());
        }

        handleGrantSpecificData(oAuth2Request, request);

        return oAuth2Request;
    }

    private void handleGrantSpecificData(OAuth2Request oAuth2Request, TokenCreationRequest request) {
        GrantData grantData = request.grantData();

        switch (grantData) {
            case GrantData.TokenExchangeData data -> {
                oAuth2Request.setIssuedTokenType(data.issuedTokenType());
                oAuth2Request.setExchangeExpiration(data.expiration());
                oAuth2Request.setSubjectTokenId(data.subjectTokenId());
                oAuth2Request.setSubjectTokenType(data.subjectTokenType());
                oAuth2Request.setDelegation(data.isDelegation());
                if (data.isDelegation()) {
                    oAuth2Request.setActorTokenId(data.actorTokenId());
                    oAuth2Request.setActorTokenType(data.actorTokenType());
                    oAuth2Request.setActClaim(buildActClaim(data.actorInfo()));
                }
            }
            case GrantData.AuthorizationCodeData data -> oAuth2Request.setAuthorizationCode(data.authorizationCode());
            case GrantData.RefreshTokenData data -> {
                oAuth2Request.setRefreshToken(data.decodedRefreshToken());
                if (data.originalResources() != null) {
                    oAuth2Request.setOriginalAuthorizationResources(data.originalResources());
                }
                // Propagate UMA 2.0 permissions from refresh token
                if (data.decodedRefreshToken() != null && data.decodedRefreshToken().get("permissions") != null) {
                    @SuppressWarnings("unchecked")
                    List<PermissionRequest> permissions = (List<PermissionRequest>) data.decodedRefreshToken().get("permissions");
                    oAuth2Request.setPermissions(permissions);
                }
            }
            case GrantData.UmaData data -> oAuth2Request.setPermissions(data.permissions());
            case GrantData.ClientCredentialsData ignored -> { }
            case GrantData.PasswordData ignored -> { }
            case GrantData.CibaData ignored -> { }
            case GrantData.ExtensionGrantData ignored -> { }
        }
    }

    /**
     * Build the "act" claim for delegation scenarios per RFC 8693 Section 4.1.
     * The "act" claim contains the "sub" claim identifying the actor (required),
     * and optionally the "gis" claim for actor identification.
     * If the subject token already has an "act" claim, it represents the prior
     * delegation chain and is nested under the current actor.
     * If the actor token itself is a delegated token (has an "act" claim),
     * it is included as "actor_act" for complete audit traceability.
     *
     * @param actorInfo the actor token information
     * @return the "act" claim as a Map
     */
    private Map<String, Object> buildActClaim(ActorTokenInfo actorInfo) {
        Map<String, Object> actClaim = new HashMap<>();

        // Required per RFC 8693: subject of the actor
        actClaim.put(Claims.SUB, actorInfo.subject());

        // Include "gis" claim if present
        if (actorInfo.hasGis()) {
            actClaim.put(Claims.GIO_INTERNAL_SUB, actorInfo.gis());
        }

        // Per RFC 8693 Section 4.1: if the subject token has an "act" claim,
        // nest it under the current actor to preserve the delegation chain
        if (actorInfo.hasSubjectTokenActClaim()) {
            actClaim.put(Claims.ACT, actorInfo.subjectTokenActClaim());
        }

        // If the actor token itself is a delegated token, include its "act" claim
        // as "actor_act" for complete audit traceability of the actor's delegation chain
        if (actorInfo.hasActorTokenActClaim()) {
            actClaim.put("actor_act", actorInfo.actorTokenActClaim());
        }

        return actClaim;
    }

}

