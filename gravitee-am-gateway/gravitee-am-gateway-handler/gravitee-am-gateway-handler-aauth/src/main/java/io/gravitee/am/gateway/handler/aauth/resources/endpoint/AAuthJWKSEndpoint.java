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
package io.gravitee.am.gateway.handler.aauth.resources.endpoint;

import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.rxjava3.core.Flowable;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Serves the AAUTH Person Server (PS) JWKS document at
 * {@code /aauth/.well-known/jwks.json}.
 * <p>
 * Returns the domain's signing keys in JWK Set format so that resources
 * and other parties can verify auth tokens issued by this PS.
 * Private key material is excluded — only public keys are returned.
 */
@Slf4j
@RequiredArgsConstructor
public class AAuthJWKSEndpoint implements Handler<RoutingContext> {

    private final CertificateManager certificateManager;

    @Override
    public void handle(RoutingContext context) {
        Flowable.fromIterable(certificateManager.providers())
                .flatMap(provider -> provider.getProvider().keys()
                        .map(AAuthJWKSEndpoint::toPublicJwk))
                .toList()
                .subscribe(
                        keys -> {
                            var jwkSet = Map.of("keys", keys);
                            context.response()
                                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/jwk-set+json; charset=UTF-8")
                                    .putHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                                    .end(Json.encodePrettily(jwkSet));
                        },
                        error -> {
                            log.error("Failed to build JWKS response", error);
                            context.response().setStatusCode(500).end();
                        }
                );
    }

    /**
     * Strip private key material from a JWK, keeping only public components.
     * Returns a map suitable for JSON serialization.
     */
    private static Map<String, Object> toPublicJwk(JWK jwk) {
        var map = new java.util.LinkedHashMap<String, Object>();
        // Standard JWK fields (RFC 7517)
        if (jwk.getKty() != null) map.put("kty", jwk.getKty());
        if (jwk.getUse() != null) map.put("use", jwk.getUse());

        // Compute alg from key type/size if not explicitly set (same as OIDC ProviderJWKSetEndpoint)
        String alg = jwk.getAlg();
        if (alg == null && jwk instanceof io.gravitee.am.model.jose.RSAKey rsa && rsa.getN() != null) {
            int keySize = new java.math.BigInteger(rsa.getN().getBytes()).bitLength();
            if (keySize >= 4096) alg = "RS512";
            else if (keySize >= 3072) alg = "RS384";
            else if (keySize >= 2048) alg = "RS256";
        }
        if (alg != null) map.put("alg", alg);
        if (jwk.getKid() != null) map.put("kid", jwk.getKid());

        // Certificate metadata
        if (jwk.getX5c() != null && !jwk.getX5c().isEmpty()) map.put("x5c", jwk.getX5c());
        if (jwk.getX5tS256() != null) map.put("x5t#S256", jwk.getX5tS256());
        if (jwk.getX5t() != null) map.put("x5t", jwk.getX5t());

        // Type-specific public components
        switch (jwk) {
            case io.gravitee.am.model.jose.RSAKey rsa -> {
                if (rsa.getE() != null) map.put("e", rsa.getE());
                if (rsa.getN() != null) map.put("n", rsa.getN());
            }
            case io.gravitee.am.model.jose.ECKey ec -> {
                if (ec.getCrv() != null) map.put("crv", ec.getCrv());
                if (ec.getX() != null) map.put("x", ec.getX());
                if (ec.getY() != null) map.put("y", ec.getY());
            }
            case io.gravitee.am.model.jose.OKPKey okp -> {
                if (okp.getCrv() != null) map.put("crv", okp.getCrv());
                if (okp.getX() != null) map.put("x", okp.getX());
            }
            default -> {
            }
        }

        // Explicitly: no 'd', 'p', 'q', 'dp', 'dq', 'qi' fields
        return map;
    }
}
