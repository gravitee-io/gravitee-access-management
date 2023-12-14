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
import static io.gravitee.am.common.audit.EventType.TOKEN_ACTIVATED;
import static io.gravitee.am.common.audit.EventType.TOKEN_REVOKED;
import static io.gravitee.am.common.audit.Status.FAILURE;
import static io.gravitee.am.common.audit.Status.SUCCESS;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ClientTokenAuditBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildDefault() {
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).build(objectMapper);
        assertEquals(TOKEN_ACTIVATED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
    }
    @Test
    void shouldBuildDefaultWhenNulls() {
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .token(null, null)
                .throwable(null)
                .tokenTarget((Client) null)
                .tokenTarget((User) null)
                .build(objectMapper);
        assertEquals(TOKEN_ACTIVATED, audit.getType());
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
    void tokenShouldBuildNoToken() {
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).token(TokenTypeHint.REFRESH_TOKEN, null).build(objectMapper);
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals(TOKEN_ACTIVATED, audit.getType());
        assertNull(audit.getOutcome().getMessage());
    }

    @Test
    void shouldBuildToken() {
        var tokenId = "token-id";
        var tokenType = TokenTypeHint.REFRESH_TOKEN;
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).token(tokenType, tokenId).build(objectMapper);
        assertEquals("[{\"op\":\"add\",\"path\":\"/token-id\",\"value\":{\"token_id\":\"" + tokenId + "\",\"token_type\":\"" + tokenType + "\"}}]", audit.getOutcome().getMessage());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals(TOKEN_ACTIVATED, audit.getType());
    }

    @Test
    void shouldBuildAccessAndRefreshToken() {
        var accessTokenId = "token-id-access";
        var accessTokenType = TokenTypeHint.ACCESS_TOKEN;
        var refreshTokenId = "token-id";
        var refreshTokenType = TokenTypeHint.REFRESH_TOKEN;
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .token(accessTokenType, accessTokenId)
                .token(refreshTokenType, refreshTokenId)
                .build(objectMapper);
        assertTrue(audit.getOutcome().getMessage().contains(accessTokenId));
        assertTrue(audit.getOutcome().getMessage().contains(accessTokenType.name()));
        assertTrue(audit.getOutcome().getMessage().contains(refreshTokenId));
        assertTrue(audit.getOutcome().getMessage().contains(refreshTokenType.name()));
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals(TOKEN_ACTIVATED, audit.getType());
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

        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).tokenTarget(user).build(objectMapper);

        assertEquals(domainId, audit.getReferenceId());
        assertEquals(domainId, audit.getTarget().getReferenceId());
        assertEquals(referenceType, audit.getTarget().getReferenceType());
        assertEquals(userId, audit.getTarget().getId());
        assertEquals(username, audit.getTarget().getAlternativeId());
        assertEquals(userDisplayName, audit.getTarget().getDisplayName());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals("[{\"op\":\"add\",\"path\":\"/" + userId + "\",\"value\":{\"user_id\":\"" + userId + "\",\"token_type\":\"[ACCESS_TOKEN, REFRESH_TOKEN, ID_TOKEN]\"}}]", audit.getOutcome().getMessage());
        assertEquals(TOKEN_ACTIVATED, audit.getType());
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

        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).tokenTarget(client).build(objectMapper);

        assertEquals(domainId, audit.getReferenceId());
        assertEquals(applicationId, audit.getAccessPoint().getId());
        assertEquals(clientId, audit.getAccessPoint().getAlternativeId());
        assertEquals(clientName, audit.getAccessPoint().getDisplayName());
        assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
        assertEquals(applicationId, audit.getTarget().getId());
        assertEquals(clientName, audit.getTarget().getDisplayName());
        assertEquals(clientName, audit.getTarget().getAlternativeId());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals(TOKEN_ACTIVATED, audit.getType());
    }

    @Test
    void shouldBuildError() {
        var message = "message-error-1";
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class).throwable(new Exception(message)).build(objectMapper);
        assertEquals(FAILURE, audit.getOutcome().getStatus());
        assertEquals(message, audit.getOutcome().getMessage());
        assertEquals(TOKEN_ACTIVATED, audit.getType());
    }
}
