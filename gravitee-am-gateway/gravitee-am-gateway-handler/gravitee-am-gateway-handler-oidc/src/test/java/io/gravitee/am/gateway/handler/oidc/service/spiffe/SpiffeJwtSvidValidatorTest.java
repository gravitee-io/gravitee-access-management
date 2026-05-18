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
package io.gravitee.am.gateway.handler.oidc.service.spiffe;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.model.application.SpiffeApplicationSettings;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpiffeJwtSvidValidatorTest {

    private static final String TRUST_DOMAIN = "example.org";
    private static final String SUBJECT = "spiffe://example.org/ns/default/sa/agent";
    private static final String TOKEN_ENDPOINT = "https://am.example.org/oauth/token";

    private static RSAPrivateKey rsaPrivateKey;

    private SpiffeDomainSettings domainSettings;
    private TrustDomain trustDomain;
    private SpiffeApplicationSettings appSettings;
    private SpiffeJwtSvidValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        rsaPrivateKey = (RSAPrivateKey) pair.getPrivate();

        domainSettings = new SpiffeDomainSettings();
        trustDomain = TrustDomain.builder().name(TRUST_DOMAIN).build();
        appSettings = new SpiffeApplicationSettings();
        appSettings.setTrustDomain(TRUST_DOMAIN);
        appSettings.setSubject(SUBJECT);
        validator = new SpiffeJwtSvidValidator(domainSettings);
    }

    @Test
    void validate_returnsNull_onWellFormedSvid() throws Exception {
        SignedJWT jwt = signedJwt(defaultClaims().build(), JWSAlgorithm.RS256);

        String result = validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT);

        assertThat(result).isNull();
    }

    @Test
    void validate_returnsReason_whenJwtNull() {
        assertThat(validator.validate(null, trustDomain, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("missing input");
    }

    @Test
    void validate_returnsReason_whenTrustDomainNull() throws Exception {
        SignedJWT jwt = signedJwt(defaultClaims().build(), JWSAlgorithm.RS256);
        assertThat(validator.validate(jwt, null, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("missing input");
    }

    @Test
    void validate_rejectsForbiddenAlgorithm_hs256() throws Exception {
        byte[] secret = new byte[32];
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), defaultClaims().build());
        jwt.sign(new MACSigner(secret));

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("forbidden algorithm: HS256");
    }

    @Test
    void validate_rejectsAlgorithm_notInAllowList() throws Exception {
        trustDomain.setAllowedAlgorithms(List.of("ES256"));
        SignedJWT jwt = signedJwt(defaultClaims().build(), JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("algorithm not allowed: RS256");
    }

    @Test
    void validate_usesDomainDefaults_whenTrustDomainAllowListEmpty() throws Exception {
        trustDomain.setAllowedAlgorithms(List.of());
        SignedJWT jwt = signedJwt(defaultClaims().build(), JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT)).isNull();
    }

    @Test
    void validate_rejectsSubject_notSpiffeId() throws Exception {
        SignedJWT jwt = signedJwt(defaultClaims().subject("not-a-spiffe-id").build(), JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("sub is not a SPIFFE ID");
    }

    @Test
    void validate_rejectsTrustDomainMismatch() throws Exception {
        SignedJWT jwt = signedJwt(
                defaultClaims().subject("spiffe://other.org/ns/default/sa/agent").build(),
                JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("trust-domain mismatch: other.org");
    }

    @Test
    void validate_isCaseInsensitive_onTrustDomainName() throws Exception {
        trustDomain.setName("Example.ORG");
        SignedJWT jwt = signedJwt(defaultClaims().build(), JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT)).isNull();
    }

    @Test
    void validate_rejects_whenAppSettingsMissing() throws Exception {
        SignedJWT jwt = signedJwt(defaultClaims().build(), JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, null, TOKEN_ENDPOINT))
                .isEqualTo("client missing spiffe settings");
    }

    @Test
    void validate_rejects_whenSubjectDoesNotMatchClient() throws Exception {
        appSettings.setSubject("spiffe://example.org/ns/default/sa/other");
        SignedJWT jwt = signedJwt(defaultClaims().build(), JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("sub does not match client subject");
    }

    @Test
    void validate_rejects_whenClientSubjectBlank() throws Exception {
        appSettings.setSubject("   ");
        SignedJWT jwt = signedJwt(defaultClaims().build(), JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("sub does not match client subject");
    }

    @Test
    void validate_rejects_whenAudienceMissingTokenEndpoint() throws Exception {
        SignedJWT jwt = signedJwt(
                defaultClaims().audience("https://other.example.com/token").build(),
                JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("aud does not contain token endpoint");
    }

    @Test
    void validate_rejects_whenIatMissing() throws Exception {
        SignedJWT jwt = signedJwt(
                new JWTClaimsSet.Builder()
                        .subject(SUBJECT)
                        .audience(TOKEN_ENDPOINT)
                        .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                        .build(),
                JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("iat is required");
    }

    @Test
    void validate_rejects_whenIatInFuture() throws Exception {
        Instant future = Instant.now().plusSeconds(600);
        SignedJWT jwt = signedJwt(
                defaultClaims()
                        .issueTime(Date.from(future))
                        .expirationTime(Date.from(future.plusSeconds(60)))
                        .build(),
                JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("iat in the future");
    }

    @Test
    void validate_rejects_whenExpMissing() throws Exception {
        SignedJWT jwt = signedJwt(
                new JWTClaimsSet.Builder()
                        .subject(SUBJECT)
                        .audience(TOKEN_ENDPOINT)
                        .issueTime(Date.from(Instant.now()))
                        .build(),
                JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("exp is required");
    }

    @Test
    void validate_rejects_whenExpired() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        SignedJWT jwt = signedJwt(
                defaultClaims()
                        .issueTime(Date.from(past))
                        .expirationTime(Date.from(past.plusSeconds(60)))
                        .build(),
                JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("expired");
    }

    @Test
    void validate_rejects_whenLifetimeExceedsMax() throws Exception {
        domainSettings.setMaxJwtLifetimeSeconds(60);
        validator = new SpiffeJwtSvidValidator(domainSettings);

        Instant now = Instant.now();
        SignedJWT jwt = signedJwt(
                defaultClaims()
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(120)))
                        .build(),
                JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("lifetime exceeds max 60s");
    }

    @Test
    void validate_rejects_whenNbfInFuture() throws Exception {
        Instant now = Instant.now();
        SignedJWT jwt = signedJwt(
                defaultClaims()
                        .notBeforeTime(Date.from(now.plusSeconds(600)))
                        .build(),
                JWSAlgorithm.RS256);

        assertThat(validator.validate(jwt, trustDomain, appSettings, TOKEN_ENDPOINT))
                .isEqualTo("nbf in the future");
    }

    @Test
    void trustDomainOf_returnsLowercasedDomain() {
        assertThat(SpiffeJwtSvidValidator.trustDomainOf("spiffe://Example.ORG/foo")).isEqualTo("example.org");
        assertThat(SpiffeJwtSvidValidator.trustDomainOf("spiffe://example.org")).isEqualTo("example.org");
    }

    @Test
    void trustDomainOf_returnsNull_onInvalidInput() {
        assertThat(SpiffeJwtSvidValidator.trustDomainOf(null)).isNull();
        assertThat(SpiffeJwtSvidValidator.trustDomainOf("not-spiffe")).isNull();
        assertThat(SpiffeJwtSvidValidator.trustDomainOf("spiffe://")).isNull();
    }

    @Test
    void isSpiffeId_recognisesPrefix() {
        assertThat(SpiffeJwtSvidValidator.isSpiffeId("spiffe://example.org/foo")).isTrue();
        assertThat(SpiffeJwtSvidValidator.isSpiffeId("https://example.org")).isFalse();
        assertThat(SpiffeJwtSvidValidator.isSpiffeId(null)).isFalse();
    }

    private JWTClaimsSet.Builder defaultClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .subject(SUBJECT)
                .audience(TOKEN_ENDPOINT)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)));
    }

    private static SignedJWT signedJwt(JWTClaimsSet claims, JWSAlgorithm alg) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(alg), claims);
        jwt.sign(new RSASSASigner(rsaPrivateKey));
        return jwt;
    }
}
