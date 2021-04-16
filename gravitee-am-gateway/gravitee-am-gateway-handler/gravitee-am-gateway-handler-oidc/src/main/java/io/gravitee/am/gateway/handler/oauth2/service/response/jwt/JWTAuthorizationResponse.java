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
package io.gravitee.am.gateway.handler.oauth2.service.response.jwt;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oidc.ResponseMode;
import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.oauth2.service.response.AuthorizationCodeResponse;
import io.gravitee.am.gateway.handler.oauth2.service.response.AuthorizationResponse;
import io.gravitee.am.gateway.handler.oauth2.service.response.ImplicitResponse;
import java.net.URISyntaxException;

/**
 * Response after authorization code flow or implicit flow or hybrid flow in JWT format.
 * See <a href="https://openid.net//specs/openid-financial-api-jarm.html#the-jwt-response-document">4.1.  The JWT Response Document</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class JWTAuthorizationResponse<T extends AuthorizationResponse> extends AuthorizationResponse {

    private final JWT jwt = new JWT();

    protected final T response;

    public JWTAuthorizationResponse(T response) {
        this.response = response;
    }

    /**
     * The issuer URL of the authorization server that created the response.
     */
    private String iss;

    /**
     * The client_id of the client the response is intended for.
     */
    private String aud;

    /**
     * Expiration of the JWT.
     */
    private long exp;

    private String responseType;

    private String responseMode;

    private String token;

    public String getIss() {
        return iss;
    }

    public void setIss(String iss) {
        this.iss = iss;
    }

    public String getAud() {
        return aud;
    }

    public void setAud(String aud) {
        this.aud = aud;
    }

    public long getExp() {
        return exp;
    }

    public void setExp(long exp) {
        this.exp = exp;
    }

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public String getResponseMode() {
        return responseMode;
    }

    public void setResponseMode(String responseMode) {
        this.responseMode = responseMode;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public static JWTAuthorizationResponse from(AuthorizationResponse response) {
        if (response == null) {
            return null;
        }

        if (response instanceof AuthorizationCodeResponse) {
            return new JWTAuthorizationCodeResponse((AuthorizationCodeResponse) response);
        } else if (response instanceof ImplicitResponse) {
            return new JWTImplicitResponse((ImplicitResponse) response);
        }

        return null;
    }

    public JWT build() {
        jwt.setAud(this.aud);
        jwt.setIss(this.iss);
        jwt.setExp(this.exp);

        if (response.getState() != null && !response.getState().isEmpty()) {
            jwt.put(Parameters.STATE, response.getState());
        }

        return jwt;
    }

    @Override
    public String buildRedirectUri() throws URISyntaxException {
        UriBuilder uriBuilder = UriBuilder.fromURIString(response.getRedirectUri());

        if (ResponseMode.QUERY_JWT.equalsIgnoreCase(responseMode)) {
            uriBuilder.addParameter(io.gravitee.am.common.oidc.Parameters.RESPONSE, token);
        } else if (ResponseMode.FRAGMENT_JWT.equalsIgnoreCase(responseMode)) {
            uriBuilder.addFragmentParameter(io.gravitee.am.common.oidc.Parameters.RESPONSE, token);
        } else if (ResponseMode.JWT.equalsIgnoreCase(responseMode)) {
            if (responseType == null || ResponseType.NONE.equalsIgnoreCase(responseType)) {
                // Nothing to do here
            } else if (io.gravitee.am.common.oauth2.ResponseType.CODE.equalsIgnoreCase(responseType)) {
                uriBuilder.addParameter(io.gravitee.am.common.oidc.Parameters.RESPONSE, token);
            } else {
                uriBuilder.addFragmentParameter(io.gravitee.am.common.oidc.Parameters.RESPONSE, token);
            }
        }

        return uriBuilder.build().toString();
    }
}
