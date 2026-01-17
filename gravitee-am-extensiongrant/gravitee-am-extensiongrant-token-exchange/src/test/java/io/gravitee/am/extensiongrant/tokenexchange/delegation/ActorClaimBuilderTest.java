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

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.tokenexchange.TokenExchangeExtensionGrantConfiguration;
import io.gravitee.am.extensiongrant.tokenexchange.validation.ValidatedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ActorClaimBuilder - RFC 8693 Section 4.1.
 *
 * @author GraviteeSource Team
 */
class ActorClaimBuilderTest {

    private ActorClaimBuilder builder;
    private TokenExchangeExtensionGrantConfiguration configuration;

    @BeforeEach
    void setUp() {
        builder = new ActorClaimBuilder();
        configuration = new TokenExchangeExtensionGrantConfiguration();
        configuration.setMaxDelegationChainDepth(3);
    }

    @Test
    void shouldBuildSimpleActorClaim() throws InvalidGrantException {
        // Given
        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .build();

        // When
        Map<String, Object> actClaim = builder.buildActorClaim(actorToken, null, configuration);

        // Then
        assertNotNull(actClaim);
        assertEquals("admin@example.com", actClaim.get(Claims.SUB));
        assertNull(actClaim.get(Claims.ACT));
    }

    @Test
    void shouldBuildActorClaimWithClientId() throws InvalidGrantException {
        // Given
        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .clientId("admin-client")
                .build();

        // When
        Map<String, Object> actClaim = builder.buildActorClaim(actorToken, null, configuration);

        // Then
        assertNotNull(actClaim);
        assertEquals("admin@example.com", actClaim.get(Claims.SUB));
        assertEquals("admin-client", actClaim.get(Claims.CLIENT_ID));
    }

    @Test
    void shouldBuildActorClaimWithIssuer() throws InvalidGrantException {
        // Given
        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .issuer("https://issuer.example.com")
                .build();

        // When
        Map<String, Object> actClaim = builder.buildActorClaim(actorToken, null, configuration);

        // Then
        assertNotNull(actClaim);
        assertEquals("admin@example.com", actClaim.get(Claims.SUB));
        assertEquals("https://issuer.example.com", actClaim.get(Claims.ISS));
    }

    @Test
    void shouldBuildNestedActorClaim() throws InvalidGrantException {
        // Given - existing delegation chain
        Map<String, Object> existingActClaim = new HashMap<>();
        existingActClaim.put(Claims.SUB, "service-account@example.com");

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .build();

        // When
        Map<String, Object> actClaim = builder.buildActorClaim(actorToken, existingActClaim, configuration);

        // Then
        assertNotNull(actClaim);
        assertEquals("admin@example.com", actClaim.get(Claims.SUB));
        assertNotNull(actClaim.get(Claims.ACT));

        @SuppressWarnings("unchecked")
        Map<String, Object> nestedAct = (Map<String, Object>) actClaim.get(Claims.ACT);
        assertEquals("service-account@example.com", nestedAct.get(Claims.SUB));
    }

    @Test
    void shouldRejectExcessiveDelegationChainDepth() {
        // Given - max depth of 3, trying to add 4th level
        configuration.setMaxDelegationChainDepth(3);

        Map<String, Object> level3 = new HashMap<>();
        level3.put(Claims.SUB, "level3@example.com");

        Map<String, Object> level2 = new HashMap<>();
        level2.put(Claims.SUB, "level2@example.com");
        level2.put(Claims.ACT, level3);

        Map<String, Object> level1 = new HashMap<>();
        level1.put(Claims.SUB, "level1@example.com");
        level1.put(Claims.ACT, level2);

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("level0@example.com")
                .build();

        // When/Then - should reject as depth would be 4
        InvalidGrantException exception = assertThrows(InvalidGrantException.class, () ->
                builder.buildActorClaim(actorToken, level1, configuration));
        assertTrue(exception.getMessage().contains("exceed maximum"));
    }

    @Test
    void shouldAllowDelegationChainAtMaxDepth() throws InvalidGrantException {
        // Given - max depth of 3, trying to add 3rd level (should be allowed)
        configuration.setMaxDelegationChainDepth(3);

        Map<String, Object> level2 = new HashMap<>();
        level2.put(Claims.SUB, "level2@example.com");

        Map<String, Object> level1 = new HashMap<>();
        level1.put(Claims.SUB, "level1@example.com");
        level1.put(Claims.ACT, level2);

        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("level0@example.com")
                .build();

        // When
        Map<String, Object> actClaim = builder.buildActorClaim(actorToken, level1, configuration);

        // Then - depth 3 should be allowed
        assertNotNull(actClaim);
        assertEquals("level0@example.com", actClaim.get(Claims.SUB));
    }

    @Test
    void shouldCalculateDelegationDepthCorrectly() {
        // Given - no delegation
        assertEquals(0, builder.calculateDelegationDepth(null));
        assertEquals(0, builder.calculateDelegationDepth("not-a-map"));

        // Given - depth 1
        Map<String, Object> depth1 = new HashMap<>();
        depth1.put(Claims.SUB, "actor@example.com");
        assertEquals(1, builder.calculateDelegationDepth(depth1));

        // Given - depth 2
        Map<String, Object> innerAct = new HashMap<>();
        innerAct.put(Claims.SUB, "inner@example.com");
        Map<String, Object> depth2 = new HashMap<>();
        depth2.put(Claims.SUB, "outer@example.com");
        depth2.put(Claims.ACT, innerAct);
        assertEquals(2, builder.calculateDelegationDepth(depth2));

        // Given - depth 3
        Map<String, Object> innermost = new HashMap<>();
        innermost.put(Claims.SUB, "innermost@example.com");
        Map<String, Object> middle = new HashMap<>();
        middle.put(Claims.SUB, "middle@example.com");
        middle.put(Claims.ACT, innermost);
        Map<String, Object> outer = new HashMap<>();
        outer.put(Claims.SUB, "outer@example.com");
        outer.put(Claims.ACT, middle);
        assertEquals(3, builder.calculateDelegationDepth(outer));
    }

    @Test
    void shouldExtractDelegationChain() {
        // Given - delegation chain of 3 actors
        Map<String, Object> innermost = new HashMap<>();
        innermost.put(Claims.SUB, "service@example.com");

        Map<String, Object> middle = new HashMap<>();
        middle.put(Claims.SUB, "admin@example.com");
        middle.put(Claims.ACT, innermost);

        Map<String, Object> outer = new HashMap<>();
        outer.put(Claims.SUB, "super-admin@example.com");
        outer.put(Claims.ACT, middle);

        // When
        List<String> chain = builder.extractDelegationChain(outer);

        // Then
        assertEquals(3, chain.size());
        assertEquals("super-admin@example.com", chain.get(0));
        assertEquals("admin@example.com", chain.get(1));
        assertEquals("service@example.com", chain.get(2));
    }

    @Test
    void shouldReturnEmptyChainForNullActClaim() {
        // When
        List<String> chain = builder.extractDelegationChain(null);

        // Then
        assertTrue(chain.isEmpty());
    }

    @Test
    void shouldFindActorInChain() {
        // Given
        Map<String, Object> innermost = new HashMap<>();
        innermost.put(Claims.SUB, "service@example.com");

        Map<String, Object> outer = new HashMap<>();
        outer.put(Claims.SUB, "admin@example.com");
        outer.put(Claims.ACT, innermost);

        // When/Then
        assertTrue(builder.isActorInChain(outer, "admin@example.com"));
        assertTrue(builder.isActorInChain(outer, "service@example.com"));
        assertFalse(builder.isActorInChain(outer, "unknown@example.com"));
        assertFalse(builder.isActorInChain(null, "admin@example.com"));
    }

    @Test
    void shouldBuildSimpleActorClaimWithoutConfiguration() {
        // Given
        String actorSubject = "admin@example.com";
        String actorClientId = "admin-client";
        String actorIssuer = "https://issuer.example.com";

        // When
        Map<String, Object> actClaim = builder.buildActorClaim(
                actorSubject, actorClientId, actorIssuer, null);

        // Then
        assertEquals("admin@example.com", actClaim.get(Claims.SUB));
        assertEquals("admin-client", actClaim.get(Claims.CLIENT_ID));
        assertEquals("https://issuer.example.com", actClaim.get(Claims.ISS));
        assertNull(actClaim.get(Claims.ACT));
    }

    @Test
    void shouldBuildSimpleActorClaimWithoutOptionalFields() {
        // Given
        String actorSubject = "admin@example.com";

        // When
        Map<String, Object> actClaim = builder.buildActorClaim(
                actorSubject, null, null, null);

        // Then
        assertEquals("admin@example.com", actClaim.get(Claims.SUB));
        assertNull(actClaim.get(Claims.CLIENT_ID));
        assertNull(actClaim.get(Claims.ISS));
    }

    @Test
    void shouldBuildActorClaimWithExistingActClaimWithoutConfig() {
        // Given
        Map<String, Object> existingAct = new HashMap<>();
        existingAct.put(Claims.SUB, "previous-actor@example.com");

        // When
        Map<String, Object> actClaim = builder.buildActorClaim(
                "current-actor@example.com", null, null, existingAct);

        // Then
        assertEquals("current-actor@example.com", actClaim.get(Claims.SUB));
        assertNotNull(actClaim.get(Claims.ACT));

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) actClaim.get(Claims.ACT);
        assertEquals("previous-actor@example.com", nested.get(Claims.SUB));
    }

    @Test
    void shouldNotIncludeEmptyClientIdOrIssuer() throws InvalidGrantException {
        // Given
        ValidatedToken actorToken = ValidatedToken.builder()
                .subject("admin@example.com")
                .clientId("")
                .issuer("")
                .build();

        // When
        Map<String, Object> actClaim = builder.buildActorClaim(actorToken, null, configuration);

        // Then
        assertEquals("admin@example.com", actClaim.get(Claims.SUB));
        assertNull(actClaim.get(Claims.CLIENT_ID));
        assertNull(actClaim.get(Claims.ISS));
    }
}
