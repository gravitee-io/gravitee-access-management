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
package io.gravitee.am.repository.jdbc.oauth2.api.model;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Table("access_tokens")
public class JdbcAccessToken extends JdbcToken {
    @Column("refresh_token")
    private String refreshToken;
    @Column("authorization_code")
    private String authorizationCode;

    /**
     * RFC 8693 Token Exchange - Actor claim for delegation scenarios (stored as JSON).
     */
    @Column("actor")
    private String actor;

    /**
     * RFC 8693 Token Exchange - The type of the source token used in the exchange.
     */
    @Column("source_token_type")
    private String sourceTokenType;

    /**
     * RFC 8693 Token Exchange - The ID of the source token used in the exchange.
     */
    @Column("source_token_id")
    private String sourceTokenId;

    /**
     * RFC 8693 Token Exchange - The issued token type URI.
     */
    @Column("issued_token_type")
    private String issuedTokenType;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getSourceTokenType() {
        return sourceTokenType;
    }

    public void setSourceTokenType(String sourceTokenType) {
        this.sourceTokenType = sourceTokenType;
    }

    public String getSourceTokenId() {
        return sourceTokenId;
    }

    public void setSourceTokenId(String sourceTokenId) {
        this.sourceTokenId = sourceTokenId;
    }

    public String getIssuedTokenType() {
        return issuedTokenType;
    }

    public void setIssuedTokenType(String issuedTokenType) {
        this.issuedTokenType = issuedTokenType;
    }
}
