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
package io.gravitee.am.gateway.handler.aauth.service.token;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.aauth.model.AAuthTokenResponse;
import io.gravitee.am.gateway.handler.aauth.signing.VerificationResult;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Creates and signs {@code aa-auth+jwt} tokens per AAUTH spec Section 9.4.1.
 * <p>
 * The auth token is signed by AM's domain signing key (via {@link CertificateManager})
 * and binds the agent's public key via the {@code cnf.jwk} claim.
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthTokenService {

    private static final long MAX_AUTH_TOKEN_LIFETIME_SECONDS = 3600; // 1 hour per spec
    private static final long DEFAULT_AUTH_TOKEN_LIFETIME_SECONDS = 300; // 5 minutes

    private final JWTService jwtService;
    private final CertificateManager certificateManager;
    private final int authTokenLifespan;

    /**
     * Create and sign an auth token for a machine-to-machine (scope-only) grant.
     *
     * @param resourceTokenClaims the validated resource token claims
     * @param agentVerification   the agent's signature verification result
     * @return the signed auth token response
     */
    public Single<AAuthTokenResponse> createAuthToken(ResourceTokenClaims resourceTokenClaims,
                                                       VerificationResult agentVerification,
                                                       String psIssuerUrl) {
        return createAuthToken(resourceTokenClaims, agentVerification, psIssuerUrl, null);
    }

    /**
     * Create and sign an auth token, optionally with a user binding ({@code sub} claim).
     *
     * @param rtClaims     the validated resource token claims
     * @param agentVerif   the agent's signature verification result
     * @param psIssuerUrl  this PS's issuer URL
     * @param sub          the user identifier (null for machine-to-machine)
     * @return the signed auth token response
     */
    public Single<AAuthTokenResponse> createAuthToken(ResourceTokenClaims rtClaims,
                                                       VerificationResult agentVerif,
                                                       String psIssuerUrl,
                                                       String sub) {
        long now = Instant.now().getEpochSecond();
        long expiresIn = Math.min(
                authTokenLifespan > 0 ? authTokenLifespan : DEFAULT_AUTH_TOKEN_LIFETIME_SECONDS,
                MAX_AUTH_TOKEN_LIFETIME_SECONDS);
        long exp = now + expiresIn;

        JWT jwt = new JWT();
        jwt.setIss(psIssuerUrl);
        jwt.put("dwk", "aauth-person.json");
        jwt.setAud(rtClaims.iss()); // aud = resource server URL
        jwt.setJti(UUID.randomUUID().toString());
        jwt.put("agent", rtClaims.agent());
        jwt.put("act", Map.of("sub", rtClaims.agent())); // actor claim per RFC 8693
        jwt.put("cnf", Map.of("jwk", publicKeyToJwk(agentVerif.publicKey())));
        jwt.setIat(now);
        jwt.put("exp", exp);

        // At least one of sub or scope MUST be present
        if (sub != null) {
            jwt.setSub(sub);
        }
        if (rtClaims.scope() != null && !rtClaims.scope().isBlank()) {
            jwt.put("scope", rtClaims.scope());
        }

        // If neither sub nor scope, this is invalid per spec — but for m2m, scope should always come from resource_token
        if (sub == null && (rtClaims.scope() == null || rtClaims.scope().isBlank())) {
            log.warn("Auth token has neither sub nor scope — spec requires at least one");
        }

        var certProvider = certificateManager.defaultCertificateProvider();
        return jwtService.encode(jwt, certProvider)
                .map(signedJwt -> new AAuthTokenResponse(signedJwt, expiresIn));
    }

    /**
     * Convert a {@link PublicKey} to a JWK-compatible map for the {@code cnf.jwk} claim.
     */
    private Map<String, Object> publicKeyToJwk(PublicKey publicKey) {
        var jwk = new LinkedHashMap<String, Object>();

        if (publicKey instanceof EdECPublicKey edKey) {
            jwk.put("kty", "OKP");
            jwk.put("crv", "Ed25519");
            // Extract raw 32-byte key from the encoded form
            byte[] encoded = edKey.getEncoded();
            byte[] raw = new byte[32];
            System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);
            jwk.put("x", Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
        } else if (publicKey instanceof ECPublicKey ecKey) {
            jwk.put("kty", "EC");
            jwk.put("crv", "P-256");
            byte[] x = toFixedLength(ecKey.getW().getAffineX().toByteArray(), 32);
            byte[] y = toFixedLength(ecKey.getW().getAffineY().toByteArray(), 32);
            jwk.put("x", Base64.getUrlEncoder().withoutPadding().encodeToString(x));
            jwk.put("y", Base64.getUrlEncoder().withoutPadding().encodeToString(y));
        }

        return jwk;
    }

    private byte[] toFixedLength(byte[] input, int length) {
        if (input.length == length) return input;
        byte[] result = new byte[length];
        if (input.length > length) {
            System.arraycopy(input, input.length - length, result, 0, length);
        } else {
            System.arraycopy(input, 0, result, length - input.length, input.length);
        }
        return result;
    }
}
