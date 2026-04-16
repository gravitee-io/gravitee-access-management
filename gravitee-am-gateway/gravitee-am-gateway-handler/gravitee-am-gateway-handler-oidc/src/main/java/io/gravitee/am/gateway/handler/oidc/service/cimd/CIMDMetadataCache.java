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
package io.gravitee.am.gateway.handler.oidc.service.cimd;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory LRU cache for CIMD metadata documents.
 * Domain-scoped keys prevent cross-domain cache poisoning.
 *
 * @author GraviteeSource Team
 */
public class CIMDMetadataCache {

    private final int maxEntries;
    private final int ttlSeconds;
    private final Map<String, CacheEntry> cache;

    public CIMDMetadataCache(int maxEntries, int ttlSeconds) {
        this.maxEntries = maxEntries;
        this.ttlSeconds = ttlSeconds;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > CIMDMetadataCache.this.maxEntries;
            }
        };
    }

    public Optional<CIMDMetadataDocument> get(String domainId, String clientIdUri) {
        String key = cacheKey(domainId, clientIdUri);
        synchronized (cache) {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                return Optional.empty();
            }
            if (entry.isExpired()) {
                cache.remove(key);
                return Optional.empty();
            }
            return Optional.of(entry.document);
        }
    }

    public void put(String domainId, String clientIdUri, CIMDMetadataDocument document) {
        String key = cacheKey(domainId, clientIdUri);
        synchronized (cache) {
            cache.put(key, new CacheEntry(document, Instant.now().plusSeconds(ttlSeconds)));
        }
    }

    public void invalidate(String domainId, String clientIdUri) {
        String key = cacheKey(domainId, clientIdUri);
        synchronized (cache) {
            cache.remove(key);
        }
    }

    public void clear() {
        synchronized (cache) {
            cache.clear();
        }
    }

    public int size() {
        synchronized (cache) {
            return cache.size();
        }
    }

    private static String cacheKey(String domainId, String clientIdUri) {
        return domainId + ":" + clientIdUri;
    }

    private record CacheEntry(CIMDMetadataDocument document, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
