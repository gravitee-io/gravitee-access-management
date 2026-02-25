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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.impl;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TrustedIssuer;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenValidationUtilsTest {

    // --- parseScopes ---

    @Test
    public void parseScopes_null_returnsEmpty() {
        assertEquals(Collections.emptySet(), TokenValidationUtils.parseScopes(null));
    }

    @Test
    public void parseScopes_string_splitsOnWhitespace() {
        Set<String> result = TokenValidationUtils.parseScopes("read write admin");
        assertEquals(3, result.size());
        assertTrue(result.contains("read"));
        assertTrue(result.contains("write"));
        assertTrue(result.contains("admin"));
    }

    @Test
    public void parseScopes_singleString_returnsSingletonSet() {
        Set<String> result = TokenValidationUtils.parseScopes("read");
        assertEquals(1, result.size());
        assertTrue(result.contains("read"));
    }

    @Test
    public void parseScopes_list_returnsAsSet() {
        Set<String> result = TokenValidationUtils.parseScopes(Arrays.asList("read", "write"));
        assertEquals(2, result.size());
        assertTrue(result.contains("read"));
        assertTrue(result.contains("write"));
    }

    @Test
    public void parseScopes_unsupportedType_returnsEmpty() {
        assertEquals(Collections.emptySet(), TokenValidationUtils.parseScopes(12345));
    }

    // --- parseAudience ---

    @Test
    public void parseAudience_null_returnsEmpty() {
        assertEquals(Collections.emptyList(), TokenValidationUtils.parseAudience(null));
    }

    @Test
    public void parseAudience_string_returnsSingletonList() {
        List<String> result = TokenValidationUtils.parseAudience("aud-1");
        assertEquals(1, result.size());
        assertEquals("aud-1", result.get(0));
    }

    @Test
    public void parseAudience_list_returnsAsList() {
        List<String> result = TokenValidationUtils.parseAudience(Arrays.asList("aud-1", "aud-2"));
        assertEquals(2, result.size());
        assertEquals("aud-1", result.get(0));
        assertEquals("aud-2", result.get(1));
    }

    @Test
    public void parseAudience_unsupportedType_returnsEmpty() {
        assertEquals(Collections.emptyList(), TokenValidationUtils.parseAudience(12345));
    }

    // --- validateTemporalClaims ---

    @Test
    public void validateTemporalClaims_validToken_noException() {
        long future = (System.currentTimeMillis() / 1000) + 3600;
        long past = (System.currentTimeMillis() / 1000) - 60;
        TokenValidationUtils.validateTemporalClaims(future, past, "test-token");
    }

    @Test
    public void validateTemporalClaims_zeroValues_noException() {
        TokenValidationUtils.validateTemporalClaims(0, 0, "test-token");
    }

    @Test(expected = InvalidGrantException.class)
    public void validateTemporalClaims_expired_throws() {
        long past = (System.currentTimeMillis() / 1000) - 3600;
        TokenValidationUtils.validateTemporalClaims(past, 0, "test-token");
    }

    @Test
    public void validateTemporalClaims_expired_messageContainsTokenType() {
        long past = (System.currentTimeMillis() / 1000) - 3600;
        try {
            TokenValidationUtils.validateTemporalClaims(past, 0, "my-token-type");
        } catch (InvalidGrantException e) {
            assertEquals("my-token-type has expired", e.getMessage());
            return;
        }
        throw new AssertionError("Expected InvalidGrantException");
    }

    @Test(expected = InvalidGrantException.class)
    public void validateTemporalClaims_notYetValid_throws() {
        long future = (System.currentTimeMillis() / 1000) + 3600;
        TokenValidationUtils.validateTemporalClaims(0, future, "test-token");
    }

    @Test
    public void validateTemporalClaims_notYetValid_messageContainsTokenType() {
        long future = (System.currentTimeMillis() / 1000) + 3600;
        try {
            TokenValidationUtils.validateTemporalClaims(0, future, "my-token-type");
        } catch (InvalidGrantException e) {
            assertEquals("my-token-type is not yet valid", e.getMessage());
            return;
        }
        throw new AssertionError("Expected InvalidGrantException");
    }

    // --- buildValidatedToken ---

    @Test
    public void buildValidatedToken_allFields() {
        Domain domain = mock(Domain.class);
        when(domain.getId()).thenReturn("domain-1");

        Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.SUB, "user-1");
        claims.put(Claims.ISS, "https://issuer.example.com");
        claims.put(Claims.JTI, "jti-1");
        claims.put(Claims.CLIENT_ID, "client-1");
        claims.put("custom", "value");

        long exp = (System.currentTimeMillis() / 1000) + 3600;
        long iat = (System.currentTimeMillis() / 1000) - 60;

        ValidatedToken result = TokenValidationUtils.buildValidatedToken(
                claims, exp, iat, 0,
                Set.of("read", "write"), List.of("aud-1"),
                "token-type", domain, null);

        assertEquals("user-1", result.getSubject());
        assertEquals("https://issuer.example.com", result.getIssuer());
        assertEquals("jti-1", result.getTokenId());
        assertEquals("client-1", result.getClientId());
        assertEquals("token-type", result.getTokenType());
        assertEquals("domain-1", result.getDomain());
        assertNotNull(result.getExpiration());
        assertNotNull(result.getIssuedAt());
        assertNull(result.getNotBefore());
        assertEquals(2, result.getScopes().size());
        assertEquals(1, result.getAudience().size());
        assertFalse(result.isTrustedIssuerValidated());
        assertEquals("value", result.getClaims().get("custom"));
    }

    @Test
    public void buildValidatedToken_withTrustedIssuer() {
        Domain domain = mock(Domain.class);
        when(domain.getId()).thenReturn("domain-1");

        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer("https://external.example.com");

        Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.SUB, "ext-user");

        ValidatedToken result = TokenValidationUtils.buildValidatedToken(
                claims, 0, 0, 0,
                Set.of(), List.of(),
                "jwt", domain, ti);

        assertTrue(result.isTrustedIssuerValidated());
        assertEquals("ext-user", result.getSubject());
    }

    @Test
    public void buildValidatedToken_zeroTimestamps_nullDates() {
        Domain domain = mock(Domain.class);
        when(domain.getId()).thenReturn("domain-1");

        ValidatedToken result = TokenValidationUtils.buildValidatedToken(
                new HashMap<>(), 0, 0, 0,
                Set.of(), List.of(),
                "token-type", domain, null);

        assertNull(result.getExpiration());
        assertNull(result.getIssuedAt());
        assertNull(result.getNotBefore());
    }

    @Test
    public void buildValidatedToken_nullClaimValues_nullFields() {
        Domain domain = mock(Domain.class);
        when(domain.getId()).thenReturn("domain-1");

        ValidatedToken result = TokenValidationUtils.buildValidatedToken(
                new HashMap<>(), 0, 0, 0,
                Set.of(), List.of(),
                "token-type", domain, null);

        assertNull(result.getSubject());
        assertNull(result.getIssuer());
        assertNull(result.getTokenId());
        assertNull(result.getClientId());
    }
}
