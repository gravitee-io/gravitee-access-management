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
package io.gravitee.am.repository.mongodb.oauth2.internal.model;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.util.Date;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccessTokenMongo {

    @BsonId
    private String id;

    private String token;

    @BsonProperty("created_at")
    private Date createdAt;

    @BsonProperty("expire_at")
    private Date expireAt;

    private String domain;

    @BsonProperty("client")
    private String client;

    private String subject;

    @BsonProperty("authorization_code")
    private String authorizationCode;

    @BsonProperty("refresh_token")
    private String refreshToken;

    /**
     * RFC 8693 Token Exchange - Actor claim for delegation scenarios.
     */
    private Map<String, Object> actor;

    /**
     * RFC 8693 Token Exchange - The type of the source token used in the exchange.
     */
    @BsonProperty("source_token_type")
    private String sourceTokenType;

    /**
     * RFC 8693 Token Exchange - The ID of the source token used in the exchange.
     */
    @BsonProperty("source_token_id")
    private String sourceTokenId;

    /**
     * RFC 8693 Token Exchange - The issued token type URI.
     */
    @BsonProperty("issued_token_type")
    private String issuedTokenType;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getClient() {
        return client;
    }

    public void setClient(String client) {
        this.client = client;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
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

    public Map<String, Object> getActor() {
        return actor;
    }

    public void setActor(Map<String, Object> actor) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AccessTokenMongo that = (AccessTokenMongo) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
