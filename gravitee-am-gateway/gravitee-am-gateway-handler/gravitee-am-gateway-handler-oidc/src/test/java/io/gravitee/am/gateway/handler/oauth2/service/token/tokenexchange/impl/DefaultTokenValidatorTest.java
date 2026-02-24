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
import io.gravitee.am.gateway.handler.oauth2.exception.TokenVerificationException;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TokenExchangeSettings;
import com.nimbusds.jose.JOSEException;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
        validator = new DefaultTokenValidator(jwtService, JWTService.TokenType.ACCESS_TOKEN, TOKEN_TYPE_URN);
    }

    @Test
    public void testGetSupportedTokenType() {
        assertEquals(TOKEN_TYPE_URN, validator.getSupportedTokenType());
    }

    @Test
    public void testValidateSuccess() {
        JWT jwt = createValidJWT();
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(validatedToken -> {
            assertEquals("user-123", validatedToken.getSubject());
            assertEquals("https://issuer.example.com", validatedToken.getIssuer());
            assertEquals("client-123", validatedToken.getClientId());
            assertEquals("jti-123", validatedToken.getTokenId());
            assertEquals(TOKEN_TYPE_URN, validatedToken.getTokenType());
            assertEquals(DOMAIN_ID, validatedToken.getDomain());
            assertFalse(validatedToken.isTrustedIssuerValidated());
            assertNotNull(validatedToken.getExpiration());
            assertTrue(validatedToken.getScopes().contains("read"));
            assertTrue(validatedToken.getScopes().contains("write"));
            assertEquals(1, validatedToken.getAudience().size());
            assertEquals("audience-123", validatedToken.getAudience().get(0));
            return true;
        });
    }

    @Test
    public void testValidateExpiredToken() {
        JWT jwt = createValidJWT();
        jwt.setExp((System.currentTimeMillis() / 1000) - 3600);

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error -> error.getMessage().contains("has expired"));
    }

    @Test
    public void testValidateNotYetValidToken() {
        JWT jwt = createValidJWT();
        jwt.setNbf((System.currentTimeMillis() / 1000) + 3600);

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error -> error.getMessage().contains("is not yet valid"));
    }

    @Test
    public void testSignatureFailure_throwsTokenVerificationException() {
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(TokenVerificationException.class);
        testObserver.assertError(error -> error.getMessage().contains("Invalid " + TOKEN_TYPE_URN));
    }

    @Test
    public void testExpiredToken_isInvalidGrantException_notTokenVerificationException() {
        JWT jwt = createValidJWT();
        jwt.setExp((System.currentTimeMillis() / 1000) - 3600);

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(error ->
            error instanceof InvalidGrantException && !(error instanceof TokenVerificationException)
        );
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
        jwt.put(Claims.DOMAIN, DOMAIN_ID);

        return jwt;
    }

}
