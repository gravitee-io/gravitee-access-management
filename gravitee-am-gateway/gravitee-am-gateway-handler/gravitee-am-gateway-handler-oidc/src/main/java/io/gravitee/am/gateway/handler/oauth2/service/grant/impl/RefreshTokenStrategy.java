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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.OrigResourcesUtils;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantStrategy;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.validation.ResourceConsistencyValidationService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Strategy for OAuth 2.0 Refresh Token Grant.
 * Handles validation and refresh of access tokens.
 *
 * @see <a href="https://tools.ietf.org/html/rfc6749#section-6">RFC 6749 Section 6</a>
 * @author GraviteeSource Team
 */
public class RefreshTokenStrategy implements GrantStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenStrategy.class);

    private final TokenService tokenService;
    private final UserAuthenticationManager userAuthenticationManager;
    private final ResourceConsistencyValidationService resourceConsistencyValidationService;

    public RefreshTokenStrategy(
            TokenService tokenService,
            UserAuthenticationManager userAuthenticationManager,
            ResourceConsistencyValidationService resourceConsistencyValidationService) {
        this.tokenService = tokenService;
        this.userAuthenticationManager = userAuthenticationManager;
        this.resourceConsistencyValidationService = resourceConsistencyValidationService;
    }

    @Override
    public boolean supports(String grantType, Client client, Domain domain) {
        if (!GrantType.REFRESH_TOKEN.equals(grantType)) {
            return false;
        }

        if (!client.hasGrantType(GrantType.REFRESH_TOKEN)) {
            LOGGER.debug("Client {} does not support refresh_token grant type", client.getClientId());
            return false;
        }

        return true;
    }

    @Override
    public Single<TokenCreationRequest> process(TokenRequest request, Client client, Domain domain) {
        LOGGER.debug("Processing refresh token request for client: {}", client.getClientId());

        String refreshTokenValue = request.parameters().getFirst(Parameters.REFRESH_TOKEN);

        if (refreshTokenValue == null || refreshTokenValue.isEmpty()) {
            return Single.error(new InvalidRequestException("A refresh token must be supplied."));
        }

        return tokenService.refresh(refreshTokenValue, request, client)
                .flatMap(refreshToken -> processRefreshToken(request, client, refreshToken, refreshTokenValue));
    }

    private Single<TokenCreationRequest> processRefreshToken(
            TokenRequest request,
            Client client,
            Token refreshToken,
            String refreshTokenValue) {

        String subject = refreshToken.getSubject();
        Map<String, Object> decodedRefreshToken = refreshToken.getAdditionalInformation();

        // Handle scopes - requested scopes must be subset of original
        Set<String> resolvedScopes = resolveScopes(request.getScopes(), refreshToken.getScope());

        // Extract original resources from refresh token
        Set<String> originalResources = OrigResourcesUtils.extractOrigResources(decodedRefreshToken);

        // Resolve final resources according to RFC 8707
        Set<String> finalResources = resourceConsistencyValidationService.resolveFinalResources(request, originalResources);
        request.setResources(finalResources);

        // Determine if refresh token rotation is enabled
        boolean supportRefresh = !client.isDisableRefreshTokenRotation() &&
                client.hasGrantType(GrantType.REFRESH_TOKEN);

        // Load user if subject is present
        if (subject == null) {
            // Client-only refresh (no user)
            return Single.just(TokenCreationRequest.forRefreshToken(
                    request,
                    null,
                    refreshTokenValue,
                    decodedRefreshToken,
                    originalResources,
                    supportRefresh
            ).withScopes(resolvedScopes));
        }

        // Build JWT from decoded refresh token to load user
        JWT jwt = new JWT(decodedRefreshToken);

        return loadUser(jwt, request, subject)
                .map(user -> TokenCreationRequest.forRefreshToken(
                        request,
                        user,
                        refreshTokenValue,
                        decodedRefreshToken,
                        originalResources,
                        supportRefresh
                ).withScopes(resolvedScopes))
                .toSingle();
    }

    private Set<String> resolveScopes(Set<String> requestedScopes, String originalScopeString) {
        // The requested scope MUST NOT include any scope not originally granted by the resource owner,
        // and if omitted is treated as equal to the scope originally granted
        Set<String> originalScopes = originalScopeString != null
                ? new HashSet<>(Arrays.asList(originalScopeString.split("\\s+")))
                : null;

        if (requestedScopes == null || requestedScopes.isEmpty()) {
            return originalScopes;
        }

        if (originalScopes != null && !originalScopes.isEmpty()) {
            return requestedScopes.stream()
                    .filter(originalScopes::contains)
                    .collect(Collectors.toSet());
        }

        return requestedScopes;
    }

    private Maybe<User> loadUser(JWT jwt, TokenRequest request, String subject) {
        return userAuthenticationManager.loadPreAuthenticatedUserBySub(jwt, request)
                .onErrorResumeNext(ex -> Maybe.error(
                        new InvalidGrantException(isBlank(ex.getMessage()) ? "unable to read user profile" : ex.getMessage())));
    }
}
