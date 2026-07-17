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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Resolves an OpenID Provider's CIBA endpoints from its discovery document and caches them per
 * well-known URL with a refresh TTL. Fail-closed: any resolution failure errors the call — the
 * caller must NOT fall back to guessed endpoints.
 */
public class OidcDiscoveryResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(OidcDiscoveryResolver.class);
    private static final String GENERIC_ERROR = "CIBA federation: could not resolve the identity provider's OIDC discovery document";

    private record Cached(ProviderMetadata metadata, long expiresAtMillis) {}

    private final WebClient client;
    private final long ttlMillis;
    private final LongSupplier clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public OidcDiscoveryResolver(WebClient client, long ttlSeconds) {
        this(client, ttlSeconds, System::currentTimeMillis);
    }

    OidcDiscoveryResolver(WebClient client, long ttlSeconds, LongSupplier clock) {
        this.client = client;
        this.ttlMillis = ttlSeconds * 1000L;
        this.clock = clock;
    }

    public Single<ProviderMetadata> resolve(String wellKnownUri) {
        if (wellKnownUri == null || wellKnownUri.isBlank()) {
            return Single.error(new IllegalStateException("CIBA federation: IdP wellKnownUri is not configured"));
        }
        Cached c = cache.get(wellKnownUri);
        if (c != null && c.expiresAtMillis() > clock.getAsLong()) {
            return Single.just(c.metadata());
        }
        return client.getAbs(wellKnownUri).rxSend().map(resp -> {
            // The wellKnownUri is an internal configuration detail — log it server-side, but keep it
            // out of the thrown message that can surface toward the relying client.
            if (resp.statusCode() != 200) {
                LOGGER.warn("CIBA-FED discovery failed (status={}) for {}", resp.statusCode(), wellKnownUri);
                throw new IllegalStateException(GENERIC_ERROR);
            }
            JsonObject body = resp.bodyAsJsonObject();
            if (body == null) {
                LOGGER.warn("CIBA-FED discovery returned a non-JSON document for {}", wellKnownUri);
                throw new IllegalStateException(GENERIC_ERROR);
            }
            String issuer = body.getString("issuer");
            String bc = body.getString("backchannel_authentication_endpoint");
            String token = body.getString("token_endpoint");
            if (issuer == null || issuer.isBlank()) {
                LOGGER.warn("CIBA-FED discovery omits issuer for {}", wellKnownUri);
                throw new IllegalStateException(GENERIC_ERROR);
            }
            if (bc == null || bc.isBlank()) {
                LOGGER.warn("CIBA-FED discovery omits backchannel_authentication_endpoint for {}", wellKnownUri);
                throw new IllegalStateException(GENERIC_ERROR);
            }
            if (token == null || token.isBlank()) {
                LOGGER.warn("CIBA-FED discovery omits token_endpoint for {}", wellKnownUri);
                throw new IllegalStateException(GENERIC_ERROR);
            }
            ProviderMetadata ep = new ProviderMetadata(issuer, bc, token);
            cache.put(wellKnownUri, new Cached(ep, clock.getAsLong() + ttlMillis));
            return ep;
        });
    }
}
