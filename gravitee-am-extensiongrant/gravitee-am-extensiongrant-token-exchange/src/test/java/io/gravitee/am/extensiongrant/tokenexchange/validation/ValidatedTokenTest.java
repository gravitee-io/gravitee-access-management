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
package io.gravitee.am.extensiongrant.tokenexchange.validation;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ValidatedToken - RFC 8693 validated token representation.
 *
 * @author GraviteeSource Team
 */
class ValidatedTokenTest {

    @Test
    void shouldBuildValidatedToken() {
        // Given
        Map<String, Object> claims = new HashMap<>();
        claims.put("custom_claim", "value");

        // When
        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .issuer("https://issuer.example.com")
                .claims(claims)
                .scopes(Set.of("read", "write"))
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .issuedAt(new Date())
                .tokenId("jti-12345")
                .audience(List.of("https://api.example.com"))
                .clientId("client-123")
                .tokenType("urn:ietf:params:oauth:token-type:access_token")
                .domain("example-domain")
                .build();

        // Then
        assertNotNull(token);
        assertEquals("user@example.com", token.getSubject());
        assertEquals("https://issuer.example.com", token.getIssuer());
        assertEquals("value", token.getClaim("custom_claim"));
        assertTrue(token.hasScope("read"));
        assertTrue(token.hasScope("write"));
        assertNotNull(token.getExpiration());
        assertNotNull(token.getIssuedAt());
        assertEquals("jti-12345", token.getTokenId());
        assertEquals(1, token.getAudience().size());
        assertEquals("https://api.example.com", token.getAudience().get(0));
        assertEquals("client-123", token.getClientId());
        assertEquals("urn:ietf:params:oauth:token-type:access_token", token.getTokenType());
        assertEquals("example-domain", token.getDomain());
    }

    @Test
    void shouldDetectExpiredToken() {
        // Given - token expired 1 hour ago
        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .expiration(new Date(System.currentTimeMillis() - 3600000))
                .build();

        // When/Then
        assertTrue(token.isExpired());
    }

    @Test
    void shouldDetectNonExpiredToken() {
        // Given - token expires in 1 hour
        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .build();

        // When/Then
        assertFalse(token.isExpired());
    }

    @Test
    void shouldNotBeExpiredIfNoExpiration() {
        // Given - no expiration set
        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .build();

        // When/Then
        assertFalse(token.isExpired());
    }

    @Test
    void shouldDetectNotYetValidToken() {
        // Given - token valid in 1 hour
        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .notBefore(new Date(System.currentTimeMillis() + 3600000))
                .build();

        // When/Then
        assertTrue(token.isNotYetValid());
    }

    @Test
    void shouldDetectValidToken() {
        // Given - token became valid 1 hour ago
        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .notBefore(new Date(System.currentTimeMillis() - 3600000))
                .build();

        // When/Then
        assertFalse(token.isNotYetValid());
    }

    @Test
    void shouldNotBeNotYetValidIfNoNbf() {
        // Given - no nbf set
        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .build();

        // When/Then
        assertFalse(token.isNotYetValid());
    }

    @Test
    void shouldGetClaimByName() {
        // Given
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "user@example.com");
        claims.put("name", "Test User");

        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .claims(claims)
                .build();

        // When/Then
        assertEquals("user@example.com", token.getClaim("email"));
        assertEquals("Test User", token.getClaim("name"));
        assertNull(token.getClaim("nonexistent"));
    }

    @Test
    void shouldGetClaimWithTypeCasting() {
        // Given
        Map<String, Object> claims = new HashMap<>();
        claims.put("age", 25);
        claims.put("active", true);
        claims.put("name", "Test User");

        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .claims(claims)
                .build();

        // When/Then
        assertEquals(Integer.valueOf(25), token.getClaim("age", Integer.class));
        assertEquals(Boolean.TRUE, token.getClaim("active", Boolean.class));
        assertEquals("Test User", token.getClaim("name", String.class));
        assertNull(token.getClaim("age", String.class)); // wrong type
        assertNull(token.getClaim("nonexistent", String.class));
    }

    @Test
    void shouldCheckClaimExistence() {
        // Given
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "user@example.com");

        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .claims(claims)
                .build();

        // When/Then
        assertTrue(token.hasClaim("email"));
        assertFalse(token.hasClaim("nonexistent"));
    }

    @Test
    void shouldReturnFalseForHasClaimWithNullClaims() {
        // Given
        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .claims(null)
                .build();

        // When/Then
        assertFalse(token.hasClaim("email"));
    }

    @Test
    void shouldCheckScopesExistence() {
        // Given - with scopes
        ValidatedToken tokenWithScopes = ValidatedToken.builder()
                .subject("user@example.com")
                .scopes(Set.of("read", "write"))
                .build();

        // Given - without scopes
        ValidatedToken tokenWithoutScopes = ValidatedToken.builder()
                .subject("user@example.com")
                .scopes(null)
                .build();

        // Given - empty scopes
        ValidatedToken tokenWithEmptyScopes = ValidatedToken.builder()
                .subject("user@example.com")
                .scopes(Collections.emptySet())
                .build();

        // When/Then
        assertTrue(tokenWithScopes.hasScopes());
        assertFalse(tokenWithoutScopes.hasScopes());
        assertFalse(tokenWithEmptyScopes.hasScopes());
    }

    @Test
    void shouldCheckSpecificScope() {
        // Given
        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .scopes(Set.of("read", "write", "admin"))
                .build();

        // When/Then
        assertTrue(token.hasScope("read"));
        assertTrue(token.hasScope("write"));
        assertTrue(token.hasScope("admin"));
        assertFalse(token.hasScope("delete"));
    }

    @Test
    void shouldReturnFalseForHasScopeWithNullScopes() {
        // Given
        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .scopes(null)
                .build();

        // When/Then
        assertFalse(token.hasScope("read"));
    }

    @Test
    void shouldStoreActClaim() {
        // Given
        Map<String, Object> actClaim = new HashMap<>();
        actClaim.put("sub", "admin@example.com");

        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .actClaim(actClaim)
                .build();

        // When/Then
        assertNotNull(token.getActClaim());
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedAct = (Map<String, Object>) token.getActClaim();
        assertEquals("admin@example.com", retrievedAct.get("sub"));
    }

    @Test
    void shouldStoreMayActClaim() {
        // Given
        Map<String, Object> mayActClaim = new HashMap<>();
        mayActClaim.put("sub", "admin@example.com");
        mayActClaim.put("aud", "https://api.example.com");

        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .mayActClaim(mayActClaim)
                .build();

        // When/Then
        assertNotNull(token.getMayActClaim());
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedMayAct = (Map<String, Object>) token.getMayActClaim();
        assertEquals("admin@example.com", retrievedMayAct.get("sub"));
        assertEquals("https://api.example.com", retrievedMayAct.get("aud"));
    }

    @Test
    void shouldHandleNullClaims() {
        // Given
        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .claims(null)
                .build();

        // When/Then
        assertNull(token.getClaim("any"));
        assertNull(token.getClaim("any", String.class));
    }

    @Test
    void shouldHandleMultipleAudiences() {
        // Given
        ValidatedToken token = ValidatedToken.builder()
                .subject("user@example.com")
                .audience(List.of("https://api1.example.com", "https://api2.example.com"))
                .build();

        // When/Then
        assertEquals(2, token.getAudience().size());
        assertTrue(token.getAudience().contains("https://api1.example.com"));
        assertTrue(token.getAudience().contains("https://api2.example.com"));
    }
}
