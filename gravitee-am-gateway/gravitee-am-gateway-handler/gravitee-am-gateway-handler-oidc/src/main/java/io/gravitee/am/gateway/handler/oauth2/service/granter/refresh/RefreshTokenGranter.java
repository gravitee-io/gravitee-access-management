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
package io.gravitee.am.gateway.handler.oauth2.service.granter.refresh;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.policy.RulesEngine;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.validation.ResourceConsistencyValidationService;
import io.gravitee.am.gateway.handler.oauth2.service.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequestResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Implementation of the Refresh Token Grant Flow
 * See <a href="https://tools.ietf.org/html/rfc6749#section-6">6. Refreshing an Access Token</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RefreshTokenGranter extends AbstractTokenGranter {

    private UserAuthenticationManager userAuthenticationManager;
    private ResourceConsistencyValidationService resourceConsistencyValidationService;

    public RefreshTokenGranter() {
        super(GrantType.REFRESH_TOKEN);
    }

    public RefreshTokenGranter(TokenRequestResolver tokenRequestResolver,
                               TokenService tokenService,
                               UserAuthenticationManager userAuthenticationManager,
                               ResourceConsistencyValidationService resourceConsistencyValidationService,
                               RulesEngine rulesEngine) {
        this();
        setTokenRequestResolver(tokenRequestResolver);
        setTokenService(tokenService);
        setRulesEngine(rulesEngine);
        this.userAuthenticationManager = userAuthenticationManager;
        this.resourceConsistencyValidationService = resourceConsistencyValidationService;
    }

    @Override
    protected Single<TokenRequest> parseRequest(TokenRequest tokenRequest, Client client) {
        String refreshToken = tokenRequest.parameters().getFirst(Parameters.REFRESH_TOKEN);

        if (refreshToken == null || refreshToken.isEmpty()) {
            return Single.error(new InvalidRequestException("A refresh token must be supplied."));
        }

        return super.parseRequest(tokenRequest, client)
                .flatMap(tokenRequest1 -> getTokenService().refresh(refreshToken, tokenRequest, client)
                        .flatMap(refreshToken1 -> {
                            // set resource owner
                            if (refreshToken1.getSubject() != null) {
                                tokenRequest1.setSubject(refreshToken1.getSubject());
                            }
                            // set scopes
                            // The requested scope MUST NOT include any scope
                            // not originally granted by the resource owner, and if omitted is
                            // treated as equal to the scope originally granted by the resource owner.
                            final Set<String> originalScopes = (refreshToken1.getScope() != null ? new HashSet<>(Arrays.asList(refreshToken1.getScope().split("\\s+"))) : null);
                            final Set<String> requestedScopes = tokenRequest1.getScopes();
                            if (requestedScopes == null || requestedScopes.isEmpty()) {
                                tokenRequest1.setScopes(originalScopes);
                            } else if (originalScopes != null && !originalScopes.isEmpty()) {
                                Set<String> filteredScopes = requestedScopes
                                        .stream()
                                        .filter(requestedScope -> originalScopes.contains(requestedScope))
                                        .collect(Collectors.toSet());
                                tokenRequest1.setScopes(filteredScopes);
                            }
                            // set decoded refresh token to the current request
                            tokenRequest1.setRefreshToken(refreshToken1.getAdditionalInformation());
                            
                            // Extract original resources from refresh token for RFC 8707 compliance
                            Set<String> originalResources = extractResourcesFromRefreshToken(refreshToken1.getAdditionalInformation());
                            
                            // Validate resource consistency according to RFC 8707
                            return resourceConsistencyValidationService.validateConsistency(tokenRequest1, originalResources)
                                    .andThen(Single.fromCallable(() -> {
                                        // Set resources for token creation:
                                        // 1. If token request has resources, use them (already validated as subset)
                                        // 2. If no token request resources, use original resources from refresh token
                                        Set<String> finalResources = tokenRequest1.getResources();
                                        if (finalResources == null || finalResources.isEmpty()) {
                                            finalResources = originalResources;
                                        }
                                        tokenRequest1.setResources(finalResources);
                                        
                                        return tokenRequest1;
                                    }));
                        }));
    }

    @Override
    protected Maybe<User> resolveResourceOwner(TokenRequest tokenRequest, Client client) {
        final String subject = tokenRequest.getSubject();

        if (subject == null) {
            return Maybe.empty();
        }

        // Build JWT using the getRefreshToken as those params contains
        // all the claims of the original token so that we are able to
        // provide a JWT to the auth manager to retrieve the right information
        // to get the user profile.
        final var jwt = new JWT(tokenRequest.getRefreshToken());
        return userAuthenticationManager.loadPreAuthenticatedUserBySub(jwt, tokenRequest)
                .onErrorResumeNext(ex -> Maybe.error(new InvalidGrantException(isBlank(ex.getMessage()) ? "unable to read user profile" : ex.getMessage())));
    }

    @Override
    protected Single<TokenRequest> resolveRequest(TokenRequest tokenRequest, Client client, User endUser) {
        // request has already been resolved during parse request step
        return Single.just(tokenRequest);
    }

    @Override
    protected boolean isSupportRefreshToken(Client client) {
        // do not issue a new refresh token if token rotation is disabled
        return !client.isDisableRefreshTokenRotation() && super.isSupportRefreshToken(client);
    }

    /**
     * Extract original resources from refresh token JWT for RFC 8707 compliance.
     * The refresh token contains the orig_resources custom claim with the original authorization resources.
     *
     * @param refreshTokenJWT the refresh token JWT as additional information
     * @return set of original resource identifiers, empty set if none
     */
    private Set<String> extractResourcesFromRefreshToken(Map<String, Object> refreshTokenJWT) {
        if (refreshTokenJWT == null || !refreshTokenJWT.containsKey("orig_resources")) {
            return new HashSet<>();
        }

        Object origResourcesClaim = refreshTokenJWT.get("orig_resources");
        if (origResourcesClaim == null) {
            return new HashSet<>();
        }

        Set<String> resources = new HashSet<>();
        if (origResourcesClaim instanceof java.util.List) {
            // Multiple resources (array)
            @SuppressWarnings("unchecked")
            java.util.List<Object> resourceList = (java.util.List<Object>) origResourcesClaim;
            for (Object resource : resourceList) {
                if (resource instanceof String) {
                    resources.add((String) resource);
                }
            }
        } else if (origResourcesClaim instanceof String) {
            // Single resource (edge case, but handle it)
            resources.add((String) origResourcesClaim);
        }

        return resources;
    }

    @Override
    protected Single<Token> createAccessToken(OAuth2Request oAuth2Request, Client client, User endUser) {
        return super.createAccessToken(oAuth2Request, client, endUser)
                .map(token -> {
                    if (!client.isDisableRefreshTokenRotation()) {
                        return token;
                    }
                    // if token rotation is disabled, return the same original refresh_token
                    token.setRefreshToken(oAuth2Request.getParameters().getFirst(Parameters.REFRESH_TOKEN));
                    return token;
                });
    }
}
