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
package io.gravitee.am.extensiongrant.tokenexchange.delegation;

import io.gravitee.am.extensiongrant.tokenexchange.validation.ValidatedToken;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DelegationContext - RFC 8693 delegation/impersonation context.
 *
 * @author GraviteeSource Team
 */
class DelegationContextTest {

    @Test
    void shouldCreateDelegationContext() {
        // Given
        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .build();

        Map<String, Object> actorClaim = new HashMap<>();
        actorClaim.put("sub", "admin@example.com");

        // When
        DelegationContext context = DelegationContext.builder()
                .subjectToken(subjectToken)
                .actorToken(actorToken)
                .asDelegation(actorClaim, 1)
                .grantedScopes(Set.of("read", "write"))
                .audience("https://api.example.com")
                .clientId("client-123")
                .build();

        // Then
        assertNotNull(context);
        assertEquals("user@example.com", context.getSubjectToken().getSubject());
        assertEquals("admin@example.com", context.getActorToken().getSubject());
        assertTrue(context.isDelegation());
        assertFalse(context.isImpersonation());
        assertEquals(1, context.getDelegationChainDepth());
        assertNotNull(context.getActorClaim());
        assertEquals("admin@example.com", context.getActorClaim().get("sub"));
        assertTrue(context.getGrantedScopes().contains("read"));
        assertEquals("https://api.example.com", context.getAudience());
    }

    @Test
    void shouldCreateDirectImpersonationContext() {
        // Given
        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .build();

        // When - no actor token for direct impersonation
        DelegationContext context = DelegationContext.builder()
                .subjectToken(subjectToken)
                .actorToken(null)
                .asDirectImpersonation()
                .clientId("admin-client")
                .build();

        // Then
        assertNotNull(context);
        assertTrue(context.isImpersonation());
        assertFalse(context.isDelegation());
        assertFalse(context.hasActorToken());
        assertEquals(DelegationContext.DelegationType.DIRECT_IMPERSONATION, context.getDelegationType());
        assertNull(context.getActorClaim());
        assertEquals(0, context.getDelegationChainDepth());
    }

    @Test
    void shouldCreateDelegatedImpersonationContext() {
        // Given
        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .build();

        // When - actor token present but impersonation mode
        DelegationContext context = DelegationContext.builder()
                .subjectToken(subjectToken)
                .actorToken(actorToken)
                .asDelegatedImpersonation()
                .clientId("admin-client")
                .build();

        // Then
        assertNotNull(context);
        assertTrue(context.isImpersonation());
        assertFalse(context.isDelegation());
        assertTrue(context.hasActorToken());
        assertEquals(DelegationContext.DelegationType.DELEGATED_IMPERSONATION, context.getDelegationType());
        assertNull(context.getActorClaim());
    }

    @Test
    void shouldCreateSimpleExchangeContext() {
        // Given
        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .build();

        // When - simple token exchange without delegation/impersonation
        DelegationContext context = DelegationContext.builder()
                .subjectToken(subjectToken)
                .asSimpleExchange()
                .clientId("client-123")
                .build();

        // Then
        assertNotNull(context);
        assertFalse(context.isDelegation());
        assertFalse(context.isImpersonation());
        assertEquals(DelegationContext.DelegationType.NONE, context.getDelegationType());
    }

    @Test
    void shouldReturnEffectiveSubject() {
        // Given
        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .build();

        // When
        DelegationContext context = DelegationContext.builder()
                .subjectToken(subjectToken)
                .asSimpleExchange()
                .build();

        // Then
        assertEquals("user@example.com", context.getEffectiveSubject());
    }

    @Test
    void shouldReturnActorSubjectWhenPresent() {
        // Given
        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .build();

        // When
        DelegationContext context = DelegationContext.builder()
                .subjectToken(subjectToken)
                .actorToken(actorToken)
                .asDelegation(new HashMap<>(), 1)
                .build();

        // Then
        assertEquals("admin@example.com", context.getActorSubject());
    }

    @Test
    void shouldReturnNullActorSubjectWhenAbsent() {
        // Given
        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .build();

        // When
        DelegationContext context = DelegationContext.builder()
                .subjectToken(subjectToken)
                .actorToken(null)
                .asDirectImpersonation()
                .build();

        // Then
        assertNull(context.getActorSubject());
    }

    @Test
    void shouldHandleAllDelegationTypes() {
        // Test all DelegationType enum values
        assertEquals(4, DelegationContext.DelegationType.values().length);

        // DELEGATION
        DelegationContext delegation = DelegationContext.builder()
                .subjectToken(createSubjectToken())
                .asDelegation(new HashMap<>(), 1)
                .build();
        assertTrue(delegation.isDelegation());
        assertFalse(delegation.isImpersonation());

        // DIRECT_IMPERSONATION
        DelegationContext directImp = DelegationContext.builder()
                .subjectToken(createSubjectToken())
                .asDirectImpersonation()
                .build();
        assertFalse(directImp.isDelegation());
        assertTrue(directImp.isImpersonation());

        // DELEGATED_IMPERSONATION
        DelegationContext delegatedImp = DelegationContext.builder()
                .subjectToken(createSubjectToken())
                .asDelegatedImpersonation()
                .build();
        assertFalse(delegatedImp.isDelegation());
        assertTrue(delegatedImp.isImpersonation());

        // NONE
        DelegationContext simple = DelegationContext.builder()
                .subjectToken(createSubjectToken())
                .asSimpleExchange()
                .build();
        assertFalse(simple.isDelegation());
        assertFalse(simple.isImpersonation());
    }

    @Test
    void shouldStoreAuditInfo() {
        // Given
        Map<String, Object> auditInfo = new HashMap<>();
        auditInfo.put("event_type", "TOKEN_EXCHANGE_DELEGATION");
        auditInfo.put("timestamp", System.currentTimeMillis());

        // When
        DelegationContext context = DelegationContext.builder()
                .subjectToken(createSubjectToken())
                .asSimpleExchange()
                .auditInfo(auditInfo)
                .build();

        // Then
        assertNotNull(context.getAuditInfo());
        assertEquals("TOKEN_EXCHANGE_DELEGATION", context.getAuditInfo().get("event_type"));
    }

    @Test
    void shouldStoreRequestedTokenType() {
        // Given
        String requestedTokenType = "urn:ietf:params:oauth:token-type:access_token";

        // When
        DelegationContext context = DelegationContext.builder()
                .subjectToken(createSubjectToken())
                .asSimpleExchange()
                .requestedTokenType(requestedTokenType)
                .build();

        // Then
        assertEquals(requestedTokenType, context.getRequestedTokenType());
    }

    @Test
    void shouldStoreResource() {
        // Given
        String resource = "https://api.example.com/v1/users";

        // When
        DelegationContext context = DelegationContext.builder()
                .subjectToken(createSubjectToken())
                .asSimpleExchange()
                .resource(resource)
                .build();

        // Then
        assertEquals(resource, context.getResource());
    }

    @Test
    void shouldBuildCompleteContext() {
        // Given
        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .issuer("https://issuer.example.com")
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .clientId("admin-app")
                .build();

        Map<String, Object> actorClaim = new HashMap<>();
        actorClaim.put("sub", "admin@example.com");
        actorClaim.put("client_id", "admin-app");

        Map<String, Object> auditInfo = new HashMap<>();
        auditInfo.put("event_type", "TOKEN_EXCHANGE_DELEGATION");

        // When
        DelegationContext context = DelegationContext.builder()
                .subjectToken(subjectToken)
                .actorToken(actorToken)
                .asDelegation(actorClaim, 1)
                .grantedScopes(Set.of("read", "write", "admin"))
                .audience("https://api.example.com")
                .resource("https://api.example.com/v1/users")
                .requestedTokenType("urn:ietf:params:oauth:token-type:access_token")
                .clientId("requesting-client")
                .auditInfo(auditInfo)
                .build();

        // Then
        assertNotNull(context);
        assertEquals("user@example.com", context.getEffectiveSubject());
        assertEquals("admin@example.com", context.getActorSubject());
        assertTrue(context.isDelegation());
        assertEquals(1, context.getDelegationChainDepth());
        assertEquals(3, context.getGrantedScopes().size());
        assertEquals("https://api.example.com", context.getAudience());
        assertEquals("https://api.example.com/v1/users", context.getResource());
        assertEquals("urn:ietf:params:oauth:token-type:access_token", context.getRequestedTokenType());
        assertEquals("requesting-client", context.getClientId());
        assertNotNull(context.getAuditInfo());
    }

    private ValidatedToken createSubjectToken() {
        return ValidatedToken.builder()
                .subject("user@example.com")
                .build();
    }
}
