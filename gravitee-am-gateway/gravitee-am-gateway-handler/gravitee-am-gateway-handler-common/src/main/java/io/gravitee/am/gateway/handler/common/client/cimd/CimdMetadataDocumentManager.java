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
package io.gravitee.am.gateway.handler.common.client.cimd;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.gravitee.am.common.event.CimdMetadataEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.service.CimdMetadataDocumentService;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Per-domain in-memory cache for CIMD metadata documents and their associated logos.
 *
 * @author GraviteeSource Team
 */
@Slf4j
public class CimdMetadataDocumentManager extends AbstractService<CimdMetadataDocumentManager>
        implements EventListener<CimdMetadataEvent, Payload>, InitializingBean {

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private CimdMetadataDocumentService cimdMetadataDocumentService;

    private final Cache<String, CacheEntry> cache;

    public CimdMetadataDocumentManager(CIMDSettings settings) {
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(settings.getCacheMaxEntries())
                .expireAfterWrite(settings.getCacheTtlSeconds(), TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void afterPropertiesSet() {
        log.info("Pre-loading CIMD metadata documents for domain {}", domain.getName());
        cimdMetadataDocumentService.findByDomain(domain.getId())
                .filter(doc -> !doc.isExpired())
                .subscribe(
                        doc -> cache.put(doc.getClientId(), new CacheEntry(doc, null)),
                        error -> log.error("Unable to pre-load CIMD documents for domain {}", domain.getName(), error)
                );
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.info("Register event listener for CIMD metadata events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, CimdMetadataEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        log.info("Dispose event listener for CIMD metadata events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, CimdMetadataEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<CimdMetadataEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN
                && domain.getId().equals(event.content().getReferenceId())) {
            final String clientId = event.content().getId();
            switch (event.type()) {
                case UPDATE, UNDEPLOY -> {
                    cache.invalidate(clientId);
                    log.debug("Domain {} evicted CIMD cache for clientId {}", domain.getName(), clientId);
                }
                case DEPLOY -> log.debug("Domain {} received CIMD DEPLOY for clientId {}", domain.getName(), clientId);
            }
        }
    }

    /**
     * Returns a non-expired cached document, or empty if absent or expired.
     */
    public Optional<CimdMetadataDocument> get(String clientId) {
        CacheEntry entry = cache.getIfPresent(clientId);
        if (entry == null || entry.document().isExpired()) {
            if (entry != null) {
                cache.invalidate(clientId);
            }
            return Optional.empty();
        }
        return Optional.of(entry.document());
    }

    /**
     * Stores a DB-sourced document in the cache, preserving any logo already cached for this entry.
     */
    public void put(String clientId, CimdMetadataDocument doc) {
        CacheEntry existing = cache.getIfPresent(clientId);
        cache.put(clientId, new CacheEntry(doc, existing != null ? existing.logo() : null));
    }

    /**
     * Constructs a document from a freshly fetched metadata JSON and stores it in the cache (no logo yet).
     */
    public void put(String clientId, JsonObject metadata, Duration ttl) {
        final CimdMetadataDocument doc = CimdMetadataDocument.of(domain.getId(), clientId, metadata.encode(), ttl);
        cache.put(clientId, new CacheEntry(doc, null));
    }

    /**
     * Attaches a logo to an existing cache entry. No-ops if the metadata entry is absent (e.g. evicted
     * between the async fetch start and completion).
     */
    public void putLogo(String clientId, CachedLogo logo) {
        CacheEntry existing = cache.getIfPresent(clientId);
        if (existing != null) {
            cache.put(clientId, new CacheEntry(existing.document(), logo));
        }
    }

    public Optional<CachedLogo> getLogoByClientId(String clientId) {
        CacheEntry entry = cache.getIfPresent(clientId);
        if (entry == null || entry.logo() == null) {
            return Optional.empty();
        }
        if (entry.document().isExpired()) {
            cache.invalidate(clientId);
            return Optional.empty();
        }
        long remainingSeconds = Math.max(0L,
                (entry.document().getExpiresAt().getTime() - System.currentTimeMillis()) / 1000);
        CachedLogo logo = entry.logo();
        return Optional.of(new CachedLogo(logo.bytes(), logo.contentType(), remainingSeconds));
    }

    /**
     * Returns a non-expired document from the local cache, falling back and warming from the DB on a miss.
     */
    public Single<Optional<CimdMetadataDocument>> resolve(String clientId) {
        Optional<CimdMetadataDocument> local = get(clientId);
        if (local.isPresent()) {
            log.debug("CIMD local cache hit for domain={}, clientId={}", domain.getId(), clientId);
            return Single.just(local);
        }
        log.debug("CIMD local cache miss, checking DB for domain={}, clientId={}", domain.getId(), clientId);
        return cimdMetadataDocumentService.findByDomainAndClientId(domain.getId(), clientId)
                .filter(doc -> !doc.isExpired())
                .doOnSuccess(doc -> {
                    log.debug("CIMD DB cache hit (restored) for domain={}, clientId={}", domain.getId(), clientId);
                    put(clientId, doc);
                })
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    public static long remainingTtlSeconds(CimdMetadataDocument doc) {
        if (doc.getExpiresAt() == null) {
            return 0L;
        }
        return Math.max(0L, (doc.getExpiresAt().getTime() - System.currentTimeMillis()) / 1000L);
    }

    public static String detectMimeType(byte[] bytes) {
        if (bytes.length >= 4
                && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 2
                && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return "image/jpeg";
        }
        if (bytes.length >= 3
                && bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46) {
            return "image/gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return "image/webp";
        }
        final int o = skipLeadingBomAndAsciiWhitespace(bytes);
        final int n = bytes.length - o;
        if (n >= 4 && bytes[o] == '<' && bytes[o + 1] == 's' && bytes[o + 2] == 'v' && bytes[o + 3] == 'g') {
            return "image/svg+xml";
        }
        if (n >= 5 && bytes[o] == '<' && bytes[o + 1] == '?' && bytes[o + 2] == 'x' && bytes[o + 3] == 'm' && bytes[o + 4] == 'l') {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    /**
     * Strips an optional UTF-8 BOM and ASCII whitespace so SVG/XML declarations are recognized when
     * editors or tools prefix the file (common for {@code <svg>} and {@code <?xml}).
     */
    private static int skipLeadingBomAndAsciiWhitespace(byte[] bytes) {
        int i = 0;
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            i = 3;
        }
        while (i < bytes.length) {
            byte b = bytes[i];
            if (b == ' ' || b == '\t' || b == '\n' || b == '\r') {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private record CacheEntry(CimdMetadataDocument document, CachedLogo logo) {}
}
