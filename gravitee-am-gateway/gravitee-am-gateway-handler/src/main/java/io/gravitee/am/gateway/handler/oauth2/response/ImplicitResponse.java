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
package io.gravitee.am.gateway.handler.oauth2.response;

import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.utils.UriBuilder;

import java.net.URISyntaxException;

/**
 * If the resource owner grants the access request, the authorization server issues an access token and delivers it to the client by adding
 * the following parameters to the fragment component of the redirection URI.
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.2.2">4.2.2. Access Token Response</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ImplicitResponse extends AuthorizationResponse {

    /**
     * Access Token Response
     *
     * Note : The authorization server MUST NOT issue a refresh token.
     */
    private AccessToken accessToken;

    public AccessToken getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(AccessToken accessToken) {
        if (accessToken.getRefreshToken() != null) {
            throw new ServerErrorException("Implicit flow must not issue a refresh token");
        }
        this.accessToken = accessToken;
    }

    @Override
    public String buildRedirectUri() throws URISyntaxException {
        AccessToken accessToken = getAccessToken();
        UriBuilder uriBuilder = UriBuilder.fromURIString(getRedirectUri());
        uriBuilder.addFragmentParameter(AccessToken.ACCESS_TOKEN, accessToken.getValue());
        uriBuilder.addFragmentParameter(AccessToken.TOKEN_TYPE, accessToken.getTokenType());
        uriBuilder.addFragmentParameter(AccessToken.EXPIRES_IN, String.valueOf(accessToken.getExpiresIn()));
        if (accessToken.getScope() != null && !accessToken.getScope().isEmpty()) {
            uriBuilder.addFragmentParameter(AccessToken.SCOPE, accessToken.getScope());
        }
        if (getState() != null) {
            uriBuilder.addFragmentParameter(OAuth2Constants.STATE, getState());
        }
        // additional information
        if (accessToken.getAdditionalInformation() != null) {
            accessToken.getAdditionalInformation().forEach((k, v) -> uriBuilder.addFragmentParameter(k, String.valueOf(v)));
        }
        return uriBuilder.build().toString();
    }
}
