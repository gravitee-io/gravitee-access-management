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
import io.gravitee.am.model.KeyResolutionMethod;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
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

    /**
     * Namespaces the cache key so an inline trusted issuer cannot collide with a stored trusted
     * domain. Transitional: trusted issuers become trusted-domain entities of their own later.
     */
    private static final String CACHE_KEY_PREFIX = "trusted-issuer:";

    private final TrustDomainKeyService trustDomainKeyService;
    private final JWSService jwsService;

    public TrustedIssuerResolverImpl(TrustDomainKeyService trustDomainKeyService, JWSService jwsService) {
        this.trustDomainKeyService = trustDomainKeyService;
        this.jwsService = jwsService;
    }

    @Override
    public Single<JWTClaimsSet> resolve(String rawToken, TrustedIssuer trustedIssuer) {
        final SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(rawToken);
        } catch (ParseException e) {
            return Single.error(malformed(trustedIssuer));
        }
        JWSAlgorithm algorithm = signedJWT.getHeader().getAlgorithm();
        if (!SUPPORTED_ALGORITHMS.contains(algorithm)) {
            return Single.error(new SecurityException(
                    "Unsupported signature algorithm " + algorithm + " for trusted issuer: " + trustedIssuer.getIssuer()));
        }
        final TrustDomain trustDomain;
        try {
            trustDomain = asTrustDomain(trustedIssuer);
        } catch (IllegalArgumentException e) {
            return Single.error(e);
        }
        return verify(signedJWT, trustDomain, trustedIssuer);
    }

    private Single<JWTClaimsSet> verify(SignedJWT signedJWT, TrustDomain trustDomain, TrustedIssuer trustedIssuer) {
        String kid = signedJWT.getHeader().getKeyID();
        Maybe<Boolean> verified = kid == null || kid.isBlank()
                ? trustDomainKeyService.getKeys(trustDomain).map(jwks -> anyKeyVerifies(signedJWT, jwks))
                : trustDomainKeyService.getKey(trustDomain, kid).map(jwk -> isValidSignature(signedJWT, jwk));
        return verified
                .switchIfEmpty(Single.error(() -> new SecurityException(
                        "No signing key available for trusted issuer: " + trustedIssuer.getIssuer())))
                .flatMap(valid -> valid
                        ? claimsOf(signedJWT, trustedIssuer)
                        : Single.error(new SecurityException(
                                "JWT signature verification failed for trusted issuer: " + trustedIssuer.getIssuer())));
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

    private static Single<JWTClaimsSet> claimsOf(SignedJWT signedJWT, TrustedIssuer trustedIssuer) {
        try {
            return Single.just(signedJWT.getJWTClaimsSet());
        } catch (ParseException e) {
            return Single.error(malformed(trustedIssuer));
        }
    }

    private static SecurityException malformed(TrustedIssuer trustedIssuer) {
        return new SecurityException("Malformed JWT from trusted issuer: " + trustedIssuer.getIssuer());
    }

    private static TrustDomain asTrustDomain(TrustedIssuer trustedIssuer) {
        KeyResolutionMethod method = trustedIssuer.getKeyResolutionMethod();
        if (method == null) {
            throw new IllegalArgumentException("Unsupported key resolution method: null");
        }
        TrustDomainKeyMaterial keyMaterial = switch (method) {
            case JWKS_URL -> TrustDomainKeyMaterial.builder()
                    .source(KeyMaterialSource.JWKS_URL)
                    .jwksUrl(trustedIssuer.getJwksUri())
                    .build();
            case PEM -> TrustDomainKeyMaterial.builder()
                    .source(KeyMaterialSource.PEM)
                    .certificate(trustedIssuer.getCertificate())
                    .build();
        };
        return TrustDomain.builder()
                .id(CACHE_KEY_PREFIX + trustedIssuer.getIssuer())
                .name(trustedIssuer.getIssuer())
                .keyMaterial(keyMaterial)
                .build();
    }
}
