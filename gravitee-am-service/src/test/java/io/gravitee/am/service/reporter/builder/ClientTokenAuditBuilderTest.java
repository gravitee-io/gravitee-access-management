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
package io.gravitee.am.service.reporter.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import org.junit.jupiter.api.Test;

import static io.gravitee.am.common.audit.EventType.TOKEN_CREATED;
import static io.gravitee.am.common.audit.EventType.TOKEN_REVOKED;
import static io.gravitee.am.common.audit.Status.FAILURE;
import static io.gravitee.am.common.audit.Status.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientTokenAuditBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildDefault() {
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).build(objectMapper);
        assertEquals(TOKEN_CREATED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
    }
    @Test
    void shouldBuildDefaultWhenNulls() {
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .throwable(null)
                .tokenActor((Client) null)
                .tokenTarget(null)
                .build(objectMapper);
        assertEquals(TOKEN_CREATED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertNull(audit.getOutcome().getMessage());
    }

    @Test
    void shouldBuildRevoked() {
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).revoked().build(objectMapper);
        assertEquals(TOKEN_REVOKED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
    }
    @Test
    void shouldBuildRevokedWithReason() {
        var reason = "revoked-reason";
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).revoked(reason).build(objectMapper);
        assertEquals(TOKEN_REVOKED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertTrue(audit.getOutcome().getMessage().contains(reason));
    }

    @Test
    void tokenShouldBuildNoTokenId() {
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).refreshToken((String) null).build(objectMapper);
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals(TOKEN_CREATED, audit.getType());
        assertNull(audit.getOutcome().getMessage());
    }

    @Test
    void tokenShouldBuildNoUser() {
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).idTokenFor((User) null).build(objectMapper);
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals(TOKEN_CREATED, audit.getType());
        assertNull(audit.getOutcome().getMessage());
    }

    @Test
    void tokenShouldBuildActorUser() {
        var userId = "user-id-1";
        var referenceId = "reference-id-1";
        var user = new User();
        user.setId(userId);
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(referenceId);
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).tokenActor(user).build(objectMapper);
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals(TOKEN_CREATED, audit.getType());
        assertEquals(userId, audit.getActor().getId());
        assertEquals(referenceId, audit.getReferenceId());
        assertNull(audit.getOutcome().getMessage());
    }

    @Test
    void shouldBuildToken() {
        var tokenId = "token-id";
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).refreshToken(tokenId).build(objectMapper);
        assertEquals("[{\"op\":\"add\",\"path\":\"/"+TokenTypeHint.REFRESH_TOKEN.name()+"\",\"value\":\""+tokenId+"\"}]", audit.getOutcome().getMessage());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals(TOKEN_CREATED, audit.getType());
    }

    @Test
    void shouldBuildIdToken() {
        var user = new User();
        user.setId("user-id-1");
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).idTokenFor(user).build(objectMapper);
        assertEquals("[{\"op\":\"add\",\"path\":\"/"+TokenTypeHint.ID_TOKEN.name()+"\",\"value\":\"Delivered for sub 'user-id-1'\"}]", audit.getOutcome().getMessage());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals(TOKEN_CREATED, audit.getType());
    }

    @Test
    void shouldBuildAccessAndRefreshToken() {
        var accessTokenId = "token-id-access";
        var accessTokenType = TokenTypeHint.ACCESS_TOKEN;
        var refreshTokenId = "token-id";
        var refreshTokenType = TokenTypeHint.REFRESH_TOKEN;
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .accessToken(accessTokenId)
                .refreshToken(refreshTokenId)
                .build(objectMapper);
        assertTrue(audit.getOutcome().getMessage().contains(accessTokenId));
        assertTrue(audit.getOutcome().getMessage().contains(accessTokenType.name()));
        assertTrue(audit.getOutcome().getMessage().contains(refreshTokenId));
        assertTrue(audit.getOutcome().getMessage().contains(refreshTokenType.name()));
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals(TOKEN_CREATED, audit.getType());
    }

    @Test
    void shouldBuildTokenTargetUser() {
        var userId = "user-id-1";
        var username = "user-name-1";
        var userDisplayName = "user-display-name-1";
        var domainId = "domainId";
        var referenceType = ReferenceType.DOMAIN;
        var user = new User();
        user.setId(userId);
        user.setUsername(username);
        user.setDisplayName(userDisplayName);
        user.setReferenceId(domainId);
        user.setReferenceType(referenceType);

        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .tokenTarget(user)
                .accessTokenSubject("accessTokenSubjectValue")
                .build(objectMapper);

        assertEquals(domainId, audit.getReferenceId());
        assertEquals(domainId, audit.getTarget().getReferenceId());
        assertEquals(referenceType, audit.getTarget().getReferenceType());
        assertEquals(userId, audit.getTarget().getId());
        assertEquals(username, audit.getTarget().getAlternativeId());
        assertEquals(userDisplayName, audit.getTarget().getDisplayName());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals(TOKEN_CREATED, audit.getType());
        assertEquals("accessTokenSubjectValue", audit.getTarget().getAttributes().get("accessTokenSubject"));
    }


    @Test
    void shouldBuildTokenTargetClient() {
        var applicationId = "client-applicationId";
        var clientId = "client-id";
        var clientName = "client-name";
        var domainId = "domainId";
        var client = new Client();
        client.setId(applicationId);
        client.setClientId(clientId);
        client.setClientName(clientName);
        client.setDomain(domainId);

        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).tokenActor(client).build(objectMapper);

        assertEquals(domainId, audit.getReferenceId());
        assertEquals(applicationId, audit.getAccessPoint().getId());
        assertEquals(clientId, audit.getAccessPoint().getAlternativeId());
        assertEquals(clientName, audit.getAccessPoint().getDisplayName());
        assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
        assertEquals(applicationId, audit.getActor().getId());
        assertEquals(clientName, audit.getActor().getDisplayName());
        assertEquals(clientName, audit.getActor().getAlternativeId());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals(TOKEN_CREATED, audit.getType());
    }

    @Test
    void shouldBuildError() {
        var message = "message-error-1";
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).throwable(new Exception(message)).build(objectMapper);
        assertEquals(FAILURE, audit.getOutcome().getStatus());
        assertEquals(message, audit.getOutcome().getMessage());
        assertEquals(TOKEN_CREATED, audit.getType());
    }

    @Test
    void shouldBuildWithResourceParameter() {
        var resources = "https://mcp.example.com/api/v1 https://mcp2.example.com/api/v1";
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .withParams(() -> {
                    var params = new java.util.HashMap<String, Object>();
                    params.put("resource", resources);
                    return params;
                })
                .build(objectMapper);
        
        assertTrue(audit.getOutcome().getMessage().contains("resource"));
        assertTrue(audit.getOutcome().getMessage().contains(resources));
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals(TOKEN_CREATED, audit.getType());
    }
}
