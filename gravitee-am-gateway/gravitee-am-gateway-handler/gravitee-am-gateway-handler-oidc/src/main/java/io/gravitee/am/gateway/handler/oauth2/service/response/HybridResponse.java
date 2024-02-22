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
import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.vertx.rxjava3.core.MultiMap;

import java.net.URISyntaxException;

/**
 * When using the Hybrid Flow, Authentication Responses are made in the same manner as for the Implicit Flow, as defined in Section 3.2.2.5, with the exception of the differences specified in this section.
 * These Authorization Endpoint results are used in the following manner:
 *
 * - access_token
 *      OAuth 2.0 Access Token.
 *      This is returned when the response_type value used is code token, or code id_token token.
 *      (A token_type value is also returned in the same cases.)
 *
 * - id_token
 *      ID Token.
 *      This is returned when the response_type value used is code id_token or code id_token token.
 *
 * - code
 *      Authorization Code.
 *      This is always returned when using the Hybrid Flow.
 *
 * See <a href="https://openid.net/specs/openid-connect-core-1_0.html#HybridAuthResponse">3.3.2.5. Successful Authentication Response</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HybridResponse extends ImplicitResponse {

    private String idToken;

    private String code;

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public String buildRedirectUri() {
        UriBuilder uriBuilder = UriBuilder.fromURIString(getRedirectUri());
        params().forEach(uriBuilder::addFragmentParameter);
        return uriBuilder.buildString();
    }


    @Override
    public MultiMap params(boolean encodeState) {
        MultiMap result = MultiMap.caseInsensitiveMultiMap();
        result.add(Parameters.CODE, getCode());
        if (getState() != null) {
            result.add(Parameters.STATE, encodeState ? getURLEncodedState() : getState());
        }
        if (getIdToken() != null) {
            result.add(ResponseType.ID_TOKEN, getIdToken());
        } else {
            Token accessToken = getAccessToken();
            result.add(Token.ACCESS_TOKEN, accessToken.getValue());
            result.add(Token.TOKEN_TYPE, accessToken.getTokenType());
            result.add(Token.EXPIRES_IN, String.valueOf(accessToken.getExpiresIn()));
            if (accessToken.getScope() != null && !accessToken.getScope().isEmpty()) {
                result.add(Token.SCOPE, accessToken.getScope());
            }
            // additional information
            if (accessToken.getAdditionalInformation() != null) {
                accessToken.getAdditionalInformation().forEach((k, v) -> result.add(k, String.valueOf(v)));
            }
        }
        return result;
    }
}
