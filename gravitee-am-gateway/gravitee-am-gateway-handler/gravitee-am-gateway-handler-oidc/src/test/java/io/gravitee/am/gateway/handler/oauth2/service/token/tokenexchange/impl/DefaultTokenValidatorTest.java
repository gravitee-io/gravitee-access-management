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

import com.nimbusds.jwt.JWTClaimsSet;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TrustedIssuerResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.KeyResolutionMethod;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.TrustedIssuer;
import com.nimbusds.jose.JOSEException;
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
public class DefaultTokenValidatorTest {

    @Mock
    private JWTService jwtService;

    @Mock
    private TokenExchangeSettings settings;

    @Mock
    private Domain domain;

    @Mock
    private TrustedIssuerResolver trustedIssuerResolver;

    private DefaultTokenValidator validator;

    private static final String TOKEN = "test.jwt.token";
    private static final String DOMAIN_ID = "domain-123";
    private static final String TOKEN_TYPE_URN = "urn:ietf:params:oauth:token-type:test";

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn(DOMAIN_ID);
        validator = new DefaultTokenValidator(jwtService, JWTService.TokenType.ACCESS_TOKEN, TOKEN_TYPE_URN, trustedIssuerResolver);
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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().contains("is not yet valid")
        );
    }

    @Test
    public void testValidateInvalidToken_noTrustedIssuers() {
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().contains("Invalid " + TOKEN_TYPE_URN)
        );
    }

    @Test
    public void testValidateWithTrustedIssuer_untrustedIssuer() {
        // Domain cert verification fails
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        // Trusted issuers are configured but no match
        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer("https://other-issuer.example.com");
        ti.setKeyResolutionMethod(KeyResolutionMethod.JWKS_URL);
        ti.setJwksUri("https://other-issuer.example.com/jwks");
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        // Decode without verification extracts iss
        JWT decodedJwt = new JWT();
        decodedJwt.setIss("https://unknown-issuer.example.com");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().contains("Untrusted issuer: https://unknown-issuer.example.com")
        );
    }

    @Test
    public void testValidateWithTrustedIssuer_signatureVerificationFails() throws Exception {
        // Domain cert verification fails
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        // Trusted issuers are configured
        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer("https://external-idp.example.com");
        ti.setKeyResolutionMethod(KeyResolutionMethod.PEM);
        ti.setCertificate("some-pem");
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        // Decode without verification extracts matching iss
        JWT decodedJwt = new JWT();
        decodedJwt.setIss("https://external-idp.example.com");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        // Trusted issuer resolver throws (bad signature — now wrapped as InvalidGrantException by resolver)
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
    public void testValidateWithTrustedIssuer_success() throws Exception {
        // Domain cert verification fails
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        // Trusted issuer configured
        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer("https://external-idp.example.com");
        ti.setKeyResolutionMethod(KeyResolutionMethod.JWKS_URL);
        ti.setJwksUri("https://external-idp.example.com/jwks");
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        // Decode without verification extracts matching iss
        JWT decodedJwt = new JWT();
        decodedJwt.setIss("https://external-idp.example.com");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        // Trusted issuer resolver verifies successfully
        long futureExp = (System.currentTimeMillis() / 1000) + 3600;
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("external-user-123")
                .issuer("https://external-idp.example.com")
                .expirationTime(new Date(futureExp * 1000))
                .claim(Claims.SCOPE, "ext:read ext:write")
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
    public void testValidateWithTrustedIssuer_scopeMapping() throws Exception {
        // Domain cert verification fails
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        // Trusted issuer with scope mappings
        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer("https://external-idp.example.com");
        ti.setKeyResolutionMethod(KeyResolutionMethod.PEM);
        ti.setCertificate("some-pem");
        ti.setScopeMappings(Map.of("ext:read", "domain:read", "ext:write", "domain:write"));
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        // Decode without verification
        JWT decodedJwt = new JWT();
        decodedJwt.setIss("https://external-idp.example.com");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        // External JWT has scopes: ext:read, ext:write, ext:admin (ext:admin is unmapped)
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
        testObserver.assertNoErrors();
        testObserver.assertValue(validatedToken -> {
            // Only mapped scopes should be present (unmapped ext:admin is dropped)
            assertEquals(2, validatedToken.getScopes().size());
            assertTrue(validatedToken.getScopes().contains("domain:read"));
            assertTrue(validatedToken.getScopes().contains("domain:write"));
            assertFalse(validatedToken.getScopes().contains("ext:admin"));
            assertTrue(validatedToken.isTrustedIssuerValidated());
            return true;
        });
    }

    @Test
    public void testValidateWithTrustedIssuer_noScopeMapping_passThrough() throws Exception {
        // Domain cert verification fails
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        // Trusted issuer without scope mappings (null)
        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer("https://external-idp.example.com");
        ti.setKeyResolutionMethod(KeyResolutionMethod.PEM);
        ti.setCertificate("some-pem");
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

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
            // All scopes pass through when no mapping configured
            assertTrue(validatedToken.getScopes().contains("read"));
            assertTrue(validatedToken.getScopes().contains("write"));
            return true;
        });
    }

    @Test
    public void testValidateWithTrustedIssuer_allFieldsCopied() throws Exception {
        // Domain cert verification fails
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer("https://external-idp.example.com");
        ti.setKeyResolutionMethod(KeyResolutionMethod.PEM);
        ti.setCertificate("some-pem");
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        JWT decodedJwt = new JWT();
        decodedJwt.setIss("https://external-idp.example.com");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        long currentTime = System.currentTimeMillis() / 1000;
        long futureExp = currentTime + 3600;
        long pastIat = currentTime - 60;
        long pastNbf = currentTime - 60;
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("external-user-123")
                .issuer("https://external-idp.example.com")
                .jwtID("ext-jti-456")
                .audience(Arrays.asList("aud-1", "aud-2"))
                .expirationTime(new Date(futureExp * 1000))
                .issueTime(new Date(pastIat * 1000))
                .notBeforeTime(new Date(pastNbf * 1000))
                .claim(Claims.SCOPE, "ext:read ext:write")
                .claim(Claims.CLIENT_ID, "ext-client-789")
                .claim("custom_claim", "custom_value")
                .build();
        when(trustedIssuerResolver.resolve(eq(TOKEN), eq(ti)))
                .thenReturn(claimsSet);

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(validatedToken -> {
            // Standard claims
            assertEquals("external-user-123", validatedToken.getSubject());
            assertEquals("https://external-idp.example.com", validatedToken.getIssuer());
            assertEquals("ext-jti-456", validatedToken.getTokenId());
            assertEquals("ext-client-789", validatedToken.getClientId());
            // Audience
            assertEquals(2, validatedToken.getAudience().size());
            assertEquals("aud-1", validatedToken.getAudience().get(0));
            assertEquals("aud-2", validatedToken.getAudience().get(1));
            // Temporal claims
            assertNotNull(validatedToken.getExpiration());
            assertNotNull(validatedToken.getIssuedAt());
            assertNotNull(validatedToken.getNotBefore());
            // Scopes
            assertEquals(2, validatedToken.getScopes().size());
            assertTrue(validatedToken.getScopes().contains("ext:read"));
            assertTrue(validatedToken.getScopes().contains("ext:write"));
            // Metadata
            assertEquals(TOKEN_TYPE_URN, validatedToken.getTokenType());
            assertEquals(DOMAIN_ID, validatedToken.getDomain());
            assertTrue(validatedToken.isTrustedIssuerValidated());
            // Custom claims
            assertEquals("custom_value", validatedToken.getClaims().get("custom_claim"));
            return true;
        });
    }

    @Test
    public void testValidateWithTrustedIssuer_expiredToken() throws Exception {
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer("https://external-idp.example.com");
        ti.setKeyResolutionMethod(KeyResolutionMethod.PEM);
        ti.setCertificate("some-pem");
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

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
    public void testValidateWithTrustedIssuer_nullTimestamps() throws Exception {
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer("https://external-idp.example.com");
        ti.setKeyResolutionMethod(KeyResolutionMethod.PEM);
        ti.setCertificate("some-pem");
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        JWT decodedJwt = new JWT();
        decodedJwt.setIss("https://external-idp.example.com");
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        // No exp, iat, or nbf set
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

    @Test
    public void testParseScopesAsString() {
        JWT jwt = createValidJWT();
        jwt.put(Claims.SCOPE, "read write admin");

        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(jwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

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
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertValue(validatedToken -> {
            assertEquals("value1", validatedToken.getClaims().get("custom_claim_1"));
            assertEquals(12345, validatedToken.getClaims().get("custom_claim_2"));
            assertTrue(validatedToken.getClaims().get("custom_claim_3") instanceof List);
            return true;
        });
    }

    @Test
    public void testValidateWithoutResolver_doesNotFallThroughToTrustedIssuers() {
        // Create a validator without a TrustedIssuerResolver (like access_token, id_token, refresh_token)
        DefaultTokenValidator noResolverValidator = new DefaultTokenValidator(
                jwtService, JWTService.TokenType.ACCESS_TOKEN, TOKEN_TYPE_URN, null);

        // Domain cert verification fails
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        // Even though settings could have trusted issuers configured, the validator should NOT attempt fallback
        // (no stubbing of settings.getTrustedIssuers() needed — the null resolver short-circuits first)

        TestObserver<ValidatedToken> testObserver = noResolverValidator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().contains("Invalid " + TOKEN_TYPE_URN)
        );
    }

    @Test
    public void testValidateStage1InvalidGrantException_propagatesImmediately() {
        // T1: When domain cert validation returns InvalidGrantException and trusted issuers are
        // configured, the error must propagate immediately without falling through to trusted issuers.
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new InvalidGrantException("Domain validation rejected this token")));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().contains("Domain validation rejected this token")
        );
    }

    @Test
    public void testValidateWithTrustedIssuer_blankIssClaimReturnsError() throws Exception {
        // T2: Blank 'iss' claim in decoded JWT should produce InvalidGrantException
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.error(new JOSEException("Invalid signature")));

        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer("https://external-idp.example.com");
        ti.setKeyResolutionMethod(KeyResolutionMethod.PEM);
        ti.setCertificate("some-pem");
        when(settings.getTrustedIssuers()).thenReturn(List.of(ti));

        JWT decodedJwt = new JWT();
        decodedJwt.setIss("   "); // blank issuer
        when(jwtService.decode(eq(TOKEN), eq(JWTService.TokenType.ACCESS_TOKEN)))
                .thenReturn(Single.just(decodedJwt));

        TestObserver<ValidatedToken> testObserver = validator.validate(TOKEN, settings, domain).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(InvalidGrantException.class);
        testObserver.assertError(error ->
            error.getMessage().contains("JWT missing 'iss' claim")
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

        return jwt;
    }

}
