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
package io.gravitee.am.identityprovider.api.trustedissuer;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;

class IdJagAssertionVerifierTest {

    private static final String ISSUER = "https://idp.example.com";
    private static final JOSEObjectType ID_JAG = new JOSEObjectType("oauth-id-jag+jwt");

    private static RSAKey rsaKey;
    private static ECKey ecKey;
    private static JWKSource<SecurityContext> jwks;

    @BeforeAll
    static void generateKeys() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("rsa-1").generate();
        ecKey = new ECKeyGenerator(Curve.P_256).keyID("ec-1").generate();
        jwks = new ImmutableJWKSet<>(new JWKSet(List.of(rsaKey.toPublicJWK(), ecKey.toPublicJWK())));
    }

    @Test
    void shouldAcceptRs256AssertionSignedByAKeyInTheJwks() throws Exception {
        String assertion = sign(rs256Header(), claims().build(), new RSASSASigner(rsaKey));

        new IdJagAssertionVerifier(ISSUER, jwks).verify(assertion).test()
                .awaitDone(5, SECONDS)
                .assertValue(claims -> "alice".equals(claims.getSubject()));
    }

    @Test
    void shouldAcceptEs256AssertionWhenTheKeyIsInTheJwks() throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).type(ID_JAG).keyID("ec-1").build();
        String assertion = sign(header, claims().build(), new ECDSASigner(ecKey));

        new IdJagAssertionVerifier(ISSUER, jwks).verify(assertion).test()
                .awaitDone(5, SECONDS)
                .assertValue(claims -> "alice".equals(claims.getSubject()));
    }

    @Test
    void shouldAcceptAssertionWithoutKidWhenAKeyInTheJwksVerifiesIt() throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(ID_JAG).build();

        verify(sign(header, claims().build(), new RSASSASigner(rsaKey)))
                .assertValue(claims -> "alice".equals(claims.getSubject()));
    }

    @Test
    void shouldAcceptAssertionExpiredWithinTheDefaultClockSkew() throws Exception {
        JWTClaimsSet claims = claims().expirationTime(new Date(System.currentTimeMillis() - 30_000)).build();

        verify(sign(rs256Header(), claims, new RSASSASigner(rsaKey)))
                .assertValue(c -> "alice".equals(c.getSubject()));
    }

    @Test
    void shouldRefuseAssertionWithoutTyp() throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa-1").build();

        verify(sign(header, claims().build(), new RSASSASigner(rsaKey))).assertError(BadJOSEException.class);
    }

    @Test
    void shouldRefuseJwtTyp() throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID("rsa-1").build();

        verify(sign(header, claims().build(), new RSASSASigner(rsaKey))).assertError(BadJOSEException.class);
    }

    @Test
    void shouldRefuseHmacAlgorithm() throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).type(ID_JAG).keyID("rsa-1").build();
        byte[] secret = new byte[32];

        verify(sign(header, claims().build(), new MACSigner(secret))).assertError(BadJOSEException.class);
    }

    @Test
    void shouldRefuseUnknownKid() throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(ID_JAG).keyID("rsa-unknown").build();

        verify(sign(header, claims().build(), new RSASSASigner(rsaKey))).assertError(BadJOSEException.class);
    }

    @Test
    void shouldRefuseIssuerMismatch() throws Exception {
        JWTClaimsSet claims = claims().issuer("https://other.example.com").build();

        verify(sign(rs256Header(), claims, new RSASSASigner(rsaKey))).assertError(BadJOSEException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sub", "aud", "client_id", "exp", "iat", "jti"})
    void shouldRefuseMissingRequiredClaim(String claim) throws Exception {
        JWTClaimsSet claims = claims().claim(claim, null).build();

        verify(sign(rs256Header(), claims, new RSASSASigner(rsaKey))).assertError(BadJOSEException.class);
    }

    @Test
    void shouldRefuseExpiredAssertion() throws Exception {
        JWTClaimsSet claims = claims().expirationTime(new Date(System.currentTimeMillis() - 600_000)).build();

        verify(sign(rs256Header(), claims, new RSASSASigner(rsaKey))).assertError(BadJOSEException.class);
    }

    @Test
    void shouldRefuseNotYetValidAssertion() throws Exception {
        JWTClaimsSet claims = claims().notBeforeTime(new Date(System.currentTimeMillis() + 600_000)).build();

        verify(sign(rs256Header(), claims, new RSASSASigner(rsaKey))).assertError(BadJOSEException.class);
    }

    @Test
    void shouldRefuseMalformedAssertion() {
        verify("not-a-jwt").assertError(ParseException.class);
    }

    @Test
    void shouldResolveRotatedKidThroughTheSameSourceWithoutRebuild() throws Exception {
        RSAKey rotatedKey = new RSAKeyGenerator(2048).keyID("rsa-2").generate();
        AtomicReference<JWKSet> published = new AtomicReference<>(new JWKSet(rsaKey.toPublicJWK()));
        JWKSource<SecurityContext> liveSource = (selector, context) -> selector.select(published.get());
        IdJagAssertionVerifier verifier = new IdJagAssertionVerifier(ISSUER, liveSource);
        JWSHeader rotatedHeader = new JWSHeader.Builder(JWSAlgorithm.RS256).type(ID_JAG).keyID("rsa-2").build();
        String beforeRotation = sign(rs256Header(), claims().build(), new RSASSASigner(rsaKey));
        String afterRotation = sign(rotatedHeader, claims().build(), new RSASSASigner(rotatedKey));

        verifier.verify(beforeRotation).test().awaitDone(5, SECONDS).assertNoErrors();
        published.set(new JWKSet(rotatedKey.toPublicJWK()));

        verifier.verify(afterRotation).test().awaitDone(5, SECONDS).assertNoErrors();
        verifier.verify(beforeRotation).test().awaitDone(5, SECONDS).assertError(BadJOSEException.class);
    }

    private static TestObserver<JWTClaimsSet> verify(String assertion) {
        return new IdJagAssertionVerifier(ISSUER, jwks).verify(assertion).test().awaitDone(5, SECONDS);
    }

    private static JWSHeader rs256Header() {
        return new JWSHeader.Builder(JWSAlgorithm.RS256).type(ID_JAG).keyID("rsa-1").build();
    }

    private static JWTClaimsSet.Builder claims() {
        long now = System.currentTimeMillis();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("alice")
                .audience("https://rs.example.com")
                .claim("client_id", "client-1")
                .expirationTime(new Date(now + 300_000))
                .issueTime(new Date(now))
                .jwtID(UUID.randomUUID().toString());
    }

    private static String sign(JWSHeader header, JWTClaimsSet claims, JWSSigner signer) throws Exception {
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(signer);
        return jwt.serialize();
    }
}
