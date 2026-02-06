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

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ActorTokenInfo;
import io.gravitee.am.model.User;
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.common.util.MultiValueMap;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable request containing all data needed for token creation.
 * This replaces the mutable OAuth2Request for the token creation flow.
 *
 * @param clientId the OAuth2 client ID
 * @param grantType the OAuth2 grant type
 * @param scopes the approved scopes for the token
 * @param resourceOwner the resource owner (user), null for client_credentials
 * @param grantData grant-specific data (sealed type for type safety)
 * @param supportRefreshToken whether to generate a refresh token
 * @param resources RFC 8707 resource indicators
 * @param originalAuthorizationResources original resources approved during authorization
 * @param httpInfo HTTP request metadata for audit/logging
 * @param additionalParameters additional request parameters
 * @param context OAuth2 contextual data for policies
 * @param executionContext mutable context for policy execution
 *
 * @author GraviteeSource Team
 */
public record TokenCreationRequest(
        String clientId,
        String grantType,
        Set<String> scopes,
        User resourceOwner,
        GrantData grantData,
        boolean supportRefreshToken,
        Set<String> resources,
        Set<String> originalAuthorizationResources,
        HttpRequestInfo httpInfo,
        MultiValueMap<String, String> additionalParameters,
        Map<String, Object> context,
        Map<String, Object> executionContext
) {

    /**
     * Create a new request with updated scopes (after scope resolution).
     */
    public TokenCreationRequest withScopes(Set<String> newScopes) {
        return new TokenCreationRequest(
                clientId, grantType, newScopes, resourceOwner, grantData,
                supportRefreshToken, resources, originalAuthorizationResources, httpInfo,
                additionalParameters, context, executionContext
        );
    }

    /**
     * Create a new request with updated execution context.
     */
    public TokenCreationRequest withExecutionContext(Map<String, Object> newContext) {
        return new TokenCreationRequest(
                clientId, grantType, scopes, resourceOwner, grantData,
                supportRefreshToken, resources, originalAuthorizationResources, httpInfo,
                additionalParameters, context, newContext
        );
    }

    /**
     * Check if this is a client-only request (no user).
     */
    public boolean isClientOnly() {
        return resourceOwner == null;
    }

    // ==================== Factory Methods ====================

    /**
     * Create request for Token Exchange (RFC 8693).
     *
     * @param original the original token request
     * @param user the user representing the subject
     * @param issuedTokenType the type of token being issued
     * @param expiration the expiration constraint from subject token
     * @param subjectTokenId the ID of the subject token
     * @param subjectTokenType the type of the subject token
     * @param actorTokenId the ID of the actor token (only for delegation)
     * @param actorTokenType the type of the actor token (only for delegation)
     * @param actorInfo actor information for delegation (null for impersonation)
     */
    public static TokenCreationRequest forTokenExchange(
            TokenRequest original,
            User user,
            String issuedTokenType,
            Date expiration,
            String subjectTokenId,
            String subjectTokenType,
            String actorTokenId,
            String actorTokenType,
            ActorTokenInfo actorInfo) {

        return new TokenCreationRequest(
                original.getClientId(),
                GrantType.TOKEN_EXCHANGE,
                original.getScopes(),
                user,
                new GrantData.TokenExchangeData(issuedTokenType, expiration, subjectTokenId, subjectTokenType, actorTokenId, actorTokenType, actorInfo),
                false, // token exchange doesn't support refresh
                original.getResources(),
                original.getOriginalAuthorizationResources(),
                HttpRequestInfo.from(original),
                original.getAdditionalParameters(),
                original.getContext(),
                Map.of()
        );
    }

    /**
     * Create request for Authorization Code grant.
     */
    public static TokenCreationRequest forAuthorizationCode(
            TokenRequest original,
            User user,
            String code,
            String codeVerifier,
            String redirectUri,
            Map<String, Object> authorizationCode,
            boolean supportRefresh) {

        return new TokenCreationRequest(
                original.getClientId(),
                GrantType.AUTHORIZATION_CODE,
                original.getScopes(),
                user,
                new GrantData.AuthorizationCodeData(code, codeVerifier, redirectUri, authorizationCode),
                supportRefresh,
                original.getResources(),
                original.getOriginalAuthorizationResources(),
                HttpRequestInfo.from(original),
                original.getAdditionalParameters(),
                original.getContext(),
                Map.of()
        );
    }

    /**
     * Create request for Client Credentials grant.
     */
    public static TokenCreationRequest forClientCredentials(
            TokenRequest original,
            boolean supportRefresh) {

        return new TokenCreationRequest(
                original.getClientId(),
                GrantType.CLIENT_CREDENTIALS,
                original.getScopes(),
                null, // no user for client credentials
                new GrantData.ClientCredentialsData(),
                supportRefresh,
                original.getResources(),
                original.getOriginalAuthorizationResources(),
                HttpRequestInfo.from(original),
                original.getAdditionalParameters(),
                original.getContext(),
                Map.of()
        );
    }

    /**
     * Create request for Refresh Token grant.
     */
    public static TokenCreationRequest forRefreshToken(
            TokenRequest original,
            User user,
            String refreshToken,
            Map<String, Object> decodedRefreshToken,
            Set<String> originalResources,
            boolean supportRefresh) {

        return new TokenCreationRequest(
                original.getClientId(),
                GrantType.REFRESH_TOKEN,
                original.getScopes(),
                user,
                new GrantData.RefreshTokenData(refreshToken, decodedRefreshToken, originalResources),
                supportRefresh,
                original.getResources(),
                originalResources,
                HttpRequestInfo.from(original),
                original.getAdditionalParameters(),
                original.getContext(),
                Map.of()
        );
    }

    /**
     * Create request for Resource Owner Password Credentials grant.
     */
    public static TokenCreationRequest forPassword(
            TokenRequest original,
            User user,
            String username,
            boolean supportRefresh) {

        return new TokenCreationRequest(
                original.getClientId(),
                GrantType.PASSWORD,
                original.getScopes(),
                user,
                new GrantData.PasswordData(username),
                supportRefresh,
                original.getResources(),
                original.getOriginalAuthorizationResources(),
                HttpRequestInfo.from(original),
                original.getAdditionalParameters(),
                original.getContext(),
                Map.of()
        );
    }

    /**
     * Create request for CIBA grant.
     */
    public static TokenCreationRequest forCiba(
            TokenRequest original,
            User user,
            String authReqId,
            List<String> acrValues,
            boolean supportRefresh) {

        return new TokenCreationRequest(
                original.getClientId(),
                GrantType.CIBA_GRANT_TYPE,
                original.getScopes(),
                user,
                new GrantData.CibaData(authReqId, acrValues),
                supportRefresh,
                original.getResources(),
                original.getOriginalAuthorizationResources(),
                HttpRequestInfo.from(original),
                original.getAdditionalParameters(),
                original.getContext(),
                Map.of()
        );
    }

    /**
     * Create request for UMA grant.
     */
    public static TokenCreationRequest forUma(
            TokenRequest original,
            User user,
            String ticket,
            List<PermissionRequest> permissions,
            boolean upgraded,
            boolean supportRefresh) {

        return new TokenCreationRequest(
                original.getClientId(),
                GrantType.UMA,
                original.getScopes(),
                user,
                new GrantData.UmaData(ticket, permissions, upgraded),
                supportRefresh,
                original.getResources(),
                original.getOriginalAuthorizationResources(),
                HttpRequestInfo.from(original),
                original.getAdditionalParameters(),
                original.getContext(),
                Map.of()
        );
    }

    /**
     * Create request for Extension Grant.
     */
    public static TokenCreationRequest forExtensionGrant(
            TokenRequest original,
            User user,
            String extensionGrantId,
            String extensionGrantType,
            Map<String, Object> additionalClaims,
            String userSource,
            boolean supportRefresh) {

        return new TokenCreationRequest(
                original.getClientId(),
                extensionGrantType,
                original.getScopes(),
                user,
                new GrantData.ExtensionGrantData(extensionGrantId, extensionGrantType, additionalClaims, userSource),
                supportRefresh,
                original.getResources(),
                original.getOriginalAuthorizationResources(),
                HttpRequestInfo.from(original),
                original.getAdditionalParameters(),
                original.getContext(),
                Map.of()
        );
    }
}
