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
package io.gravitee.am.gateway.handler.oauth2.service.request;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import lombok.Getter;
import lombok.Setter;

/**
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.1.3">Access Token Request</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */

@Getter
@Setter
public class TokenRequest extends OAuth2Request {

    /**
     * The resource owner username.
     * REQUIRED for <a href="https://tools.ietf.org/html/rfc6749#section-4.3">Resource Owner Password Credentials Grant</a>
     */
    private String username;

    /**
     * The resource owner password.
     * REQUIRED for <a href="https://tools.ietf.org/html/rfc6749#section-4.3">Resource Owner Password Credentials Grant</a>
     */
    private String password;

    /**
     * REQUIRED for <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#uma-grant-type">User Managed Access Grant</a>
     */
    private String ticket;

    /**
     * REQUIRED for <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#uma-grant-type">User Managed Access Grant</a>
     */
    private String claimToken;

    /**
     * REQUIRED for <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#uma-grant-type">User Managed Access Grant</a>
     */
    private String claimTokenFormat;

    /**
     * REQUIRED for <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#uma-grant-type">User Managed Access Grant</a>
     */
    private String persistedClaimsToken;

    /**
     * REQUIRED for <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-grant-2.0.html#uma-grant-type">User Managed Access Grant</a>
     */
    private String requestingPartyToken;

    public OAuth2Request createOAuth2Request() {
        MultiValueMap<String, String> requestParameters = parameters();
        MultiValueMap<String, String> safeRequestParameters = new LinkedMultiValueMap(requestParameters);

        // Remove password if present to prevent leaks
        safeRequestParameters.remove(Parameters.PASSWORD);
        safeRequestParameters.remove(Parameters.CLIENT_SECRET);

        OAuth2Request oAuth2Request = new OAuth2Request();
        // set technical information
        oAuth2Request.setId(id());
        oAuth2Request.setTransactionId(transactionId());
        oAuth2Request.setTimestamp(timestamp());
        oAuth2Request.setPath(path());
        oAuth2Request.setOrigin(getOrigin());
        oAuth2Request.setUri(uri());
        oAuth2Request.setScheme(scheme());
        oAuth2Request.setContextPath(contextPath());
        oAuth2Request.setMethod(method());
        oAuth2Request.setVersion(version());
        oAuth2Request.setHeaders(headers());
        oAuth2Request.setParameters(safeRequestParameters);
        oAuth2Request.setHttpResponse(getHttpResponse());
        oAuth2Request.setContext(getContext());

        // set OAuth 2.0 information
        oAuth2Request.setClientId(getClientId());
        oAuth2Request.setScopes(getScopes());
        oAuth2Request.setGrantType(getGrantType());
        oAuth2Request.setSubject(getSubject());
        oAuth2Request.setAdditionalParameters(getAdditionalParameters());
        oAuth2Request.setRefreshToken(getRefreshToken());
        oAuth2Request.setAuthorizationCode(getAuthorizationCode());
        oAuth2Request.setResources(getResources());
        // preserve original authorization resources for RFC 8707 compliance
        oAuth2Request.setOriginalAuthorizationResources(getOriginalAuthorizationResources());

        // set UMA 2.0 permissions
        oAuth2Request.setPermissions(getPermissions());

        // certificate bound access token
        oAuth2Request.setConfirmationMethodX5S256(getConfirmationMethodX5S256());

        return oAuth2Request;
    }
}

