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
package io.gravitee.am.gateway.handler.common.dpop.impl;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.oauth2.InvalidDPoPProofException;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.jwt.InMemoryJWTCache;
import io.gravitee.am.gateway.handler.common.jwt.JWTCache;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpVersion;
import io.gravitee.gateway.api.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Full RFC 9449 §4.3 failure matrix for {@link DPoPProofValidatorImpl}. Every assertion checks the
 * externally observable outcome (returned thumbprint, or {@link InvalidDPoPProofException}), never
 * internal state.
 *
 * @author GraviteeSource Team
 */
class DPoPProofValidatorImplTest {

    private static final String HOST = "am.example.com";
    private static final String PATH = "/oauth/token";
    private static final String HTU = "https://am.example.com/oauth/token";
    private static final String ACCESS_TOKEN = "an-opaque-or-jwt-access-token";

    private ECKey ecKey;
    private RSAKey rsaKey;
    private JWK ecPublicJwk;
    private JWK rsaPublicJwk;
    private JWSSigner ecSigner;
    private JWSSigner rsaSigner;
    private String ecJkt;
    private String rsaJkt;

    private DPoPProofValidatorImpl validator;

    @BeforeEach
    void setUp() throws Exception {
        ecKey = new ECKeyGenerator(Curve.P_256).keyID("ec-1").generate();
        rsaKey = new RSAKeyGenerator(2048).keyID("rsa-1").generate();
        ecPublicJwk = ecKey.toPublicJWK();
        rsaPublicJwk = rsaKey.toPublicJWK();
        ecSigner = new ECDSASigner(ecKey);
        rsaSigner = new RSASSASigner(rsaKey);
        ecJkt = ecPublicJwk.computeThumbprint().toString();
        rsaJkt = rsaPublicJwk.computeThumbprint().toString();
        validator = new DPoPProofValidatorImpl(30, 3, cache(Duration.ofSeconds(33)));
    }


    @Test
    void token_mode_valid_ec_proof_returns_thumbprint() throws Exception {
        String proof = new ProofBuilder().sign();
        validator.validateForToken(tokenRequest(proof), null).test().assertValue(ecJkt).assertComplete();
    }

    @Test
    void token_mode_valid_rsa_proof_returns_thumbprint() throws Exception {
        String proof = new ProofBuilder().alg(JWSAlgorithm.RS256).headerJwk(rsaPublicJwk).signer(rsaSigner).sign();
        validator.validateForToken(tokenRequest(proof), null).test().assertValue(rsaJkt).assertComplete();
    }

    @Test
    void resource_mode_valid_proof_with_matching_ath_and_jkt_passes() throws Exception {
        String proof = new ProofBuilder().ath(ath(ACCESS_TOKEN)).sign();
        validator.validateForResource(tokenRequest(proof), ACCESS_TOKEN, ecJkt).test().assertValue(ecJkt).assertComplete();
    }

    @Test
    void iat_within_skew_future_is_accepted() throws Exception {
        String proof = new ProofBuilder().iat(Date.from(Instant.now().plusSeconds(2))).sign();
        validator.validateForToken(tokenRequest(proof), null).test().assertValue(ecJkt);
    }


    @Test
    void bad_signature_is_rejected() throws Exception {
        ECKey other = new ECKeyGenerator(Curve.P_256).generate();
        String proof = new ProofBuilder().headerJwk(other.toPublicJWK()).signer(ecSigner).sign();
        validator.validateForToken(tokenRequest(proof), null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void alg_none_is_rejected() throws Exception {
        String unsecured = new PlainJWT(new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issueTime(new Date())
                .claim("htm", "POST")
                .claim("htu", HTU)
                .build()).serialize();
        validator.validateForToken(tokenRequest(unsecured), null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void symmetric_alg_is_rejected() throws Exception {
        OctetSequenceKey oct = new OctetSequenceKeyGenerator(256).generate();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType(DPoPProofValidatorImpl.DPOP_JWT_TYPE)).build(),
                defaultClaims().build());
        jwt.sign(new MACSigner(oct));
        validator.validateForToken(tokenRequest(jwt.serialize()), null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void disallowed_asymmetric_alg_ps256_is_rejected() throws Exception {
        String proof = new ProofBuilder().alg(JWSAlgorithm.PS256).headerJwk(rsaPublicJwk).signer(rsaSigner).sign();
        validator.validateForToken(tokenRequest(proof), null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void private_key_material_in_jwk_header_is_rejected() throws Exception {
        String[] parts = new ProofBuilder().sign().split("\\.");
        Map<String, Object> header = JSONObjectUtils.parse(Base64URL.from(parts[0]).decodeToString());
        header.put("jwk", ecKey.toJSONObject());
        String tampered = Base64URL.encode(JSONObjectUtils.toJSONString(header)) + "." + parts[1] + "." + parts[2];
        validator.validateForToken(tokenRequest(tampered), null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void missing_jwk_header_is_rejected() throws Exception {
        String proof = new ProofBuilder().headerJwk(null).signer(ecSigner).sign();
        validator.validateForToken(tokenRequest(proof), null).test().assertError(InvalidDPoPProofException.class);
    }


    @Test
    void wrong_typ_is_rejected() throws Exception {
        String proof = new ProofBuilder().typ(new JOSEObjectType("jwt")).sign();
        validator.validateForToken(tokenRequest(proof), null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void more_than_one_dpop_header_is_rejected() throws Exception {
        String proof = new ProofBuilder().sign();
        validator.validateForToken(tokenRequest(proof, proof), null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void missing_dpop_header_is_rejected() {
        validator.validateForToken(tokenRequest(), null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void malformed_proof_jwt_is_rejected() {
        validator.validateForToken(tokenRequest("this.is.not-a-jwt"), null).test().assertError(InvalidDPoPProofException.class);
    }


    @Test
    void wrong_htm_is_rejected() throws Exception {
        String proof = new ProofBuilder().htm("GET").sign();
        validator.validateForToken(tokenRequest(proof), null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void wrong_htu_is_rejected() throws Exception {
        String proof = new ProofBuilder().htu("https://am.example.com/somewhere-else").sign();
        validator.validateForToken(tokenRequest(proof), null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void htu_matches_only_after_forwarded_header_resolution() throws Exception {
        String proof = new ProofBuilder().htu(HTU).sign();
        validator.validateForToken(forwardedRequest(proof), null).test().assertValue(ecJkt).assertComplete();
    }

    @Test
    void htu_equal_to_internal_url_is_rejected_when_forwarded() throws Exception {
        String proof = new ProofBuilder().htu("http://10.0.0.5:8092/oauth/token").sign();
        validator.validateForToken(forwardedRequest(proof), null).test().assertError(InvalidDPoPProofException.class);
    }


    @Test
    void iat_too_old_is_rejected() throws Exception {
        String proof = new ProofBuilder().iat(Date.from(Instant.now().minusSeconds(60))).sign();
        validator.validateForToken(tokenRequest(proof), null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void iat_beyond_skew_in_the_future_is_rejected() throws Exception {
        String proof = new ProofBuilder().iat(Date.from(Instant.now().plusSeconds(30))).sign();
        validator.validateForToken(tokenRequest(proof), null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void missing_iat_is_rejected() throws Exception {
        String proof = new ProofBuilder().iat(null).sign();
        validator.validateForToken(tokenRequest(proof), null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void missing_jti_is_rejected() throws Exception {
        String proof = new ProofBuilder().jti(null).sign();
        validator.validateForToken(tokenRequest(proof), null).test().assertError(InvalidDPoPProofException.class);
    }


    @Test
    void resource_mode_missing_ath_is_rejected() throws Exception {
        String proof = new ProofBuilder().sign();
        validator.validateForResource(tokenRequest(proof), ACCESS_TOKEN, ecJkt).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void resource_mode_ath_mismatch_is_rejected() throws Exception {
        String proof = new ProofBuilder().ath(ath("a-different-token")).sign();
        validator.validateForResource(tokenRequest(proof), ACCESS_TOKEN, ecJkt).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void resource_mode_jkt_not_matching_token_binding_is_rejected() throws Exception {
        String proof = new ProofBuilder().ath(ath(ACCESS_TOKEN)).sign();
        validator.validateForResource(tokenRequest(proof), ACCESS_TOKEN, "a-different-thumbprint").test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void resource_mode_requires_access_token_and_binding() throws Exception {
        String proof = new ProofBuilder().ath(ath(ACCESS_TOKEN)).sign();
        validator.validateForResource(tokenRequest(proof), null, ecJkt).test().assertError(InvalidDPoPProofException.class);
        validator.validateForResource(tokenRequest(proof), ACCESS_TOKEN, null).test().assertError(InvalidDPoPProofException.class);
    }


    @Test
    void duplicate_jti_within_window_is_rejected_on_same_node() throws Exception {
        String proof = new ProofBuilder().jti("fixed-jti").sign();
        validator.validateForToken(tokenRequest(proof), null).test().assertValue(ecJkt);
        validator.validateForToken(tokenRequest(proof), null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void replay_entry_expires_after_the_acceptance_window() throws Exception {
        DPoPProofValidatorImpl shortLived = new DPoPProofValidatorImpl(30, 3, cache(Duration.ofMillis(150)));
        String proof = new ProofBuilder().jti("expiring-jti").sign();

        shortLived.validateForToken(tokenRequest(proof), null).test().assertValue(ecJkt);
        shortLived.validateForToken(tokenRequest(proof), null).test().assertError(InvalidDPoPProofException.class);

        Thread.sleep(350);

        shortLived.validateForToken(tokenRequest(proof), null).test().assertValue(ecJkt).assertComplete();
    }


    @Test
    void refresh_mode_valid_proof_matching_jkt_returns_thumbprint() throws Exception {
        String proof = new ProofBuilder().sign();
        validator.validateForRefresh(tokenRequest(proof), ecJkt, null).test().assertValue(ecJkt).assertComplete();
    }

    @Test
    void refresh_mode_thumbprint_mismatch_is_rejected() throws Exception {
        String proof = new ProofBuilder().sign();
        validator.validateForRefresh(tokenRequest(proof), "a-different-thumbprint", null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void refresh_mode_does_not_require_ath() throws Exception {
        String proof = new ProofBuilder().sign();
        validator.validateForRefresh(tokenRequest(proof), ecJkt, null).test().assertValue(ecJkt).assertComplete();
    }

    @Test
    void refresh_mode_ignores_ath_when_present() throws Exception {
        String proof = new ProofBuilder().ath(ath("some-unrelated-token")).sign();
        validator.validateForRefresh(tokenRequest(proof), ecJkt, null).test().assertValue(ecJkt).assertComplete();
    }

    @Test
    void refresh_mode_requires_expected_thumbprint() throws Exception {
        String proof = new ProofBuilder().sign();
        validator.validateForRefresh(tokenRequest(proof), null, null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void refresh_mode_bad_signature_is_rejected() throws Exception {
        ECKey other = new ECKeyGenerator(Curve.P_256).generate();
        String proof = new ProofBuilder().headerJwk(other.toPublicJWK()).signer(ecSigner).sign();
        validator.validateForRefresh(tokenRequest(proof), ecJkt, null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void refresh_mode_alg_none_is_rejected() throws Exception {
        String unsecured = new PlainJWT(new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issueTime(new Date())
                .claim("htm", "POST")
                .claim("htu", HTU)
                .build()).serialize();
        validator.validateForRefresh(tokenRequest(unsecured), ecJkt, null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void refresh_mode_wrong_htu_is_rejected() throws Exception {
        String proof = new ProofBuilder().htu("https://am.example.com/somewhere-else").sign();
        validator.validateForRefresh(tokenRequest(proof), ecJkt, null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void refresh_mode_iat_too_old_is_rejected() throws Exception {
        String proof = new ProofBuilder().iat(Date.from(Instant.now().minusSeconds(60))).sign();
        validator.validateForRefresh(tokenRequest(proof), ecJkt, null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void refresh_mode_missing_jti_is_rejected() throws Exception {
        String proof = new ProofBuilder().jti(null).sign();
        validator.validateForRefresh(tokenRequest(proof), ecJkt, null).test().assertError(InvalidDPoPProofException.class);
    }

    @Test
    void refresh_mode_duplicate_jti_within_window_is_rejected_on_same_node() throws Exception {
        String proof = new ProofBuilder().jti("refresh-fixed-jti").sign();
        validator.validateForRefresh(tokenRequest(proof), ecJkt, null).test().assertValue(ecJkt);
        validator.validateForRefresh(tokenRequest(proof), ecJkt, null).test().assertError(InvalidDPoPProofException.class);
    }


    @Test
    void token_mode_alg_inside_domain_allowlist_is_accepted() throws Exception {
        String proof = new ProofBuilder().sign();
        validator.validateForToken(tokenRequest(proof), Set.of("ES256", "ES384")).test().assertValue(ecJkt).assertComplete();
    }

    @Test
    void token_mode_alg_outside_domain_allowlist_is_rejected() throws Exception {
        String proof = new ProofBuilder().alg(JWSAlgorithm.RS256).headerJwk(rsaPublicJwk).signer(rsaSigner).sign();
        validator.validateForToken(tokenRequest(proof), Set.of("ES256", "ES384", "ES512")).test()
                .assertError(InvalidDPoPProofException.class);
    }

    @Test
    void refresh_mode_alg_inside_domain_allowlist_is_accepted() throws Exception {
        String proof = new ProofBuilder().sign();
        validator.validateForRefresh(tokenRequest(proof), ecJkt, Set.of("ES256")).test().assertValue(ecJkt).assertComplete();
    }

    @Test
    void refresh_mode_alg_outside_domain_allowlist_is_rejected() throws Exception {
        String proof = new ProofBuilder().alg(JWSAlgorithm.RS256).headerJwk(rsaPublicJwk).signer(rsaSigner).sign();
        validator.validateForRefresh(tokenRequest(proof), rsaJkt, Set.of("ES256")).test()
                .assertError(InvalidDPoPProofException.class);
    }

    @Test
    void resource_mode_accepts_base_set_alg_regardless_of_narrower_allowlist() throws Exception {
        String proof = new ProofBuilder().alg(JWSAlgorithm.RS256).headerJwk(rsaPublicJwk).signer(rsaSigner)
                .ath(ath(ACCESS_TOKEN)).sign();
        validator.validateForResource(tokenRequest(proof), ACCESS_TOKEN, rsaJkt).test().assertValue(rsaJkt).assertComplete();
    }


    private static JWTCache cache(Duration ttl) {
        return InMemoryJWTCache.builder().maxSize(100).expireAfterWrite(ttl).build();
    }

    private static String ath(String token) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return Base64URL.encode(digest.digest(token.getBytes(StandardCharsets.US_ASCII))).toString();
    }

    private static JWTClaimsSet.Builder defaultClaims() {
        return new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issueTime(new Date())
                .claim("htm", "POST")
                .claim("htu", HTU);
    }

    private Request tokenRequest(String... proofs) {
        return request("POST", "https", Map.of(HttpHeaders.HOST, HOST), PATH, List.of(proofs));
    }

    private Request forwardedRequest(String... proofs) {
        return request("POST", "http",
                Map.of(HttpHeaders.HOST, "10.0.0.5:8092",
                        HttpHeaders.X_FORWARDED_PROTO, "https",
                        HttpHeaders.X_FORWARDED_HOST, HOST),
                PATH, List.of(proofs));
    }

    private Request request(String method, String scheme, Map<String, String> headers, String path, List<String> proofs) {
        Request request = mock(Request.class);
        when(request.method()).thenReturn(HttpMethod.valueOf(method));
        when(request.scheme()).thenReturn(scheme);
        when(request.path()).thenReturn(path);
        when(request.uri()).thenReturn(path);
        when(request.host()).thenReturn(headers.get(HttpHeaders.HOST));
        when(request.version()).thenReturn(HttpVersion.HTTP_1_1);
        final io.gravitee.gateway.api.http.HttpHeaders h = io.gravitee.gateway.api.http.HttpHeaders.create();
        headers.forEach(h::set);
        proofs.forEach(p -> h.add(ConstantKeys.DPOP_PROOF_HEADER, p));
        when(request.headers()).thenReturn(h);
        return request;
    }

    /**
     * Fluent builder for DPoP proof JWTs. Defaults produce a valid EC (ES256) token-endpoint proof;
     * setting any field to {@code null} omits it (to exercise missing-claim cases).
     */
    private final class ProofBuilder {
        private JWSAlgorithm alg = JWSAlgorithm.ES256;
        private JOSEObjectType typ = new JOSEObjectType(DPoPProofValidatorImpl.DPOP_JWT_TYPE);
        private JWK headerJwk = ecPublicJwk;
        private JWSSigner signer = ecSigner;
        private String htm = "POST";
        private String htu = HTU;
        private Date iat = new Date();
        private String jti = UUID.randomUUID().toString();
        private String ath;

        private ProofBuilder alg(JWSAlgorithm alg) { this.alg = alg; return this; }
        private ProofBuilder typ(JOSEObjectType typ) { this.typ = typ; return this; }
        private ProofBuilder headerJwk(JWK jwk) { this.headerJwk = jwk; return this; }
        private ProofBuilder signer(JWSSigner signer) { this.signer = signer; return this; }
        private ProofBuilder htm(String htm) { this.htm = htm; return this; }
        private ProofBuilder htu(String htu) { this.htu = htu; return this; }
        private ProofBuilder iat(Date iat) { this.iat = iat; return this; }
        private ProofBuilder jti(String jti) { this.jti = jti; return this; }
        private ProofBuilder ath(String ath) { this.ath = ath; return this; }

        private String sign() throws Exception {
            JWSHeader.Builder header = new JWSHeader.Builder(alg);
            if (typ != null) {
                header.type(typ);
            }
            if (headerJwk != null) {
                header.jwk(headerJwk);
            }
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();
            if (htm != null) {
                claims.claim("htm", htm);
            }
            if (htu != null) {
                claims.claim("htu", htu);
            }
            if (iat != null) {
                claims.issueTime(iat);
            }
            if (jti != null) {
                claims.jwtID(jti);
            }
            if (ath != null) {
                claims.claim("ath", ath);
            }
            SignedJWT jwt = new SignedJWT(header.build(), claims.build());
            jwt.sign(signer);
            return jwt.serialize();
        }
    }
}
