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
package io.gravitee.am.gateway.handler.oauth2.exception;

import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oidc.ResponseMode;
import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.common.web.UriBuilder;

import java.net.URISyntaxException;

/**
 * See <a href="https://bitbucket.org/openid/fapi/src/master/Financial_API_JWT_Secured_Authorization_Response_Mode.md#markdown-header-41-the-jwt-response-document">
 *     Error responses</a>
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JWTOAuth2Exception extends OAuth2Exception {

    private final OAuth2Exception exception;

    private String state;

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

    public JWTOAuth2Exception(final OAuth2Exception exception, final String state) {
        this.exception = exception;
        this.state = state;
    }

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

    public JWT build() {
        JWT jwt = new JWT();

        jwt.put("error", exception.getOAuth2ErrorCode());
        jwt.put("error_description", exception.getMessage());

        jwt.setAud(this.aud);
        jwt.setIss(this.iss);
        jwt.setExp(this.exp);

        if (this.state != null && !this.state.isEmpty()) {
            jwt.put(Parameters.STATE, this.state);
        }

        return jwt;
    }

    public String buildRedirectUri(final String redirectUri, final String responseType, final String responseMode, final String jwt) throws URISyntaxException {
        UriBuilder uriBuilder = UriBuilder.fromURIString(redirectUri);

        if (ResponseMode.QUERY_JWT.equalsIgnoreCase(responseMode)) {
            uriBuilder.addParameter(io.gravitee.am.common.oidc.Parameters.RESPONSE, jwt);
        } else if (ResponseMode.FRAGMENT_JWT.equalsIgnoreCase(responseMode)) {
            uriBuilder.addFragmentParameter(io.gravitee.am.common.oidc.Parameters.RESPONSE,jwt);
        } else if (ResponseMode.JWT.equalsIgnoreCase(responseMode)) {
            if (responseType == null || ResponseType.NONE.equalsIgnoreCase(responseType)) {
                // Nothing to do here
            } else if (io.gravitee.am.common.oauth2.ResponseType.CODE.equalsIgnoreCase(responseType)) {
                uriBuilder.addParameter(io.gravitee.am.common.oidc.Parameters.RESPONSE, jwt);
            } else {
                uriBuilder.addFragmentParameter(io.gravitee.am.common.oidc.Parameters.RESPONSE,jwt);
            }
        }

        return uriBuilder.build().toString();
    }

    @Override
    public int getHttpStatusCode() {
        return exception.getHttpStatusCode();
    }

    @Override
    public String getOAuth2ErrorCode() {
        return exception.getOAuth2ErrorCode();
    }

    @Override
    public String toString() {
        return exception.toString();
    }
}
