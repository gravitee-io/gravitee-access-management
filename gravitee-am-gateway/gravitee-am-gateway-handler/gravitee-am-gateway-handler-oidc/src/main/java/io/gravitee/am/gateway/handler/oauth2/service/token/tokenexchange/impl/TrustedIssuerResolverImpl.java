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
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TrustedIssuerResolver;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.gateway.handler.oidc.service.trustdomain.TrustDomainKeyService;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.TrustDomain;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.text.ParseException;
import java.util.List;
import java.util.Set;

/**
 * Verifies JWTs presented for a trusted external issuer, resolving the issuer's keys through the
 * shared {@link TrustDomainKeyService}. That service applies the domain's scheme, private-address,
 * timeout and response-size restrictions to any remote fetch, and owns the cache: keys survive a
 * transient fetch failure and are refreshed on a {@code kid} miss.
 *
 * @author GraviteeSource Team
 */
public class TrustedIssuerResolverImpl implements TrustedIssuerResolver {

    /** Asymmetric algorithms only; HMAC is not supported for external trust. */
    private static final Set<JWSAlgorithm> SUPPORTED_ALGORITHMS = Set.of(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512,
            JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512);

    private static final String SIGNATURE_USE = "sig";

    private final TrustDomainKeyService trustDomainKeyService;
    private final JWSService jwsService;

    public TrustedIssuerResolverImpl(TrustDomainKeyService trustDomainKeyService, JWSService jwsService) {
        this.trustDomainKeyService = trustDomainKeyService;
        this.jwsService = jwsService;
    }

    @Override
    public Single<JWTClaimsSet> resolve(String rawToken, TrustDomain trustedDomain) {
        final SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(rawToken);
        } catch (ParseException e) {
            return Single.error(malformed(trustedDomain));
        }
        JWSAlgorithm algorithm = signedJWT.getHeader().getAlgorithm();
        if (!SUPPORTED_ALGORITHMS.contains(algorithm)) {
            return Single.error(new SecurityException(
                    "Unsupported signature algorithm " + algorithm + " for trusted issuer: " + trustedDomain.issuer()));
        }
        return verify(signedJWT, trustedDomain);
    }

    private Single<JWTClaimsSet> verify(SignedJWT signedJWT, TrustDomain trustedDomain) {
        String kid = signedJWT.getHeader().getKeyID();
        Maybe<Boolean> verified = kid == null || kid.isBlank()
                ? trustDomainKeyService.getKeys(trustedDomain).map(jwks -> anyKeyVerifies(signedJWT, jwks))
                : trustDomainKeyService.getKey(trustedDomain, kid).map(jwk -> isValidSignature(signedJWT, jwk));
        return verified
                .switchIfEmpty(Single.error(() -> new SecurityException(
                        "No signing key available for trusted issuer: " + trustedDomain.issuer())))
                .flatMap(valid -> valid
                        ? claimsOf(signedJWT, trustedDomain)
                        : Single.error(new SecurityException(
                                "JWT signature verification failed for trusted issuer: " + trustedDomain.issuer())));
    }

    private boolean anyKeyVerifies(SignedJWT signedJWT, JWKSet jwks) {
        List<JWK> keys = jwks != null ? jwks.getKeys() : null;
        return keys != null && keys.stream()
                .filter(TrustedIssuerResolverImpl::usableForSignatureVerification)
                .anyMatch(jwk -> isValidSignature(signedJWT, jwk));
    }

    private static boolean usableForSignatureVerification(JWK jwk) {
        return jwk.getUse() == null || SIGNATURE_USE.equals(jwk.getUse());
    }

    private boolean isValidSignature(SignedJWT signedJWT, JWK jwk) {
        try {
            return jwsService.isValidSignature(signedJWT, jwk);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Single<JWTClaimsSet> claimsOf(SignedJWT signedJWT, TrustDomain trustedDomain) {
        try {
            return Single.just(signedJWT.getJWTClaimsSet());
        } catch (ParseException e) {
            return Single.error(malformed(trustedDomain));
        }
    }

    private static SecurityException malformed(TrustDomain trustedDomain) {
        return new SecurityException("Malformed JWT from trusted issuer: " + trustedDomain.issuer());
    }
}
