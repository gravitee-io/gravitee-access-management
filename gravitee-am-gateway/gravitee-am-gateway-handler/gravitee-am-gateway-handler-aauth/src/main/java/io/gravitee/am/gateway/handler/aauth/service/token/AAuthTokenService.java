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
import io.gravitee.am.model.oidc.Client;
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

    private static final String AUTH_TOKEN_TYP = "aa-auth+jwt";
    private static final long MAX_AUTH_TOKEN_LIFETIME_SECONDS = 3600; // 1 hour per spec
    private static final long DEFAULT_AUTH_TOKEN_LIFETIME_SECONDS = 300; // 5 minutes

    private final CertificateManager certificateManager;
    private final int authTokenLifespan;

    /**
     * Create and sign an auth token for a machine-to-machine (scope-only) grant.
     */
    public Single<AAuthTokenResponse> createAuthToken(ResourceTokenClaims resourceTokenClaims,
                                                       VerificationResult agentVerification,
                                                       String psIssuerUrl) {
        return createAuthToken(resourceTokenClaims, agentVerification, psIssuerUrl, null, null);
    }

    /**
     * Create and sign an auth token, optionally with a user binding ({@code sub} claim).
     * Uses the client's certificate for signing (same pattern as OIDC ID token).
     */
    public Single<AAuthTokenResponse> createAuthToken(ResourceTokenClaims rtClaims,
                                                       VerificationResult agentVerif,
                                                       String psIssuerUrl,
                                                       String sub) {
        return createAuthToken(rtClaims, agentVerif, psIssuerUrl, sub, null);
    }

    /**
     * Create and sign an auth token with optional user binding and client certificate selection.
     */
    public Single<AAuthTokenResponse> createAuthToken(ResourceTokenClaims rtClaims,
                                                       VerificationResult agentVerif,
                                                       String psIssuerUrl,
                                                       String sub,
                                                       Client client) {
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

        if (sub != null) {
            jwt.setSub(sub);
        }
        if (rtClaims.scope() != null && !rtClaims.scope().isBlank()) {
            jwt.put("scope", rtClaims.scope());
        }

        if (sub == null && (rtClaims.scope() == null || rtClaims.scope().isBlank())) {
            log.warn("Auth token has neither sub nor scope — spec requires at least one");
        }

        // Sign with typ=aa-auth+jwt. Use the same certificate selection as OIDC ID tokens:
        // 1. If client has a configured certificate → use it
        // 2. Otherwise findByAlgorithm("RS256") → first asymmetric key in the JWKS
        // 3. Fall back to default (HMAC) only as last resort
        var certSingle = client != null
                ? certificateManager.getClientCertificateProvider(client, false)
                : certificateManager.findByAlgorithm("RS256")
                        .switchIfEmpty(certificateManager.findByAlgorithm("ES256"))
                        .switchIfEmpty(io.reactivex.rxjava3.core.Maybe.defer(() -> {
                            log.warn("No asymmetric certificate found for AAUTH auth token signing. "
                                    + "Auth tokens signed with symmetric keys cannot be verified via JWKS.");
                            return io.reactivex.rxjava3.core.Maybe.just(certificateManager.defaultCertificateProvider());
                        }))
                        .toSingle();

        return certSingle.map(certProvider -> {
            String signedToken = certProvider.getJwtBuilder().sign(jwt, AUTH_TOKEN_TYP);
            return new AAuthTokenResponse(signedToken, expiresIn);
        });
    }

    /**
     * Convert a {@link PublicKey} to a JWK-compatible map for the {@code cnf.jwk} claim.
     */
    private Map<String, Object> publicKeyToJwk(PublicKey publicKey) {
        var jwk = new LinkedHashMap<String, Object>();
        if (publicKey instanceof EdECPublicKey) {
            jwk.put("kty", "OKP");
            jwk.put("crv", "Ed25519");
            // Extract the raw 32-byte Ed25519 key from the X.509 encoded form
            byte[] encoded = publicKey.getEncoded();
            byte[] rawKey = new byte[32];
            System.arraycopy(encoded, encoded.length - 32, rawKey, 0, 32);
            jwk.put("x", Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey));
        } else if (publicKey instanceof ECPublicKey ecKey) {
            jwk.put("kty", "EC");
            jwk.put("crv", "P-256");
            byte[] x = ecKey.getW().getAffineX().toByteArray();
            byte[] y = ecKey.getW().getAffineY().toByteArray();
            // Ensure 32-byte fixed-length encoding
            jwk.put("x", Base64.getUrlEncoder().withoutPadding().encodeToString(toFixedLength(x, 32)));
            jwk.put("y", Base64.getUrlEncoder().withoutPadding().encodeToString(toFixedLength(y, 32)));
        } else {
            throw new IllegalArgumentException("Unsupported public key type: " + publicKey.getClass());
        }
        return jwk;
    }

    private byte[] toFixedLength(byte[] value, int length) {
        if (value.length == length) return value;
        byte[] result = new byte[length];
        if (value.length > length) {
            System.arraycopy(value, value.length - length, result, 0, length);
        } else {
            System.arraycopy(value, 0, result, length - value.length, value.length);
        }
        return result;
    }
}
