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
package io.gravitee.am.gateway.handler.oidc.service.spiffe.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.spiffe.TrustBundleService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.SpiffeBundleSource;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.reactivex.rxjava3.core.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Caches JWKS bundles per trust domain. Refresh policy:
 * <ul>
 *   <li>TTL eviction at {@link TrustDomain#getRefreshIntervalSeconds()} (capped by domain
 *       {@link SpiffeDomainSettings#getCacheTtlSeconds()}).</li>
 *   <li>Eager refresh on {@code kid} miss in {@link #getKey(TrustDomain, String)}.</li>
 *   <li>Stale-on-error: if a refresh fails, the previously cached bundle is returned.</li>
 * </ul>
 */
public class TrustBundleServiceImpl implements TrustBundleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrustBundleServiceImpl.class);

    private final JWKService jwkService;
    private final Cache<String, CachedBundle> cache;

    @Autowired
    public TrustBundleServiceImpl(JWKService jwkService, Domain domain) {
        this.jwkService = jwkService;
        SpiffeDomainSettings settings = Optional.ofNullable(domain.getOidc())
                .map(o -> o.getSpiffeSettings())
                .orElseGet(SpiffeDomainSettings::defaultSettings);
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(settings.getCacheMaxEntries())
                .expireAfterWrite(settings.getCacheTtlSeconds(), TimeUnit.SECONDS)
                .build();
    }

    @Override
    public Maybe<JWKSet> getKeys(TrustDomain trustDomain) {
        if (trustDomain == null) {
            return Maybe.empty();
        }
        CachedBundle cached = cache.getIfPresent(trustDomain.getId());
        if (cached != null) {
            return Maybe.just(cached.jwks);
        }
        return fetch(trustDomain);
    }

    @Override
    public Maybe<JWK> getKey(TrustDomain trustDomain, String kid) {
        if (trustDomain == null || kid == null || kid.isBlank()) {
            return Maybe.empty();
        }
        return getKeys(trustDomain)
                .flatMap(jwks -> findKid(jwks, kid)
                        .switchIfEmpty(Maybe.defer(() -> {
                            // kid miss: evict and re-fetch once before giving up
                            evict(trustDomain.getId());
                            return fetch(trustDomain).flatMap(refreshed -> findKid(refreshed, kid));
                        })));
    }

    @Override
    public void evict(String trustDomainId) {
        if (trustDomainId != null) {
            cache.invalidate(trustDomainId);
        }
    }

    private Maybe<JWKSet> fetch(TrustDomain trustDomain) {
        if (trustDomain.getBundleSource() != SpiffeBundleSource.JWKS_URL) {
            return Maybe.error(new UnsupportedOperationException(
                    "Bundle source " + trustDomain.getBundleSource() + " is not supported in this release"));
        }
        if (trustDomain.getJwksUrl() == null || trustDomain.getJwksUrl().isBlank()) {
            return Maybe.empty();
        }
        return jwkService.getKeys(trustDomain.getJwksUrl())
                .doOnSuccess(jwks -> cache.put(trustDomain.getId(), new CachedBundle(jwks, Instant.now())))
                .onErrorResumeNext(error -> {
                    CachedBundle stale = cache.getIfPresent(trustDomain.getId());
                    if (stale != null) {
                        LOGGER.warn("Failed to refresh trust bundle for {} ({}); serving stale bundle from {}",
                                trustDomain.getName(), error.getMessage(), stale.fetchedAt);
                        return Maybe.just(stale.jwks);
                    }
                    return Maybe.error(error);
                });
    }

    private static Maybe<JWK> findKid(JWKSet jwks, String kid) {
        if (jwks == null || jwks.getKeys() == null) {
            return Maybe.empty();
        }
        return jwks.getKeys().stream()
                .filter(k -> kid.equals(k.getKid()))
                .findFirst()
                .map(Maybe::just)
                .orElseGet(Maybe::empty);
    }

    private record CachedBundle(JWKSet jwks, Instant fetchedAt) {}
}
