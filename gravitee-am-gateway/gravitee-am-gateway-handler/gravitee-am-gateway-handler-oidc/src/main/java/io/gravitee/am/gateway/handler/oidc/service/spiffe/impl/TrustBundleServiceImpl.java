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
import io.gravitee.am.service.utils.PrivateAddressGuard;
import io.reactivex.rxjava3.core.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Caches JWKS bundles per trust domain. Refresh policy:
 * <ul>
 *   <li>Soft refresh interval per trust domain (capped by {@link SpiffeDomainSettings#getCacheTtlSeconds()}).
 *       When an entry is past the soft interval the bundle is re-fetched on the next access.</li>
 *   <li>Eager refresh on {@code kid} miss: fetch a new bundle without evicting the existing one,
 *       so a transient fetch failure can still fall back to the previous bundle.</li>
 *   <li>Stale-on-error: when a refresh fails, the previously cached bundle is returned.
 *       Hard expiry (a multiple of the soft interval, ≥ 1h) bounds how long stale data is served.</li>
 * </ul>
 */
public class TrustBundleServiceImpl implements TrustBundleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrustBundleServiceImpl.class);

    /** Hard-TTL multiplier — entries this much past the soft refresh interval are not served stale. */
    private static final int HARD_TTL_MULTIPLIER = 4;

    private static final long HARD_TTL_FLOOR_SECONDS = Duration.ofHours(1).toSeconds();

    private final JWKService jwkService;
    private final SpiffeDomainSettings settings;
    private final Cache<String, CachedBundle> cache;

    @Autowired
    public TrustBundleServiceImpl(JWKService jwkService, Domain domain) {
        this.jwkService = jwkService;
        this.settings = Optional.ofNullable(domain.getOidc())
                .map(o -> o.getWorkloadIdentitySettings())
                .orElseGet(SpiffeDomainSettings::defaultSettings);
        long hardTtl = Math.max(
                (long) settings.getCacheTtlSeconds() * HARD_TTL_MULTIPLIER,
                HARD_TTL_FLOOR_SECONDS);
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(settings.getCacheMaxEntries())
                .expireAfterWrite(hardTtl, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public Maybe<JWKSet> getKeys(TrustDomain trustDomain) {
        if (trustDomain == null) {
            return Maybe.empty();
        }
        CachedBundle cached = cache.getIfPresent(trustDomain.getId());
        if (cached != null && !isStale(cached, trustDomain)) {
            return Maybe.just(cached.jwks);
        }
        return fetch(trustDomain, cached);
    }

    @Override
    public Maybe<JWK> getKey(TrustDomain trustDomain, String kid) {
        if (trustDomain == null || kid == null || kid.isBlank()) {
            return Maybe.empty();
        }
        return getKeys(trustDomain)
                .flatMap(jwks -> findKid(jwks, kid)
                        .switchIfEmpty(Maybe.defer(() -> {
                            // kid miss: fetch fresh without evicting first; if the fetch fails we
                            // fall back to the cached bundle, then look up the kid again.
                            CachedBundle existing = cache.getIfPresent(trustDomain.getId());
                            return fetch(trustDomain, existing)
                                    .flatMap(refreshed -> findKid(refreshed, kid));
                        })));
    }

    @Override
    public void evict(String trustDomainId) {
        if (trustDomainId != null) {
            cache.invalidate(trustDomainId);
        }
    }

    private boolean isStale(CachedBundle entry, TrustDomain trustDomain) {
        return entry.fetchedAt.plusSeconds(softTtlSeconds(trustDomain)).isBefore(Instant.now());
    }

    private long softTtlSeconds(TrustDomain trustDomain) {
        long perDomain = trustDomain.getRefreshIntervalSeconds() > 0
                ? trustDomain.getRefreshIntervalSeconds()
                : settings.getCacheTtlSeconds();
        return Math.min(perDomain, settings.getCacheTtlSeconds());
    }

    private Maybe<JWKSet> fetch(TrustDomain trustDomain, CachedBundle existing) {
        if (trustDomain.getBundleSource() != SpiffeBundleSource.JWKS_URL) {
            return Maybe.error(new UnsupportedOperationException(
                    "Bundle source " + trustDomain.getBundleSource() + " is not supported in this release"));
        }
        if (trustDomain.getJwksUrl() == null || trustDomain.getJwksUrl().isBlank()) {
            return Maybe.empty();
        }
        // Re-validate the URL against current SPIFFE policy at fetch time.
        // Validation also runs on create/update, but DNS may rebind to a private
        // address afterwards or the domain policy may have been tightened since.
        String urlSafetyError = checkUrlSafety(trustDomain.getJwksUrl());
        if (urlSafetyError != null) {
            return Maybe.error(new SecurityException(
                    "Refused to fetch JWKS for trust domain " + trustDomain.getName() + ": " + urlSafetyError));
        }
        Maybe<JWKSet> upstream = jwkService.getKeys(trustDomain.getJwksUrl());
        if (settings.getFetchTimeoutMs() > 0) {
            upstream = upstream.timeout(settings.getFetchTimeoutMs(), TimeUnit.MILLISECONDS);
        }
        return upstream
                .doOnSuccess(jwks -> cache.put(trustDomain.getId(), new CachedBundle(jwks, Instant.now())))
                .onErrorResumeNext(error -> {
                    if (existing != null) {
                        LOGGER.warn("Failed to refresh trust bundle for {} ({}); serving stale bundle from {}",
                                trustDomain.getName(), error.getMessage(), existing.fetchedAt);
                        return Maybe.just(existing.jwks);
                    }
                    return Maybe.error(error);
                });
    }

    /**
     * Returns null when the URL passes current SPIFFE policy, or a human-readable reason otherwise.
     * Mirrors the create/update validation in TrustDomainServiceImpl so that a stored URL whose
     * resolution drifts to a private address — or that violates a tightened domain policy — is
     * refused at fetch time.
     */
    private String checkUrlSafety(String jwksUrl) {
        return PrivateAddressGuard.validateHttpUrl(
                "jwksUrl", jwksUrl, settings.isAllowUnsecuredHttpUri(), settings.isAllowPrivateIpAddress())
                .orElse(null);
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
