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
package io.gravitee.am.gateway.handler.oidc.service.trustdomain.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.nimbusds.jose.JOSEException;
import io.gravitee.am.certificate.api.X509CertUtils;
import io.gravitee.am.gateway.handler.oidc.service.trustdomain.TrustDomainKeyService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.KeyRetrievalSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.service.jwk.JWKSetFetcher;
import io.gravitee.am.service.utils.jwk.converter.JWKConverter;
import io.gravitee.am.service.utils.PrivateAddressGuard;
import io.reactivex.rxjava3.core.Maybe;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;

/**
 * Caches JWKS bundles per trust domain. Refresh policy:
 * <ul>
 *   <li>Soft refresh interval per trust domain (capped by {@link KeyRetrievalSettings#getCacheTtlSeconds()}).
 *       When an entry is past the soft interval the bundle is re-fetched on the next access.</li>
 *   <li>Eager refresh on {@code kid} miss: fetch a new bundle without evicting the existing one,
 *       so a transient fetch failure can still fall back to the previous bundle.</li>
 *   <li>Stale-on-error: when a refresh fails, the previously cached bundle is returned.
 *       Hard expiry (a multiple of the soft interval, ≥ 1h) bounds how long stale data is served.</li>
 * </ul>
 */
@CustomLog
public class TrustDomainKeyServiceImpl implements TrustDomainKeyService {


    /** Hard-TTL multiplier — entries this much past the soft refresh interval are not served stale. */
    private static final int HARD_TTL_MULTIPLIER = 4;

    private static final long HARD_TTL_FLOOR_SECONDS = Duration.ofHours(1).toSeconds();

    private final JWKSetFetcher jwkSetFetcher;
    private final KeyRetrievalSettings settings;
    private final Cache<String, CachedBundle> cache;

    public TrustDomainKeyServiceImpl(JWKSetFetcher jwkSetFetcher, Domain domain) {
        this.jwkSetFetcher = jwkSetFetcher;
        this.settings = Optional.ofNullable(domain.getKeyRetrievalSettings())
                .orElseGet(KeyRetrievalSettings::defaultSettings);
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
        KeyMaterialSource source = sourceOf(trustDomain);
        if (source == null) {
            return Maybe.empty();
        }
        if (source != KeyMaterialSource.JWKS_URL) {
            return inlineKeys(trustDomain, source);
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
        KeyMaterialSource source = sourceOf(trustDomain);
        if (source == KeyMaterialSource.PEM) {
            // A PEM certificate holds exactly one key and carries no kid of its own, so matching on
            // kid would reject a token that key did in fact sign.
            return getKeys(trustDomain).flatMap(TrustDomainKeyServiceImpl::onlyKey);
        }
        return getKeys(trustDomain)
                .flatMap(jwks -> findKid(jwks, kid)
                        .switchIfEmpty(Maybe.defer(() -> {
                            if (source != KeyMaterialSource.JWKS_URL) {
                                // inline key material: there is nothing to refresh
                                return Maybe.empty();
                            }
                            // kid miss: fetch fresh without evicting first; if the fetch fails we
                            // fall back to the cached bundle, then look up the kid again.
                            CachedBundle existing = cache.getIfPresent(trustDomain.getId());
                            return fetch(trustDomain, existing)
                                    .flatMap(refreshed -> findKid(refreshed, kid));
                        })));
    }

    private static KeyMaterialSource sourceOf(TrustDomain trustDomain) {
        return Optional.ofNullable(trustDomain.getKeyMaterial())
                .map(TrustDomainKeyMaterial::getSource)
                .orElse(null);
    }

    private Maybe<JWKSet> inlineKeys(TrustDomain trustDomain, KeyMaterialSource source) {
        TrustDomainKeyMaterial keyMaterial = trustDomain.getKeyMaterial();
        return switch (source) {
            case JWK_SET -> keyMaterial.getJwkSet() != null
                    ? Maybe.just(keyMaterial.getJwkSet())
                    : Maybe.empty();
            case PEM -> parseCertificate(trustDomain, keyMaterial.getCertificate());
            case JWKS_URL -> Maybe.empty();
        };
    }

    private Maybe<JWKSet> parseCertificate(TrustDomain trustDomain, String certificate) {
        X509Certificate cert = X509CertUtils.parse(certificate);
        if (cert == null) {
            return Maybe.error(new IllegalStateException(
                    "Unable to parse the PEM certificate of trust domain " + trustDomain.getName()));
        }
        try {
            JWKSet jwkSet = new JWKSet();
            jwkSet.setKeys(List.of(JWKConverter.convert(com.nimbusds.jose.jwk.JWK.parse(cert))));
            return Maybe.just(jwkSet);
        } catch (JOSEException e) {
            return Maybe.error(new IllegalStateException(
                    "Unable to convert the PEM certificate of trust domain " + trustDomain.getName() + " to a JWK", e));
        }
    }

    private static Maybe<JWK> onlyKey(JWKSet jwks) {
        if (jwks == null || jwks.getKeys() == null || jwks.getKeys().isEmpty()) {
            return Maybe.empty();
        }
        return Maybe.just(jwks.getKeys().get(0));
    }

    @Override
    public void evict(String trustDomainId) {
        if (trustDomainId != null) {
            cache.invalidate(trustDomainId);
        }
    }

    private boolean isStale(CachedBundle entry, TrustDomain trustDomain) {
        long softTtl = softTtlSeconds(trustDomain);
        return softTtl <= 0 || entry.fetchedAt.plusSeconds(softTtl).isBefore(Instant.now());
    }

    private long softTtlSeconds(TrustDomain trustDomain) {
        long perDomain = trustDomain.getRefreshIntervalSeconds() > 0
                ? trustDomain.getRefreshIntervalSeconds()
                : settings.getCacheTtlSeconds();
        return Math.min(perDomain, settings.getCacheTtlSeconds());
    }

    private Maybe<JWKSet> fetch(TrustDomain trustDomain, CachedBundle existing) {
        String jwksUrl = trustDomain.getKeyMaterial().getJwksUrl();
        if (jwksUrl == null || jwksUrl.isBlank()) {
            return Maybe.empty();
        }
        // Re-validate the URL against the current key retrieval policy at fetch time.
        // Validation also runs on create/update, but DNS may rebind to a private
        // address afterwards or the domain policy may have been tightened since.
        String urlSafetyError = checkUrlSafety(jwksUrl);
        if (urlSafetyError != null) {
            log.warn("SSRF guard refused the key material fetch for trusted domain {} ({}): {}",
                    trustDomain.getName(), jwksUrl, urlSafetyError);
            return Maybe.error(new SecurityException(
                    "Refused to fetch JWKS for trust domain " + trustDomain.getName() + ": " + urlSafetyError));
        }
        Maybe<JWKSet> upstream = jwkSetFetcher.getKeys(jwksUrl, maxResponseSizeBytes())
                .map(JWKSetFetcher.JWKSetFetchResponse::jwkSet);
        if (settings.getFetchTimeoutMs() > 0) {
            upstream = upstream.timeout(settings.getFetchTimeoutMs(), TimeUnit.MILLISECONDS);
        }
        return upstream
                .doOnSuccess(jwks -> cache.put(trustDomain.getId(), new CachedBundle(jwks, Instant.now())))
                .onErrorResumeNext(error -> {
                    if (existing != null) {
                        log.warn("Failed to refresh trust bundle for {} ({}); serving stale bundle from {}",
                                trustDomain.getName(), error.getMessage(), existing.fetchedAt);
                        return Maybe.just(existing.jwks);
                    }
                    return Maybe.error(error);
                });
    }

    private long maxResponseSizeBytes() {
        return Math.max(0L, settings.getMaxResponseSizeKb()) * 1024L;
    }

    /**
     * Returns null when the URL passes the current key retrieval policy, or a human-readable reason otherwise.
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
