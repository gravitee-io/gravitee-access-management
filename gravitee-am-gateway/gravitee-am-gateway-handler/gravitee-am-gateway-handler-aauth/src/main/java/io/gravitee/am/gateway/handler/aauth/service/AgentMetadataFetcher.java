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
package io.gravitee.am.gateway.handler.aauth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import io.gravitee.am.gateway.handler.aauth.signing.SignatureVerificationException;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches and caches agent metadata and JWKS documents from remote agent servers.
 * <p>
 * Caching rules per the AAUTH spec (JWKS Discovery section):
 * <ul>
 *   <li>MUST NOT fetch more frequently than once per 60 seconds per URL</li>
 *   <li>Discard cached entries after 24 hours</li>
 *   <li>On failure, use cached data if available</li>
 * </ul>
 */
@Slf4j
public class AgentMetadataFetcher {

    private static final long MIN_FETCH_INTERVAL_SECONDS = 60;
    private static final long MAX_CACHE_AGE_SECONDS = 86400; // 24 hours
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CachedEntry<AgentMetadata>> metadataCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedEntry<JWKSDocument>> jwksCache = new ConcurrentHashMap<>();

    public AgentMetadataFetcher() {
        this(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build(), new ObjectMapper());
    }

    public AgentMetadataFetcher(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetch agent metadata from {@code {agentUrl}/.well-known/aauth-agent.json}.
     */
    public AgentMetadata fetchMetadata(String agentUrl) throws SignatureVerificationException {
        String url = agentUrl + "/.well-known/aauth-agent.json";
        return fetchCached(url, metadataCache, () -> {
            String body = httpGet(url);
            return objectMapper.readValue(body, AgentMetadata.class);
        });
    }

    /**
     * Fetch a JWKS document from the given URL.
     *
     * @param jwksUri      the JWKS URL
     * @param forceRefresh if true, bypass the cache (used for unknown_key retry)
     */
    public JWKSDocument fetchJWKS(String jwksUri, boolean forceRefresh) throws SignatureVerificationException {
        if (forceRefresh) {
            jwksCache.remove(jwksUri);
        }
        return fetchCached(jwksUri, jwksCache, () -> {
            String body = httpGet(jwksUri);
            JWKSet jwkSet = JWKSet.parse(body);
            return new JWKSDocument(jwkSet);
        });
    }

    public JWKSDocument fetchJWKS(String jwksUri) throws SignatureVerificationException {
        return fetchJWKS(jwksUri, false);
    }

    private <T> T fetchCached(String url, ConcurrentHashMap<String, CachedEntry<T>> cache,
                               FetchAction<T> fetchAction) throws SignatureVerificationException {
        CachedEntry<T> cached = cache.get(url);
        Instant now = Instant.now();

        // Return cached if within minimum fetch interval
        if (cached != null && cached.isValid(now, MIN_FETCH_INTERVAL_SECONDS)) {
            return cached.value;
        }

        // Evict if too old
        if (cached != null && cached.isExpired(now, MAX_CACHE_AGE_SECONDS)) {
            cache.remove(url);
            cached = null;
        }

        try {
            T result = fetchAction.fetch();
            cache.put(url, new CachedEntry<>(result, now));
            return result;
        } catch (Exception e) {
            // On failure, return cached if available
            if (cached != null) {
                log.warn("Failed to fetch {}, using cached value: {}", url, e.getMessage());
                return cached.value;
            }
            log.error("Failed to fetch {} and no cached value available: {}", url, e.getMessage());
            throw new SignatureVerificationException("invalid_key");
        }
    }

    private String httpGet(String url) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " from " + url);
        }
        return response.body();
    }

    @FunctionalInterface
    private interface FetchAction<T> {
        T fetch() throws Exception;
    }

    private record CachedEntry<T>(T value, Instant fetchedAt) {
        boolean isValid(Instant now, long minIntervalSeconds) {
            return Duration.between(fetchedAt, now).getSeconds() < minIntervalSeconds;
        }

        boolean isExpired(Instant now, long maxAgeSeconds) {
            return Duration.between(fetchedAt, now).getSeconds() > maxAgeSeconds;
        }
    }
}
