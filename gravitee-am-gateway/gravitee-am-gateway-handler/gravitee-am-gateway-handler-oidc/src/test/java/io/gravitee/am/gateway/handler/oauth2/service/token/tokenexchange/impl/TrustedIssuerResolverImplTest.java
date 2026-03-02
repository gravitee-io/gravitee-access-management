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
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.certificate.api.X509CertUtils;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.model.KeyResolutionMethod;
import io.gravitee.am.model.TrustedIssuer;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Unit tests for {@link TrustedIssuerResolverImpl}.
 * Tests PEM-based verification with dynamically generated RSA keys and certificates.
 */
public class TrustedIssuerResolverImplTest {

    private static String trustedCertPem;
    private static PrivateKey trustedPrivateKey;
    private static PrivateKey untrustedPrivateKey;

    @BeforeClass
    public static void generateKeys() throws Exception {
        // Generate trusted key pair + self-signed certificate
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair trustedKeyPair = keyGen.generateKeyPair();
        trustedPrivateKey = trustedKeyPair.getPrivate();
        trustedCertPem = generateSelfSignedCertPem(trustedKeyPair);

        // Generate untrusted key pair (different from trusted)
        KeyPair untrustedKeyPair = keyGen.generateKeyPair();
        untrustedPrivateKey = untrustedKeyPair.getPrivate();
    }

    private static String generateSelfSignedCertPem(KeyPair keyPair) throws Exception {
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

    private TrustedIssuerResolverImpl resolver;

    @Before
    public void setUp() {
        resolver = new TrustedIssuerResolverImpl();
    }

    @Test
    public void shouldVerifyJwtWithValidPemCertificate() throws Exception {
        TrustedIssuer issuer = pemIssuer("https://trusted.example.com", trustedCertPem);

        String jwt = signJwt(trustedPrivateKey, "https://trusted.example.com", "user-123");

        JWTClaimsSet claims = resolver.resolve(jwt, issuer);

        assertNotNull(claims);
        assertEquals("user-123", claims.getSubject());
        assertEquals("https://trusted.example.com", claims.getIssuer());
    }

    @Test(expected = InvalidGrantException.class)
    public void shouldRejectJwtSignedWithWrongKey() throws Exception {
        TrustedIssuer issuer = pemIssuer("https://trusted.example.com", trustedCertPem);

        // Sign with untrusted key but claim trusted issuer
        String jwt = signJwt(untrustedPrivateKey, "https://trusted.example.com", "user-123");

        resolver.resolve(jwt, issuer);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowOnInvalidPemCertificate() throws Exception {
        TrustedIssuer issuer = pemIssuer("https://bad.example.com", "not-a-valid-pem");

        String jwt = signJwt(trustedPrivateKey, "https://bad.example.com", "user-123");

        resolver.resolve(jwt, issuer);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowOnUnsupportedKeyResolutionMethod() throws Exception {
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://unsupported.example.com");
        issuer.setKeyResolutionMethod(null);

        String jwt = signJwt(trustedPrivateKey, "https://unsupported.example.com", "user-123");

        resolver.resolve(jwt, issuer);
    }

    @Test
    public void shouldCacheProcessorForSameIssuer() throws Exception {
        TrustedIssuer issuer = pemIssuer("https://cached.example.com", trustedCertPem);

        String jwt1 = signJwt(trustedPrivateKey, "https://cached.example.com", "user-1");
        String jwt2 = signJwt(trustedPrivateKey, "https://cached.example.com", "user-2");

        JWTClaimsSet claims1 = resolver.resolve(jwt1, issuer);
        JWTClaimsSet claims2 = resolver.resolve(jwt2, issuer);

        assertEquals("user-1", claims1.getSubject());
        assertEquals("user-2", claims2.getSubject());
    }

    @Test(expected = InvalidGrantException.class)
    public void shouldRejectMalformedTokenString() {
        TrustedIssuer issuer = pemIssuer("https://trusted.example.com", trustedCertPem);

        // T5: Completely malformed token (not a valid JWT format)
        resolver.resolve("not-a-jwt-at-all", issuer);
    }

    // --- Helpers ---

    private static TrustedIssuer pemIssuer(String issuerUrl, String certificate) {
        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer(issuerUrl);
        ti.setKeyResolutionMethod(KeyResolutionMethod.PEM);
        ti.setCertificate(certificate);
        return ti;
    }

    private static String signJwt(PrivateKey privateKey, String issuer, String subject) throws Exception {
        JWSSigner signer = new RSASSASigner(privateKey);
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .build();
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }
}
