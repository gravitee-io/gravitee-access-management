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
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import io.gravitee.am.certificate.api.X509CertUtils;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TrustedIssuerResolver;
import io.gravitee.am.model.TrustedIssuer;

import java.net.URI;
import java.text.ParseException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves and caches JWT processors for trusted external issuers.
 *
 * <p>Supports two key resolution methods:
 * <ul>
 *   <li>JWKS_URL: Fetches keys from a remote JWKS endpoint (with outage tolerance and retrying).</li>
 *   <li>PEM: Uses a static PEM-encoded X.509 certificate.</li>
 * </ul>
 *
 * <p><b>Cache lifecycle:</b> This bean is created per domain reactor via {@code OAuth2Configuration}.
 * When a domain configuration changes, the gateway stops the old reactor and starts a new one,
 * which creates a fresh {@code TrustedIssuerResolverImpl} with an empty cache. This means the
 * processor cache is naturally invalidated on config changes — no explicit cache clearing is needed.
 *
 * @author GraviteeSource Team
 */
public class TrustedIssuerResolverImpl implements TrustedIssuerResolver {

    private static final int JWKS_CONNECT_TIMEOUT_MS = 5000;
    private static final int JWKS_READ_TIMEOUT_MS = 5000;

    private final ConcurrentMap<String, JWTProcessor<SecurityContext>> processorCache = new ConcurrentHashMap<>();

    @Override
    public JWTClaimsSet resolve(String rawToken, TrustedIssuer trustedIssuer) throws BadJOSEException, JOSEException, ParseException {
        JWTProcessor<SecurityContext> processor = processorCache.computeIfAbsent(
                trustedIssuer.getIssuer(),
                key -> buildProcessor(trustedIssuer)
        );
        return processor.process(rawToken, null);
    }

    private JWTProcessor<SecurityContext> buildProcessor(TrustedIssuer trustedIssuer) {
        JWKSource<SecurityContext> jwkSource = buildJwkSource(trustedIssuer);

        // Asymmetric algorithms only; HMAC not supported for external trust
        JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(
                new HashSet<>(List.of(
                        JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
                        JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512,
                        JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512
                )),
                jwkSource
        );

        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(keySelector);
        // Only require "iss" claim to be present; other standard validations (exp, nbf)
        // are handled by DefaultTokenValidator after verification.
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                null, Collections.emptySet()
        ));
        return processor;
    }

    private JWKSource<SecurityContext> buildJwkSource(TrustedIssuer trustedIssuer) {
        String method = trustedIssuer.getKeyResolutionMethod();

        if (TrustedIssuer.KEY_RESOLUTION_JWKS_URL.equals(method)) {
            return buildRemoteJwkSource(trustedIssuer.getJwksUri());
        } else if (TrustedIssuer.KEY_RESOLUTION_PEM.equals(method)) {
            return buildPemJwkSource(trustedIssuer.getCertificate());
        } else {
            throw new IllegalArgumentException("Unsupported key resolution method: " + method);
        }
    }

    @SuppressWarnings("unchecked")
    private JWKSource<SecurityContext> buildRemoteJwkSource(String jwksUri) {
        try {
            DefaultResourceRetriever retriever = new DefaultResourceRetriever(
                    JWKS_CONNECT_TIMEOUT_MS, JWKS_READ_TIMEOUT_MS);
            return (JWKSource<SecurityContext>) JWKSourceBuilder.create(URI.create(jwksUri).toURL(), retriever)
                    .outageTolerant(true)
                    .retrying(true)
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWKS URL: " + jwksUri, e);
        }
    }

    private JWKSource<SecurityContext> buildPemJwkSource(String pemCertificate) {
        X509Certificate cert = X509CertUtils.parse(pemCertificate);
        if (cert == null) {
            throw new IllegalArgumentException("Failed to parse PEM certificate for trusted issuer");
        }
        try {
            JWK jwk = JWK.parse(cert);
            return new ImmutableJWKSet<>(new JWKSet(jwk));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert certificate to JWK", e);
        }
    }
}
