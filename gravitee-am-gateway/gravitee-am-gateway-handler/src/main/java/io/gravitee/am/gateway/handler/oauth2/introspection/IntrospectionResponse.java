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
package io.gravitee.am.gateway.handler.oauth2.introspection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The introspection response.
 *
 * See <a href="https://tools.ietf.org/html/rfc7662#section-2.2"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntrospectionResponse {

    @JsonProperty("active")
    private boolean active;

    @JsonProperty("scope")
    private String scope;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("exp")
    private Long expireAt;

    @JsonProperty("iat")
    private Long issueAt;

    @JsonProperty("nbf")
    private Long notToUseBefore;

    @JsonProperty("sub")
    private String subject;

    @JsonProperty("aud")
    private String audience;

    @JsonProperty("iss")
    private String issuer;

    @JsonProperty("jti")
    private String identifier;

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Long expireAt) {
        this.expireAt = expireAt;
    }

    public Long getIssueAt() {
        return issueAt;
    }

    public void setIssueAt(Long issueAt) {
        this.issueAt = issueAt;
    }

    public Long getNotToUseBefore() {
        return notToUseBefore;
    }

    public void setNotToUseBefore(Long notToUseBefore) {
        this.notToUseBefore = notToUseBefore;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
}
