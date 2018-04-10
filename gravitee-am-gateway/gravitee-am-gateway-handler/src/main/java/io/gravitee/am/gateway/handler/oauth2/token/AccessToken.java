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
package io.gravitee.am.gateway.handler.oauth2.token;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * See definition at <a href="https://tools.ietf.org/html/rfc6749#section-1.4"></a>
 *
 * If the access token request is valid and authorized, the authorization server issues an access token as described in
 * <a href="https://tools.ietf.org/html/rfc6749#section-5.1">Successful Response</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface AccessToken {

    String BEARER_TYPE = "Bearer";

    String OAUTH2_TYPE = "OAuth2";

    /**
     * REQUIRED. The access token issued by the authorization server.
     */
    String ACCESS_TOKEN = "access_token";

    /**
     * REQUIRED. The type of the token issued as described in
     *  <a href="https://tools.ietf.org/html/rfc6749#section-7.1">Section 7.1</a>. Value is case insensitive.
     */
    String TOKEN_TYPE = "token_type";

    /**
     * RECOMMENDED. The lifetime in seconds of the access token.
     * For example, the value "3600" denotes that the access token will expire in one hour from the time the response was generated.
     * If omitted, the authorization server SHOULD provide the expiration time via other means or document the default value.
     */
    String EXPIRES_IN = "expires_in";

    /**
     * OPTIONAL. The refresh token, which can be used to obtain new access tokens using the same authorization grant as described in
     * <a href="https://tools.ietf.org/html/rfc6749#section-6">Section 6</a>
     */
    String REFRESH_TOKEN = "refresh_token";

    /**
     * OPTIONAL. The scope of the access request as described by <a href="https://tools.ietf.org/html/rfc6749#section-3.3">Section 3.3</a>.
     * The requested scope MUST NOT include any scope not originally granted by the resource owner,
     * and if omitted is treated as equal to the scope originally granted by the resource owner.
     */
    String SCOPE = "scope";

    @JsonProperty(ACCESS_TOKEN)
    String getValue();

    @JsonProperty(TOKEN_TYPE)
    String getTokenType();

    @JsonProperty(EXPIRES_IN)
    int getExpiresIn();

    @JsonProperty(REFRESH_TOKEN)
    String getRefreshToken();

    @JsonProperty(SCOPE)
    String getScope();
}
