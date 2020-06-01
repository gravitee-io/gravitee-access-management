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
package io.gravitee.am.gateway.handler.oauth2.service.token;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * See definition at <a href="https://tools.ietf.org/html/rfc6749#section-1.4"></a>
 *
 * If the access token request is valid and authorized, the authorization server issues an access token as described in
 * <a href="https://tools.ietf.org/html/rfc6749#section-5.1">Successful Response</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class Token {

    private String value;
    private String tokenType = BEARER_TYPE.toLowerCase();
    private long expiresIn;
    private String scope;
    private String clientId;
    private String subject;
    private String refreshToken;
    private Map<String, Object> additionalInformation = new HashMap<>();
    private Date createdAt;
    private Date expireAt;
    private Boolean upgraded;

    public static final String BEARER_TYPE = "Bearer";

    /**
     * REQUIRED. The access token issued by the authorization server.
     */
    public static final String ACCESS_TOKEN = "access_token";

    /**
     * REQUIRED. The type of the token issued as described in
     *  <a href="https://tools.ietf.org/html/rfc6749#section-7.1">Section 7.1</a>. Value is case insensitive.
     */
    public static final String TOKEN_TYPE = "token_type";

    /**
     * RECOMMENDED. The lifetime in seconds of the access token.
     * For example, the value "3600" denotes that the access token will expire in one hour from the time the response was generated.
     * If omitted, the authorization server SHOULD provide the expiration time via other means or document the default value.
     */
    public static final String EXPIRES_IN = "expires_in";

    /**
     * OPTIONAL. The refresh token, which can be used to obtain new access tokens using the same authorization grant as described in
     * <a href="https://tools.ietf.org/html/rfc6749#section-6">Section 6</a>
     */
    public static final String REFRESH_TOKEN = "refresh_token";

    /**
     * OPTIONAL. The scope of the access request as described by <a href="https://tools.ietf.org/html/rfc6749#section-3.3">Section 3.3</a>.
     * The requested scope MUST NOT include any scope not originally granted by the resource owner,
     * and if omitted is treated as equal to the scope originally granted by the resource owner.
     */
    public static final String SCOPE = "scope";

    /**
     * UMA 2.0 is introduced the capability to upgrade a previous (Requesting Party) Token with new permissions
     */
    public static final String UPGRADED = "upgraded";

    public Token(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Map<String, Object> getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(Map<String, Object> additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Date expireAt) {
        this.expireAt = expireAt;
    }

    public Boolean isUpgraded() {
        return upgraded;
    }

    public void setUpgraded(Boolean upgraded) {
        this.upgraded = upgraded;
    }
}
