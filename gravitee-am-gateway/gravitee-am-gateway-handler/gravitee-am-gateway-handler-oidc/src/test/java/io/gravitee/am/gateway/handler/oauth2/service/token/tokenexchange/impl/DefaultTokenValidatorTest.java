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
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TokenExchangeSettings;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultTokenValidatorTest {

    @Mock
    private JWTService jwtService;

    @Mock
    private TokenExchangeSettings settings;

    @Mock
    private Domain domain;

    private DefaultTokenValidator validator;

    private static final String TOKEN = "test.jwt.token";
    private static final String DOMAIN_ID = "domain-123";
    private static final String TOKEN_TYPE_URN = "urn:ietf:params:oauth:token-type:test";

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn(DOMAIN_ID);
        validator = new DefaultTokenValidator(jwtService, JWTService.TokenType.ACCESS_TOKEN, TOKEN_TYPE_URN);
    }

    @Test
    public void testValidateSuccess() {
        JWT jwt = createValidJWT();
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(validatedToken -> {
            assertEquals("user-123", validatedToken.getSubject());
            assertEquals("https://issuer.example.com", validatedToken.getIssuer());
            assertEquals("client-123", validatedToken.getClientId());
            assertEquals("jti-123", validatedToken.getTokenId());
            assertEquals(TOKEN_TYPE_URN, validatedToken.getTokenType());
            assertEquals(DOMAIN_ID, validatedToken.getDomain());
            assertNotNull(validatedToken.getClaims());
            assertNotNull(validatedToken.getScopes());
            assertTrue(validatedToken.getScopes().contains("read"));
            assertTrue(validatedToken.getScopes().contains("write"));
            assertNotNull(validatedToken.getAudience());
            assertEquals(1, validatedToken.getAudience().size());
            assertEquals("audience-123", validatedToken.getAudience().get(0));
            return true;
        });
    }

    @Test
    public void testValidateExpiredToken() {
        JWT jwt = createValidJWT();
        long expiredTime = (System.currentTimeMillis() / 1000) - 3600;
        jwt.setExp(expiredTime);

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().contains("has expired")
        );
    }

    @Test
    public void testValidateNotYetValidToken() {
        JWT jwt = createValidJWT();
        long futureTime = (System.currentTimeMillis() / 1000) + 3600;
        jwt.setNbf(futureTime);

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().contains("is not yet valid")
        );
    }

    @Test
    public void testValidateInvalidToken() {
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new Exception("Invalid signature")));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().contains("Invalid " + TOKEN_TYPE_URN)
        );
    }

    @Test
    public void testParseScopesAsString() {
        JWT jwt = createValidJWT();
        jwt.put(Claims.SCOPE, "read write admin");

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();

        testObserver.assertComplete();
        testObserver.assertValue(validatedToken -> {
            assertEquals(3, validatedToken.getScopes().size());
            assertTrue(validatedToken.getScopes().contains("read"));
            assertTrue(validatedToken.getScopes().contains("write"));
            assertTrue(validatedToken.getScopes().contains("admin"));
            return true;
        });
    }

    @Test
    public void testParseScopesAsList() {
        JWT jwt = createValidJWT();
        jwt.put(Claims.SCOPE, Arrays.asList("read", "write", "admin"));

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();

        testObserver.assertComplete();
        testObserver.assertValue(validatedToken -> {
            assertEquals(3, validatedToken.getScopes().size());
            assertTrue(validatedToken.getScopes().contains("read"));
            assertTrue(validatedToken.getScopes().contains("write"));
            assertTrue(validatedToken.getScopes().contains("admin"));
            return true;
        });
    }

    @Test
    public void testParseScopesNull() {
        JWT jwt = createValidJWT();
        jwt.remove(Claims.SCOPE);

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();

        testObserver.assertComplete();
        testObserver.assertValue(validatedToken -> {
            assertTrue(validatedToken.getScopes().isEmpty());
            return true;
        });
    }

    @Test
    public void testParseAudienceAsString() {
        JWT jwt = createValidJWT();
        jwt.put(Claims.AUD, "single-audience");

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();

        testObserver.assertComplete();
        testObserver.assertValue(validatedToken -> {
            assertEquals(1, validatedToken.getAudience().size());
            assertEquals("single-audience", validatedToken.getAudience().get(0));
            return true;
        });
    }

    @Test
    public void testParseAudienceNull() {
        JWT jwt = createValidJWT();
        jwt.put(Claims.AUD, null);

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();

        testObserver.assertComplete();
        testObserver.assertValue(validatedToken -> {
            assertTrue(validatedToken.getAudience().isEmpty());
            return true;
        });
    }

    @Test
    public void testValidateWithoutOptionalFields() {
        JWT jwt = new JWT();
        jwt.setSub("user-123");
        jwt.setIss("https://issuer.example.com");
        long currentTime = System.currentTimeMillis() / 1000;
        jwt.setExp(currentTime + 3600);

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();

        testObserver.assertComplete();
        testObserver.assertValue(validatedToken -> {
            assertEquals("user-123", validatedToken.getSubject());
            assertNull(validatedToken.getClientId());
            assertNull(validatedToken.getTokenId());
            assertTrue(validatedToken.getScopes().isEmpty());
            assertTrue(validatedToken.getAudience().isEmpty());
            return true;
        });
    }

    @Test
    public void testValidateWithZeroTimestamps() {
        JWT jwt = createValidJWT();
        jwt.setExp(0);
        jwt.setIat(0);
        jwt.setNbf(0);

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();

        testObserver.assertComplete();
        testObserver.assertValue(validatedToken -> {
            assertNull(validatedToken.getExpiration());
            assertNull(validatedToken.getIssuedAt());
            assertNull(validatedToken.getNotBefore());
            return true;
        });
    }

    @Test
    public void testGetSupportedTokenType() {
        String supportedType = validator.getSupportedTokenType();
        assertEquals(TOKEN_TYPE_URN, supportedType);
    }

    @Test
    public void testAllClaimsAreCopied() {
        JWT jwt = createValidJWT();
        jwt.put("custom_claim_1", "value1");
        jwt.put("custom_claim_2", 12345);
        jwt.put("custom_claim_3", Arrays.asList("a", "b", "c"));

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();

        testObserver.assertComplete();
        testObserver.assertValue(validatedToken -> {
            assertEquals("value1", validatedToken.getClaims().get("custom_claim_1"));
            assertEquals(12345, validatedToken.getClaims().get("custom_claim_2"));
            assertTrue(validatedToken.getClaims().get("custom_claim_3") instanceof List);
            return true;
        });
    }

    private JWT createValidJWT() {
        JWT jwt = new JWT();
        jwt.setSub("user-123");
        jwt.setIss("https://issuer.example.com");
        jwt.setJti("jti-123");
        jwt.setAud("audience-123");

        long currentTime = System.currentTimeMillis() / 1000;
        jwt.setExp(currentTime + 3600);
        jwt.setIat(currentTime - 60);
        jwt.setNbf(currentTime - 60);

        jwt.put(Claims.SCOPE, "read write");
        jwt.put(Claims.CLIENT_ID, "client-123");

        return jwt;
    }

}
