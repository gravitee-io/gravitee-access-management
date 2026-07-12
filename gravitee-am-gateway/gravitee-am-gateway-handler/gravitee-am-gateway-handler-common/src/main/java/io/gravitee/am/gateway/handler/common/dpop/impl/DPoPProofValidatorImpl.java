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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.oauth2.InvalidDPoPProofException;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.dpop.DPoPProofValidator;
import io.gravitee.am.gateway.handler.common.jwt.JWTCache;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.gateway.api.Request;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Nimbus-backed implementation of {@link DPoPProofValidator}.
 *
 * <p>The proof is self-verifying: it embeds the public key in its {@code jwk} header, so signature
 * verification and thumbprint computation are done directly against that key without consulting any
 * key store. Replay is bounded by the {@code iat} acceptance window plus the mandatory
 * {@code htm}/{@code htu}/{@code ath}/{@code jkt} bindings, with a node-local best-effort cache
 * keyed on {@code jti} for same-node duplicate detection.</p>
 *
 * @author GraviteeSource Team
 */
public class DPoPProofValidatorImpl implements DPoPProofValidator {

    static final String DPOP_JWT_TYPE = "dpop+jwt";

    private static final Set<JWSAlgorithm> SUPPORTED_ALGORITHMS = Set.of(
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512,
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512);

    private final int validitySeconds;
    private final int clockSkewSeconds;
    private final JWTCache replayCache;

    public DPoPProofValidatorImpl(int validitySeconds, int clockSkewSeconds, JWTCache replayCache) {
        this.validitySeconds = validitySeconds;
        this.clockSkewSeconds = clockSkewSeconds;
        this.replayCache = replayCache;
    }

    @Override
    public Single<String> validateForToken(Request request, Set<String> allowedAlgorithms) {
        return validate(request, null, null, allowedAlgorithms);
    }

    @Override
    public Single<String> validateForResource(Request request, String accessToken, String expectedThumbprint) {
        if (accessToken == null || expectedThumbprint == null) {
            return Single.error(new InvalidDPoPProofException("DPoP resource validation requires the access token and its cnf.jkt binding"));
        }
        return validate(request, accessToken, expectedThumbprint, null);
    }

    @Override
    public Single<String> validateForRefresh(Request request, String expectedThumbprint, Set<String> allowedAlgorithms) {
        if (expectedThumbprint == null) {
            return Single.error(new InvalidDPoPProofException("DPoP refresh validation requires the refresh token cnf.jkt binding"));
        }
        return validate(request, null, expectedThumbprint, allowedAlgorithms);
    }

    private Single<String> validate(Request request, String accessToken, String expectedThumbprint, Set<String> allowedAlgorithms) {
        return Single.defer(() -> {
            final SignedJWT proof = parseSingleProof(request);
            final JWSHeader header = proof.getHeader();
            validateHeader(header, allowedAlgorithms);

            final JWK jwk = header.getJWK();
            verifySignature(proof, jwk);

            final JWTClaimsSet proofClaims = readClaims(proof);
            validateHttpMethod(proofClaims, request.method() != null ? request.method().name() : null);
            validateHttpUri(proofClaims, resolveRequestUri(request));
            validateIssuedAt(proofClaims);
            final String jti = validateJti(proofClaims);

            final String thumbprint = computeThumbprint(jwk);

            if (accessToken != null) {
                validateAccessTokenHash(proofClaims, accessToken);
            }
            if (expectedThumbprint != null) {
                validateThumbprintBinding(thumbprint, expectedThumbprint);
            }

            return registerJti(jti).andThen(Single.just(thumbprint));
        });
    }

    private SignedJWT parseSingleProof(Request request) {
        final List<String> proofs = request.headers().getAll(ConstantKeys.DPOP_PROOF_HEADER);
        if (proofs == null || proofs.size() != 1) {
            throw invalid("Request must carry exactly one DPoP header");
        }
        try {
            return SignedJWT.parse(proofs.get(0));
        } catch (ParseException e) {
            throw invalid("DPoP proof is not a well-formed signed JWT");
        }
    }

    private void validateHeader(JWSHeader header, Set<String> allowedAlgorithms) {
        if (header.getType() == null || !DPOP_JWT_TYPE.equals(header.getType().getType())) {
            throw invalid("DPoP proof must set the typ header to dpop+jwt");
        }
        final JWSAlgorithm algorithm = header.getAlgorithm();
        if (algorithm == null || !SUPPORTED_ALGORITHMS.contains(algorithm)) {
            throw invalid("DPoP proof uses an unsupported or non-asymmetric signing algorithm");
        }
        if (allowedAlgorithms != null && !allowedAlgorithms.contains(algorithm.getName())) {
            throw invalid("DPoP proof signing algorithm is not accepted by the domain allowlist");
        }
        final JWK jwk = header.getJWK();
        if (jwk == null) {
            throw invalid("DPoP proof must embed the public key in its jwk header");
        }
        if (jwk.isPrivate()) {
            throw invalid("DPoP proof jwk header must not contain private key material");
        }
    }

    private void verifySignature(SignedJWT proof, JWK jwk) {
        try {
            final JWSVerifier verifier;
            if (jwk instanceof ECKey ecKey) {
                verifier = new ECDSAVerifier(ecKey);
            } else if (jwk instanceof RSAKey rsaKey) {
                verifier = new RSASSAVerifier(rsaKey);
            } else {
                throw invalid("DPoP proof key type is not supported");
            }
            if (!proof.verify(verifier)) {
                throw invalid("DPoP proof signature is invalid");
            }
        } catch (JOSEException e) {
            throw invalid("DPoP proof signature could not be verified");
        }
    }

    private JWTClaimsSet readClaims(SignedJWT proof) {
        try {
            return proof.getJWTClaimsSet();
        } catch (ParseException e) {
            throw invalid("DPoP proof claims are not valid");
        }
    }

    private void validateHttpMethod(JWTClaimsSet claims, String requestMethod) {
        final String htm = stringClaim(claims, "htm");
        if (htm == null || requestMethod == null || !htm.equals(requestMethod)) {
            throw invalid("DPoP proof htm does not match the request method");
        }
    }

    private void validateHttpUri(JWTClaimsSet claims, String expectedUri) {
        final String htu = normalizeUri(stringClaim(claims, "htu"));
        if (htu == null || expectedUri == null || !htu.equals(expectedUri)) {
            throw invalid("DPoP proof htu does not match the request URI");
        }
    }

    private void validateIssuedAt(JWTClaimsSet claims) {
        final Date iat = claims.getIssueTime();
        if (iat == null) {
            throw invalid("DPoP proof is missing the iat claim");
        }
        final long now = Instant.now().getEpochSecond();
        final long issuedAt = iat.toInstant().getEpochSecond();
        if (issuedAt < now - validitySeconds || issuedAt > now + clockSkewSeconds) {
            throw invalid("DPoP proof iat is outside the accepted window");
        }
    }

    private String validateJti(JWTClaimsSet claims) {
        final String jti = claims.getJWTID();
        if (jti == null || jti.isBlank()) {
            throw invalid("DPoP proof is missing the jti claim");
        }
        return jti;
    }

    private void validateAccessTokenHash(JWTClaimsSet claims, String accessToken) {
        final String ath = stringClaim(claims, "ath");
        if (ath == null) {
            throw invalid("DPoP proof is missing the ath claim");
        }
        if (!ath.equals(base64UrlSha256(accessToken))) {
            throw invalid("DPoP proof ath does not match the presented access token");
        }
    }

    private void validateThumbprintBinding(String thumbprint, String expectedThumbprint) {
        if (!expectedThumbprint.equals(thumbprint)) {
            throw invalid("DPoP proof key does not match the token cnf.jkt binding");
        }
    }

    private Completable registerJti(String jti) {
        return Completable.defer(() -> {
            final long expiresAt = Instant.now().plusSeconds((long) validitySeconds + clockSkewSeconds).toEpochMilli();
            return replayCache.put(jti, expiresAt)
                    ? Completable.complete()
                    : Completable.error(invalid("DPoP proof has already been used (replay detected)"));
        });
    }

    private String resolveRequestUri(Request request) {
        return normalizeUri(UriBuilderRequest.resolveProxyRequest(request));
    }

    private String computeThumbprint(JWK jwk) {
        try {
            return jwk.computeThumbprint().toString();
        } catch (JOSEException e) {
            throw invalid("Unable to compute the DPoP key thumbprint");
        }
    }

    private static String base64UrlSha256(String value) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64URL.encode(digest.digest(value.getBytes(StandardCharsets.US_ASCII))).toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Strip the query and fragment: {@code htu} is compared ignoring them (RFC 9449 §4.3).
     */
    private static String normalizeUri(String uri) {
        if (uri == null) {
            return null;
        }
        int cut = uri.indexOf('?');
        if (cut >= 0) {
            uri = uri.substring(0, cut);
        }
        cut = uri.indexOf('#');
        if (cut >= 0) {
            uri = uri.substring(0, cut);
        }
        return uri;
    }

    private static InvalidDPoPProofException invalid(String message) {
        return new InvalidDPoPProofException(message);
    }
}
