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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.certificate.api.X509CertUtils;
import io.gravitee.am.gateway.handler.oidc.service.jws.impl.JWSServiceImpl;
import io.gravitee.am.gateway.handler.oidc.service.trustdomain.impl.TrustDomainKeyServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.KeyResolutionMethod;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.oidc.KeyRetrievalSettings;
import io.gravitee.am.service.jwk.JWKSetFetcher;
import io.gravitee.am.service.jwk.JWKSetFetcher.JWKSetFetchResponse;
import io.gravitee.am.service.utils.jwk.converter.JWKConverter;
import io.reactivex.rxjava3.core.Maybe;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Trusted-issuer key resolution, exercised through the real shared trust bundle service so the
 * SSRF guard, the response-size bound and the cache behaviour it brings are covered here.
 */
@ExtendWith(MockitoExtension.class)
class TrustedIssuerResolverImplTest {

    private static final String ISSUER = "https://trusted.example.com";
    private static final String JWKS_URL = "https://example.com/.well-known/jwks.json";
    private static final long DEFAULT_MAX_RESPONSE_SIZE_BYTES =
            (long) KeyRetrievalSettings.DEFAULT_MAX_RESPONSE_SIZE_KB * 1024L;

    private static String trustedCertPem;
    private static PrivateKey trustedPrivateKey;
    private static RSAPublicKey trustedPublicKey;
    private static PrivateKey untrustedPrivateKey;

    @Mock
    private JWKSetFetcher jwkSetFetcher;

    private KeyRetrievalSettings settings;
    private Domain domain;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair trustedKeyPair = keyGen.generateKeyPair();
        trustedPrivateKey = trustedKeyPair.getPrivate();
        trustedPublicKey = (RSAPublicKey) trustedKeyPair.getPublic();
        trustedCertPem = selfSignedCertPem(trustedKeyPair);

        untrustedPrivateKey = keyGen.generateKeyPair().getPrivate();
    }

    @BeforeEach
    void setUp() {
        settings = new KeyRetrievalSettings();
        settings.setFetchTimeoutMs(0);
        OIDCSettings oidc = new OIDCSettings();
        oidc.setKeyRetrievalSettings(settings);
        domain = new Domain();
        domain.setId("domain-1");
        domain.setOidc(oidc);
    }

    @Test
    void shouldVerifyJwtSignedWithThePemCertificateKey() throws Exception {
        String jwt = signJwt(trustedPrivateKey, "user-123", null);

        resolver().resolve(jwt, pemIssuer(trustedCertPem)).test()
                .assertNoErrors()
                .assertValue(claims -> "user-123".equals(claims.getSubject())
                        && ISSUER.equals(claims.getIssuer()));
        verify(jwkSetFetcher, never()).getKeys(anyString(), anyLong());
    }

    @Test
    void shouldVerifyJwtCarryingAKidAgainstThePemCertificate() throws Exception {
        String jwt = signJwt(trustedPrivateKey, "user-with-kid", "cert-uuid-123");

        resolver().resolve(jwt, pemIssuer(trustedCertPem)).test()
                .assertNoErrors()
                .assertValue(claims -> "user-with-kid".equals(claims.getSubject()));
    }

    @Test
    void shouldRejectJwtSignedWithAnotherKey() throws Exception {
        String jwt = signJwt(untrustedPrivateKey, "user-123", null);

        resolver().resolve(jwt, pemIssuer(trustedCertPem)).test()
                .assertError(SecurityException.class)
                .assertError(error -> error.getMessage().contains("signature verification failed"));
    }

    @Test
    void shouldRejectMalformedToken() {
        resolver().resolve("not-a-jwt-at-all", pemIssuer(trustedCertPem)).test()
                .assertError(SecurityException.class)
                .assertError(error -> error.getMessage().contains("Malformed JWT"));
    }

    @Test
    void shouldRejectJwtSignedWithASymmetricAlgorithm() throws Exception {
        JWSSigner signer = new MACSigner("0123456789012345678901234567890123456789");
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).build(),
                new JWTClaimsSet.Builder().subject("user-123").issuer(ISSUER).build());
        signedJWT.sign(signer);

        resolver().resolve(signedJWT.serialize(), pemIssuer(trustedCertPem)).test()
                .assertError(SecurityException.class)
                .assertError(error -> error.getMessage().contains("HS256"));
    }

    @Test
    void shouldFailWhenThePemCertificateCannotBeParsed() throws Exception {
        String jwt = signJwt(trustedPrivateKey, "user-123", null);

        resolver().resolve(jwt, pemIssuer("not-a-valid-pem")).test()
                .assertError(IllegalStateException.class);
    }

    @Test
    void shouldFailWhenTheKeyResolutionMethodIsMissing() throws Exception {
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer(ISSUER);
        String jwt = signJwt(trustedPrivateKey, "user-123", null);

        resolver().resolve(jwt, issuer).test()
                .assertError(IllegalArgumentException.class);
    }

    @Test
    void shouldVerifyJwtAgainstKeysFetchedFromTheJwksUrl() throws Exception {
        when(jwkSetFetcher.getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES))
                .thenReturn(Maybe.just(new JWKSetFetchResponse(jwks("kid-1"), null)));
        String jwt = signJwt(trustedPrivateKey, "user-123", "kid-1");

        resolver().resolve(jwt, jwksIssuer(JWKS_URL)).test()
                .assertNoErrors()
                .assertValue(claims -> "user-123".equals(claims.getSubject()));
    }

    @Test
    void shouldRefuseJwksUrlResolvingToAPrivateAddress() throws Exception {
        String jwt = signJwt(trustedPrivateKey, "user-123", "kid-1");

        resolver().resolve(jwt, jwksIssuer("https://10.0.0.5/keys")).test()
                .assertError(SecurityException.class)
                .assertError(error -> error.getMessage().contains("Refused to fetch JWKS"));
        verify(jwkSetFetcher, never()).getKeys(anyString(), anyLong());
    }

    @Test
    void shouldRefuseUnsecuredJwksUrl() throws Exception {
        String jwt = signJwt(trustedPrivateKey, "user-123", "kid-1");

        resolver().resolve(jwt, jwksIssuer("http://example.com/keys")).test()
                .assertError(SecurityException.class)
                .assertError(error -> error.getMessage().contains("Refused to fetch JWKS"));
        verify(jwkSetFetcher, never()).getKeys(anyString(), anyLong());
    }

    @Test
    void shouldFetchPrivateJwksUrlWhenTheDomainPolicyPermitsIt() throws Exception {
        settings.setAllowPrivateIpAddress(true);
        when(jwkSetFetcher.getKeys("https://10.0.0.5/keys", DEFAULT_MAX_RESPONSE_SIZE_BYTES))
                .thenReturn(Maybe.just(new JWKSetFetchResponse(jwks("kid-1"), null)));
        String jwt = signJwt(trustedPrivateKey, "user-123", "kid-1");

        resolver().resolve(jwt, jwksIssuer("https://10.0.0.5/keys")).test()
                .assertNoErrors()
                .assertValue(claims -> "user-123".equals(claims.getSubject()));
    }

    @Test
    void shouldBoundTheJwksResponseWithTheConfiguredMaximumSize() throws Exception {
        settings.setMaxResponseSizeKb(8);
        when(jwkSetFetcher.getKeys(JWKS_URL, 8 * 1024L))
                .thenReturn(Maybe.just(new JWKSetFetchResponse(jwks("kid-1"), null)));
        String jwt = signJwt(trustedPrivateKey, "user-123", "kid-1");

        resolver().resolve(jwt, jwksIssuer(JWKS_URL)).test().assertNoErrors();

        verify(jwkSetFetcher, times(1)).getKeys(JWKS_URL, 8 * 1024L);
    }

    @Test
    void shouldReuseCachedKeysBetweenResolutions() throws Exception {
        when(jwkSetFetcher.getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES))
                .thenReturn(Maybe.just(new JWKSetFetchResponse(jwks("kid-1"), null)));
        TrustedIssuerResolverImpl resolver = resolver();
        TrustedIssuer issuer = jwksIssuer(JWKS_URL);

        resolver.resolve(signJwt(trustedPrivateKey, "user-1", "kid-1"), issuer).test().assertNoErrors();
        resolver.resolve(signJwt(trustedPrivateKey, "user-2", "kid-1"), issuer).test().assertNoErrors();

        verify(jwkSetFetcher, times(1)).getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES);
    }

    @Test
    void shouldServeCachedKeysWhenARefreshFails() throws Exception {
        settings.setCacheTtlSeconds(0);
        when(jwkSetFetcher.getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES))
                .thenReturn(Maybe.just(new JWKSetFetchResponse(jwks("kid-1"), null)))
                .thenReturn(Maybe.error(new RuntimeException("upstream down")));
        TrustedIssuerResolverImpl resolver = resolver();
        TrustedIssuer issuer = jwksIssuer(JWKS_URL);

        resolver.resolve(signJwt(trustedPrivateKey, "user-1", "kid-1"), issuer).test().assertNoErrors();
        resolver.resolve(signJwt(trustedPrivateKey, "user-2", "kid-1"), issuer).test()
                .assertNoErrors()
                .assertValue(claims -> "user-2".equals(claims.getSubject()));

        verify(jwkSetFetcher, times(2)).getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES);
    }

    @Test
    void shouldRefreshKeysWhenTheKidIsNotInTheCachedSet() throws Exception {
        when(jwkSetFetcher.getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES))
                .thenReturn(Maybe.just(new JWKSetFetchResponse(jwks("kid-1"), null)))
                .thenReturn(Maybe.just(new JWKSetFetchResponse(jwks("kid-2"), null)));
        TrustedIssuerResolverImpl resolver = resolver();
        TrustedIssuer issuer = jwksIssuer(JWKS_URL);

        resolver.resolve(signJwt(trustedPrivateKey, "user-1", "kid-1"), issuer).test().assertNoErrors();
        resolver.resolve(signJwt(trustedPrivateKey, "user-2", "kid-2"), issuer).test()
                .assertNoErrors()
                .assertValue(claims -> "user-2".equals(claims.getSubject()));

        verify(jwkSetFetcher, times(2)).getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES);
    }

    @Test
    void shouldRejectJwtWhoseKidIsNotPublishedByTheIssuer() throws Exception {
        when(jwkSetFetcher.getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES))
                .thenReturn(Maybe.just(new JWKSetFetchResponse(jwks("kid-1"), null)));
        String jwt = signJwt(trustedPrivateKey, "user-123", "unknown-kid");

        resolver().resolve(jwt, jwksIssuer(JWKS_URL)).test()
                .assertError(SecurityException.class)
                .assertError(error -> error.getMessage().contains("No signing key"));
    }

    @Test
    void shouldIgnoreEncryptionKeysWhenTheJwtCarriesNoKid() throws Exception {
        when(jwkSetFetcher.getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES))
                .thenReturn(Maybe.just(new JWKSetFetchResponse(jwks("kid-1", "enc"), null)));
        String jwt = signJwt(trustedPrivateKey, "user-123", null);

        resolver().resolve(jwt, jwksIssuer(JWKS_URL)).test()
                .assertError(SecurityException.class)
                .assertError(error -> error.getMessage().contains("signature verification failed"));
    }

    @Test
    void shouldVerifyAgainstASigningKeyWhenTheJwtCarriesNoKid() throws Exception {
        when(jwkSetFetcher.getKeys(JWKS_URL, DEFAULT_MAX_RESPONSE_SIZE_BYTES))
                .thenReturn(Maybe.just(new JWKSetFetchResponse(jwks("kid-1", "sig"), null)));
        String jwt = signJwt(trustedPrivateKey, "user-123", null);

        resolver().resolve(jwt, jwksIssuer(JWKS_URL)).test()
                .assertNoErrors()
                .assertValue(claims -> "user-123".equals(claims.getSubject()));
    }

    // --- helpers -----------------------------------------------------------

    private TrustedIssuerResolverImpl resolver() {
        return new TrustedIssuerResolverImpl(new TrustDomainKeyServiceImpl(jwkSetFetcher, domain), new JWSServiceImpl());
    }

    private static TrustedIssuer pemIssuer(String certificate) {
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer(ISSUER);
        issuer.setKeyResolutionMethod(KeyResolutionMethod.PEM);
        issuer.setCertificate(certificate);
        return issuer;
    }

    private static TrustedIssuer jwksIssuer(String jwksUri) {
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer(ISSUER);
        issuer.setKeyResolutionMethod(KeyResolutionMethod.JWKS_URL);
        issuer.setJwksUri(jwksUri);
        return issuer;
    }

    private static JWKSet jwks(String kid) {
        return jwks(kid, null);
    }

    private static JWKSet jwks(String kid, String use) {
        io.gravitee.am.model.jose.JWK key = JWKConverter.convert(
                new com.nimbusds.jose.jwk.RSAKey.Builder(trustedPublicKey).keyID(kid).build());
        key.setUse(use);
        JWKSet set = new JWKSet();
        set.setKeys(List.of(key));
        return set;
    }

    private static String signJwt(PrivateKey privateKey, String subject, String kid) throws Exception {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(ISSUER)
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .build();
        JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.RS256);
        if (kid != null) {
            headerBuilder.keyID(kid);
        }
        SignedJWT signedJWT = new SignedJWT(headerBuilder.build(), claimsSet);
        signedJWT.sign(new RSASSASigner(privateKey));
        return signedJWT.serialize();
    }

    private static String selfSignedCertPem(KeyPair keyPair) throws Exception {
        X500Name dn = new X500Name("CN=Test");
        Date now = new Date();
        Date notBefore = new Date(now.getTime() - 86_400_000L);
        Date notAfter = new Date(now.getTime() + 365L * 86_400_000L);

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dn, BigInteger.valueOf(now.getTime()), notBefore, notAfter, dn, keyPair.getPublic());

        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProviderSingleton.getInstance())
                .getCertificate(certBuilder.build(
                        new JcaContentSignerBuilder("SHA256WithRSA")
                                .setProvider(BouncyCastleProviderSingleton.getInstance())
                                .build(keyPair.getPrivate())));

        return X509CertUtils.toPEMString(cert);
    }
}
