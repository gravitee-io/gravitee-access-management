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

import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ActorTokenInfo;
import io.gravitee.am.model.uma.PermissionRequest;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sealed interface for grant-specific data.
 * Each grant type has its own record with the data it needs.
 * Using sealed types provides type safety and enables pattern matching.
 *
 * @author GraviteeSource Team
 */
public sealed interface GrantData permits
        GrantData.TokenExchangeData,
        GrantData.AuthorizationCodeData,
        GrantData.ClientCredentialsData,
        GrantData.RefreshTokenData,
        GrantData.PasswordData,
        GrantData.CibaData,
        GrantData.UmaData,
        GrantData.ExtensionGrantData {

    /**
     * Token Exchange (RFC 8693) specific data.
     *
     * @param issuedTokenType the type of token being issued
     * @param expiration the expiration time constraint from subject token
     * @param subjectTokenId the ID of the subject token
     * @param subjectTokenType the type of the subject token
     * @param actorTokenId the ID of the actor token (only for delegation)
     * @param actorTokenType the type of the actor token (only for delegation)
     * @param actorInfo actor information for delegation scenarios (null for impersonation)
     */
    record TokenExchangeData(
            String issuedTokenType,
            Date expiration,
            String subjectTokenId,
            String subjectTokenType,
            String actorTokenId,
            String actorTokenType,
            ActorTokenInfo actorInfo
    ) implements GrantData {
        public boolean isDelegation() {
            return actorInfo != null;
        }
    }

    /**
     * Authorization Code grant specific data.
     */
    record AuthorizationCodeData(
            String code,
            String codeVerifier,
            String redirectUri,
            Map<String, Object> authorizationCode
    ) implements GrantData {}

    /**
     * Client Credentials grant specific data.
     */
    record ClientCredentialsData() implements GrantData {}

    /**
     * Refresh Token grant specific data.
     */
    record RefreshTokenData(
            String refreshToken,
            Map<String, Object> decodedRefreshToken,
            Set<String> originalResources
    ) implements GrantData {}

    /**
     * Resource Owner Password Credentials grant specific data.
     */
    record PasswordData(
            String username
    ) implements GrantData {}

    /**
     * CIBA (Client Initiated Backchannel Authentication) grant specific data.
     */
    record CibaData(
            String authReqId,
            List<String> acrValues
    ) implements GrantData {}

    /**
     * UMA (User Managed Access) grant specific data.
     */
    record UmaData(
            String ticket,
            List<PermissionRequest> permissions,
            boolean upgraded
    ) implements GrantData {}

    /**
     * Extension Grant specific data for custom grant types.
     */
    record ExtensionGrantData(
            String extensionGrantId,
            String extensionGrantType,
            Map<String, Object> additionalClaims,
            String userSource
    ) implements GrantData {}
}
