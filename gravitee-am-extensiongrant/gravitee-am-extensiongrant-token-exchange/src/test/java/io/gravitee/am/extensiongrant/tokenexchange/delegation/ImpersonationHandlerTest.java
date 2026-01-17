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

import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.tokenexchange.TokenExchangeExtensionGrantConfiguration;
import io.gravitee.am.extensiongrant.tokenexchange.validation.ValidatedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ImpersonationHandler - RFC 8693 Impersonation scenarios.
 *
 * @author GraviteeSource Team
 */
class ImpersonationHandlerTest {

    private ImpersonationHandler handler;
    private TokenExchangeExtensionGrantConfiguration configuration;

    @BeforeEach
    void setUp() {
        handler = new ImpersonationHandler();
        configuration = new TokenExchangeExtensionGrantConfiguration();
    }

    @Test
    void shouldDenyImpersonationWhenDisabled() {
        // Given
        configuration.setAllowImpersonation(false);
        Set<String> scopes = Set.of(ImpersonationHandler.IMPERSONATION_SCOPE);

        // When
        ImpersonationHandler.ImpersonationResult result = handler.checkImpersonation(
                configuration, null, scopes);

        // Then
        assertFalse(result.isAllowed());
        assertEquals("Impersonation is not allowed", result.getReason());
    }

    @Test
    void shouldDenyImpersonationWhenMissingScope() {
        // Given
        configuration.setAllowImpersonation(true);
        Set<String> scopes = Set.of("read", "write"); // no impersonation scope

        // When
        ImpersonationHandler.ImpersonationResult result = handler.checkImpersonation(
                configuration, null, scopes);

        // Then
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("missing scope"));
    }

    @Test
    void shouldAllowDirectImpersonationWithScope() {
        // Given
        configuration.setAllowImpersonation(true);
        Set<String> scopes = Set.of(ImpersonationHandler.IMPERSONATION_SCOPE);

        // When - no actor token = direct impersonation
        ImpersonationHandler.ImpersonationResult result = handler.checkImpersonation(
                configuration, null, scopes);

        // Then
        assertTrue(result.isAllowed());
        assertEquals(ImpersonationHandler.ImpersonationType.DIRECT, result.getType());
    }

    @Test
    void shouldAllowDelegatedImpersonationWithScope() {
        // Given
        configuration.setAllowImpersonation(true);
        Set<String> scopes = Set.of(ImpersonationHandler.IMPERSONATION_SCOPE);

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .build();

        // When - with actor token = delegated impersonation
        ImpersonationHandler.ImpersonationResult result = handler.checkImpersonation(
                configuration, actorToken, scopes);

        // Then
        assertTrue(result.isAllowed());
        assertEquals(ImpersonationHandler.ImpersonationType.DELEGATED, result.getType());
    }

    @Test
    void shouldAllowImpersonationWithAdminScope() {
        // Given
        configuration.setAllowImpersonation(true);
        Set<String> scopes = Set.of(ImpersonationHandler.ADMIN_IMPERSONATION_SCOPE);

        // When
        ImpersonationHandler.ImpersonationResult result = handler.checkImpersonation(
                configuration, null, scopes);

        // Then
        assertTrue(result.isAllowed());
    }

    @Test
    void shouldAllowImpersonationWithPrefixedScope() {
        // Given
        configuration.setAllowImpersonation(true);
        Set<String> scopes = Set.of("impersonate:users");

        // When
        ImpersonationHandler.ImpersonationResult result = handler.checkImpersonation(
                configuration, null, scopes);

        // Then
        assertTrue(result.isAllowed());
    }

    @Test
    void shouldDenyImpersonationWithNullScopes() {
        // Given
        configuration.setAllowImpersonation(true);

        // When
        ImpersonationHandler.ImpersonationResult result = handler.checkImpersonation(
                configuration, null, null);

        // Then
        assertFalse(result.isAllowed());
    }

    @Test
    void shouldDenyImpersonationWithEmptyScopes() {
        // Given
        configuration.setAllowImpersonation(true);
        Set<String> scopes = new HashSet<>();

        // When
        ImpersonationHandler.ImpersonationResult result = handler.checkImpersonation(
                configuration, null, scopes);

        // Then
        assertFalse(result.isAllowed());
    }

    @Test
    void shouldValidateImpersonationSuccessfully() throws InvalidGrantException {
        // Given
        configuration.setAllowImpersonation(true);

        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .build();

        // When/Then - should not throw
        assertDoesNotThrow(() -> handler.validateImpersonation(subjectToken, actorToken, configuration));
    }

    @Test
    void shouldRejectImpersonationWhenDisabledDuringValidation() {
        // Given
        configuration.setAllowImpersonation(false);

        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .build();

        // When/Then
        InvalidGrantException exception = assertThrows(InvalidGrantException.class, () ->
                handler.validateImpersonation(subjectToken, null, configuration));
        assertEquals("Impersonation is not allowed", exception.getMessage());
    }

    @Test
    void shouldValidateImpersonationWithMayActConstraints() throws InvalidGrantException {
        // Given
        configuration.setAllowImpersonation(true);

        Map<String, Object> mayAct = new HashMap<>();
        mayAct.put("sub", "admin@example.com");

        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim(mayAct)
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .build();

        // When/Then - should pass because actor matches may_act
        assertDoesNotThrow(() -> handler.validateImpersonation(subjectToken, actorToken, configuration));
    }

    @Test
    void shouldRejectImpersonationWhenMayActDenies() {
        // Given
        configuration.setAllowImpersonation(true);

        Map<String, Object> mayAct = new HashMap<>();
        mayAct.put("sub", "authorized-admin@example.com");

        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim(mayAct)
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("unauthorized-admin@example.com")
                .build();

        // When/Then
        InvalidGrantException exception = assertThrows(InvalidGrantException.class, () ->
                handler.validateImpersonation(subjectToken, actorToken, configuration));
        assertTrue(exception.getMessage().contains("may_act constraint"));
    }

    @Test
    void shouldIdentifyImpersonationScenarioWhenNoActorToken() {
        // Given
        configuration.setAllowImpersonation(true);

        // When - no actor token
        boolean isImpersonation = handler.isImpersonationScenario(configuration, false);

        // Then
        assertTrue(isImpersonation);
    }

    @Test
    void shouldIdentifyDelegationScenarioWithActorToken() {
        // Given
        configuration.setAllowImpersonation(true);
        configuration.setAllowDelegation(true);

        // When - has actor token
        boolean isImpersonation = handler.isImpersonationScenario(configuration, true);

        // Then - with actor token and delegation enabled, it's delegation not impersonation
        assertFalse(isImpersonation);
    }

    @Test
    void shouldIdentifyImpersonationWhenDelegationDisabled() {
        // Given
        configuration.setAllowImpersonation(true);
        configuration.setAllowDelegation(false);

        // When - has actor token but delegation disabled
        boolean isImpersonation = handler.isImpersonationScenario(configuration, true);

        // Then - should be impersonation when delegation is disabled
        assertTrue(isImpersonation);
    }

    @Test
    void shouldNotBeImpersonationWhenDisabled() {
        // Given
        configuration.setAllowImpersonation(false);

        // When
        boolean isImpersonation = handler.isImpersonationScenario(configuration, false);

        // Then
        assertFalse(isImpersonation);
    }

    @Test
    void shouldCreateAuditInfoForDirectImpersonation() {
        // Given
        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .issuer("https://issuer.example.com")
                .build();

        // When
        Map<String, Object> auditInfo = handler.createAuditInfo(
                subjectToken, null, "admin-client",
                ImpersonationHandler.ImpersonationType.DIRECT);

        // Then
        assertEquals("TOKEN_EXCHANGE_IMPERSONATION", auditInfo.get("event_type"));
        assertEquals("DIRECT", auditInfo.get("impersonation_type"));
        assertEquals("user@example.com", auditInfo.get("impersonated_subject"));
        assertEquals("https://issuer.example.com", auditInfo.get("impersonated_subject_issuer"));
        assertEquals("admin-client", auditInfo.get("client_id"));
        assertNotNull(auditInfo.get("timestamp"));
        assertNull(auditInfo.get("actor_subject"));
    }

    @Test
    void shouldCreateAuditInfoForDelegatedImpersonation() {
        // Given
        ValidatedToken subjectToken = ValidatedToken.builder()
                .subject("user@example.com")
                .issuer("https://issuer.example.com")
                .build();

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .issuer("https://admin-issuer.example.com")
                .clientId("admin-app")
                .build();

        // When
        Map<String, Object> auditInfo = handler.createAuditInfo(
                subjectToken, actorToken, "admin-client",
                ImpersonationHandler.ImpersonationType.DELEGATED);

        // Then
        assertEquals("TOKEN_EXCHANGE_IMPERSONATION", auditInfo.get("event_type"));
        assertEquals("DELEGATED", auditInfo.get("impersonation_type"));
        assertEquals("user@example.com", auditInfo.get("impersonated_subject"));
        assertEquals("admin@example.com", auditInfo.get("actor_subject"));
        assertEquals("https://admin-issuer.example.com", auditInfo.get("actor_issuer"));
        assertEquals("admin-app", auditInfo.get("actor_client_id"));
    }
}
