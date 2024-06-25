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
package io.gravitee.am.gateway.handler.oauth2.service.response;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.vertx.rxjava3.core.MultiMap;
import lombok.Getter;

/**
 * If the resource owner grants the access request, the authorization server issues an access token and delivers it to the client by adding
 * the following parameters to the fragment component of the redirection URI.
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.2.2">4.2.2. Access Token Response</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
public class ImplicitResponse extends AuthorizationResponse {

    /**
     * Access Token Response
     *
     * Note : The authorization server MUST NOT issue a refresh token.
     */
    private Token accessToken;

    public void setAccessToken(Token accessToken) {
        if (accessToken.getRefreshToken() != null) {
            throw new ServerErrorException("Implicit flow must not issue a refresh token");
        }
        this.accessToken = accessToken;
    }

    @Override
    public String buildRedirectUri() {
        UriBuilder uriBuilder = UriBuilder.fromURIString(getRedirectUri());
        // Implicit type (token) requires fragment
        // https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html#Security
        params().forEach(uriBuilder::addFragmentParameter);
        return uriBuilder.buildString();
    }

    @Override
    public MultiMap params(boolean encodeState) {
        MultiMap result = MultiMap.caseInsensitiveMultiMap();
        Token accessToken = getAccessToken();
        result.add(Token.ACCESS_TOKEN, accessToken.getValue());
        result.add(Token.TOKEN_TYPE, accessToken.getTokenType());
        result.add(Token.EXPIRES_IN, String.valueOf(accessToken.getExpiresIn()));
        if (accessToken.getScope() != null && !accessToken.getScope().isEmpty()) {
            result.add(Token.SCOPE, accessToken.getScope());
        }
        if (getState() != null) {
            result.add(Parameters.STATE, encodeState ? getURLEncodedState() : getState());
        }
        // additional information
        if (accessToken.getAdditionalInformation() != null) {
            accessToken.getAdditionalInformation().forEach((k, v) -> result.add(k, String.valueOf(v)));
        }
        return result;
    }
}
