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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.TokenVerificationException;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TrustedIssuerResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.KeyResolutionMethod;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.TrustedIssuer;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TrustedIssuerTokenValidatorTest {

    @Mock
    private JWTService jwtService;

    @Mock
    private TrustedIssuerResolver trustedIssuerResolver;

    @Mock
    private TokenExchangeSettings settings;

    @Mock
    private Domain domain;

    private TrustedIssuerTokenValidator validator;

    private static final String TOKEN = "test.jwt.token";
    private static final String DOMAIN_ID = "domain-123";
    private static final String TOKEN_TYPE_URN = "urn:ietf:params:oauth:token-type:test";

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn(DOMAIN_ID);
        DefaultTokenValidator delegate = new DefaultTokenValidator(
                jwtService, JWTService.TokenType.ACCESS_TOKEN, TOKEN_TYPE_URN);
        validator = new TrustedIssuerTokenValidator(
                delegate, trustedIssuerResolver, jwtService,
                JWTService.TokenType.ACCESS_TOKEN, TOKEN_TYPE_URN);
    }

    // --- Decorator behavior: no trusted issuers configured ---

    @Test
    public void testDelegateSuccess_noFallback() {
        JWT jwt = createValidJWT();
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(validatedToken -> {
            assertEquals("user-123", validatedToken.getSubject());
            assertFalse(validatedToken.isTrustedIssuerValidated());
            return true;
        });
    }

    @Test
    public void testDelegateExpired_propagatesWithoutFallback() {
        JWT jwt = createValidJWT();
        jwt.setExp((System.currentTimeMillis() / 1000) - 3600);
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().contains("has expired")
        );
    }

    @Test
    public void testNoTrustedIssuers_signatureFailurePropagates() {
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(TokenVerificationException.class);
        testObserver.assertError(error ->
            error.getMessage().contains("Invalid " + TOKEN_TYPE_URN)
        );
    }

    // --- Decorator behavior: trusted issuers configured, delegate fails ---

    @Test
    public void testExpiredDomainToken_propagatesEvenWithTrustedIssuers() {
        // Domain token — signature matches but token is expired
        // InvalidGrantException (not TokenVerificationException) propagates without checking trusted issuers
        JWT expiredJwt = createValidJWT();
        expiredJwt.setExp((System.currentTimeMillis() / 1000) - 3600);
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(expiredJwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        // Expired error propagates — no fallback to trusted issuers
        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().contains("has expired")
        );
    }

    @Test
    public void testUnknownIssuer_reportsUntrustedIssuer() {
        TrustedIssuer ti = createTrustedIssuer();
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        // Delegate fails with TokenVerificationException
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        // Decode extracts iss that doesn't match any trusted issuer
        JWT decodedJwt = new JWT();
        decodedJwt.setIss("https://unknown-issuer.example.com");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().equals("Untrusted issuer: https://unknown-issuer.example.com")
        );
    }

    @Test
    public void testBlankIssClaim_reportsJwtMissingIss() {
        TrustedIssuer ti = createTrustedIssuer();
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        JWT decodedJwt = new JWT();
        decodedJwt.setIss("   ");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().equals("JWT missing 'iss' claim")
        );
    }

    // --- Trusted issuer validation ---

    @Test
    public void testTrustedIssuer_success() throws Exception {
        TrustedIssuer ti = createTrustedIssuer();
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        JWT decodedJwt = new JWT();
        decodedJwt.setIss("https://external-idp.example.com");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        long futureExp = (System.currentTimeMillis() / 1000) + 3600;
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("external-user-123")
                .issuer("https://external-idp.example.com")
                .expirationTime(new Date(futureExp * 1000))
                .claim(Claims.SCOPE, "ext:read ext:write")
                .claim(Claims.DOMAIN, DOMAIN_ID)
                .build();
        when(trustedIssuerResolver.resolve(eq(TOKEN), eq(ti)))
                .thenReturn(claimsSet);

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(validatedToken -> {
            assertEquals("external-user-123", validatedToken.getSubject());
            assertEquals("https://external-idp.example.com", validatedToken.getIssuer());
            assertTrue(validatedToken.isTrustedIssuerValidated());
            assertEquals(DOMAIN_ID, validatedToken.getDomain());
            assertTrue(validatedToken.getScopes().contains("ext:read"));
            assertTrue(validatedToken.getScopes().contains("ext:write"));
            return true;
        });
    }

    @Test
    public void testTrustedIssuer_signatureVerificationFails() throws Exception {
        TrustedIssuer ti = createTrustedIssuer();
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        JWT decodedJwt = new JWT();
        decodedJwt.setIss("https://external-idp.example.com");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        when(trustedIssuerResolver.resolve(eq(TOKEN), eq(ti)))
                .thenThrow(new InvalidGrantException("JWT signature verification failed for trusted issuer: https://external-idp.example.com"));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().contains("JWT signature verification failed")
        );
    }

    @Test
    public void testTrustedIssuer_scopeMapping() throws Exception {
        TrustedIssuer ti = createTrustedIssuer();
        ti.setScopeMappings(Map.of("ext:read", "domain:read", "ext:write", "domain:write"));
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        JWT decodedJwt = new JWT();
        decodedJwt.setIss("https://external-idp.example.com");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        long futureExp = (System.currentTimeMillis() / 1000) + 3600;
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("external-user-123")
                .issuer("https://external-idp.example.com")
                .expirationTime(new Date(futureExp * 1000))
                .claim(Claims.SCOPE, "ext:read ext:write ext:admin")
                .build();
        when(trustedIssuerResolver.resolve(eq(TOKEN), eq(ti)))
                .thenReturn(claimsSet);

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertValue(validatedToken -> {
            assertEquals(2, validatedToken.getScopes().size());
            assertTrue(validatedToken.getScopes().contains("domain:read"));
            assertTrue(validatedToken.getScopes().contains("domain:write"));
            assertFalse(validatedToken.getScopes().contains("ext:admin"));
            return true;
        });
    }

    @Test
    public void testTrustedIssuer_noScopeMapping_passThrough() throws Exception {
        TrustedIssuer ti = createTrustedIssuer();
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        JWT decodedJwt = new JWT();
        decodedJwt.setIss("https://external-idp.example.com");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        long futureExp = (System.currentTimeMillis() / 1000) + 3600;
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("external-user-123")
                .issuer("https://external-idp.example.com")
                .expirationTime(new Date(futureExp * 1000))
                .claim(Claims.SCOPE, "read write")
                .build();
        when(trustedIssuerResolver.resolve(eq(TOKEN), eq(ti)))
                .thenReturn(claimsSet);

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertValue(validatedToken -> {
            assertTrue(validatedToken.getScopes().contains("read"));
            assertTrue(validatedToken.getScopes().contains("write"));
            return true;
        });
    }

    @Test
    public void testTrustedIssuer_allFieldsCopied() throws Exception {
        TrustedIssuer ti = createTrustedIssuer();
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        JWT decodedJwt = new JWT();
        decodedJwt.setIss("https://external-idp.example.com");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        long currentTime = System.currentTimeMillis() / 1000;
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("external-user-123")
                .issuer("https://external-idp.example.com")
                .jwtID("ext-jti-456")
                .audience(Arrays.asList("aud-1", "aud-2"))
                .expirationTime(new Date((currentTime + 3600) * 1000))
                .issueTime(new Date((currentTime - 60) * 1000))
                .notBeforeTime(new Date((currentTime - 60) * 1000))
                .claim(Claims.SCOPE, "ext:read ext:write")
                .claim(Claims.CLIENT_ID, "ext-client-789")
                .claim(Claims.DOMAIN, DOMAIN_ID)
                .claim("custom_claim", "custom_value")
                .build();
        when(trustedIssuerResolver.resolve(eq(TOKEN), eq(ti)))
                .thenReturn(claimsSet);

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertValue(validatedToken -> {
            assertEquals("external-user-123", validatedToken.getSubject());
            assertEquals("https://external-idp.example.com", validatedToken.getIssuer());
            assertEquals("ext-jti-456", validatedToken.getTokenId());
            assertEquals("ext-client-789", validatedToken.getClientId());
            assertEquals(2, validatedToken.getAudience().size());
            assertNotNull(validatedToken.getExpiration());
            assertNotNull(validatedToken.getIssuedAt());
            assertNotNull(validatedToken.getNotBefore());
            assertEquals(TOKEN_TYPE_URN, validatedToken.getTokenType());
            assertEquals(DOMAIN_ID, validatedToken.getDomain());
            assertTrue(validatedToken.isTrustedIssuerValidated());
            assertEquals("custom_value", validatedToken.getClaims().get("custom_claim"));
            return true;
        });
    }

    @Test
    public void testTrustedIssuer_expiredToken() throws Exception {
        TrustedIssuer ti = createTrustedIssuer();
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        JWT decodedJwt = new JWT();
        decodedJwt.setIss("https://external-idp.example.com");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        long pastExp = (System.currentTimeMillis() / 1000) - 3600;
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("external-user-123")
                .issuer("https://external-idp.example.com")
                .expirationTime(new Date(pastExp * 1000))
                .build();
        when(trustedIssuerResolver.resolve(eq(TOKEN), eq(ti)))
                .thenReturn(claimsSet);

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().contains("has expired")
        );
    }

    @Test
    public void testTrustedIssuer_nullTimestamps() throws Exception {
        TrustedIssuer ti = createTrustedIssuer();
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        JWT decodedJwt = new JWT();
        decodedJwt.setIss("https://external-idp.example.com");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("external-user-123")
                .issuer("https://external-idp.example.com")
                .build();
        when(trustedIssuerResolver.resolve(eq(TOKEN), eq(ti)))
                .thenReturn(claimsSet);

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertValue(validatedToken -> {
            assertNull(validatedToken.getExpiration());
            assertNull(validatedToken.getIssuedAt());
            assertNull(validatedToken.getNotBefore());
            return true;
        });
    }

    // --- Helpers ---

    private TrustedIssuer createTrustedIssuer() {
        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer("https://external-idp.example.com");
        ti.setKeyResolutionMethod(KeyResolutionMethod.PEM);
        ti.setCertificate("some-pem");
        return ti;
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
