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
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Per-domain in-memory cache for CIMD metadata documents and their associated logos.
 *
 * @author GraviteeSource Team
 */
public class CimdMetadataDocumentManager extends AbstractService<CimdMetadataDocumentManager>
        implements EventListener<CimdMetadataEvent, Payload>, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(CimdMetadataDocumentManager.class);

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private CimdMetadataDocumentService cimdMetadataDocumentService;

    private final Cache<String, CimdMetadataDocument> metadataCache;
    private final Cache<String, CachedLogo> logoCache;
    private final ConcurrentHashMap<String, String> clientIdToLogoUri = new ConcurrentHashMap<>();

    public CimdMetadataDocumentManager(CIMDSettings settings) {
        this.metadataCache = CacheBuilder.newBuilder()
                .maximumSize(settings.getCacheMaxEntries())
                .expireAfterWrite(settings.getCacheTtlSeconds(), TimeUnit.SECONDS)
                .build();
        this.logoCache = CacheBuilder.newBuilder()
                .maximumSize(settings.getCacheMaxEntries())
                .expireAfterWrite(settings.getCacheTtlSeconds(), TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void afterPropertiesSet() {
        logger.info("Pre-loading CIMD metadata documents for domain {}", domain.getName());
        cimdMetadataDocumentService.findByDomain(domain.getId())
                .filter(doc -> !doc.isExpired())
                .subscribe(
                        doc -> metadataCache.put(doc.getClientId(), doc),
                        error -> logger.error("Unable to pre-load CIMD documents for domain {}", domain.getName(), error)
                );
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        logger.info("Register event listener for CIMD metadata events for domain {}", domain.getName());
        eventManager.subscribeForEvents(this, CimdMetadataEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        logger.info("Dispose event listener for CIMD metadata events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, CimdMetadataEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<CimdMetadataEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN
                && domain.getId().equals(event.content().getReferenceId())) {
            final String clientId = event.content().getId();
            switch (event.type()) {
                case UPDATE, UNDEPLOY -> {
                    metadataCache.invalidate(clientId);
                    String logoUri = clientIdToLogoUri.remove(clientId);
                    if (logoUri != null) {
                        logoCache.invalidate(logoUri);
                    }
                    logger.debug("Domain {} evicted CIMD cache for clientId {}", domain.getName(), clientId);
                }
                case DEPLOY -> logger.debug("Domain {} received CIMD DEPLOY for clientId {}", domain.getName(), clientId);
            }
        }
    }

    /**
     * Returns a non-expired cached document, or empty if absent or expired.
     */
    public Optional<CimdMetadataDocument> get(String clientId) {
        CimdMetadataDocument doc = metadataCache.getIfPresent(clientId);
        if (doc == null || doc.isExpired()) {
            if (doc != null) {
                metadataCache.invalidate(clientId);
            }
            return Optional.empty();
        }
        return Optional.of(doc);
    }

    /**
     * Stores a DB-sourced document in the metadata cache and records any logo_uri mapping.
     */
    public void put(String clientId, CimdMetadataDocument doc) {
        metadataCache.put(clientId, doc);
        extractAndRecordLogoUri(clientId, doc.getMetadata());
    }

    /**
     * Constructs a document from a freshly fetched metadata JSON and stores it in the metadata cache.
     */
    public void put(String clientId, JsonObject metadata, Duration ttl) {
        final CimdMetadataDocument doc = CimdMetadataDocument.of(domain.getId(), clientId, metadata.encode(), ttl);
        metadataCache.put(clientId, doc);
        recordLogoUri(clientId, metadata.getString("logo_uri"));
    }

    /**
     * Records the logo_uri associated with a clientId so that logo cache entries can be
     * co-evicted when the metadata document is invalidated.
     */
    public void recordLogoUri(String clientId, String logoUri) {
        if (logoUri != null && !logoUri.isBlank()) {
            clientIdToLogoUri.put(clientId, logoUri);
        }
    }

    public Optional<CachedLogo> getLogo(String logoUri) {
        return Optional.ofNullable(logoCache.getIfPresent(logoUri));
    }

    public Optional<CachedLogo> getLogoByClientId(String clientId) {
        String logoUri = clientIdToLogoUri.get(clientId);
        if (logoUri == null) {
            return Optional.empty();
        }
        return getLogo(logoUri);
    }

    public void putLogo(String logoUri, CachedLogo logo) {
        logoCache.put(logoUri, logo);
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
        return "application/octet-stream";
    }

    private void extractAndRecordLogoUri(String clientId, String rawMetadata) {
        if (rawMetadata == null) {
            return;
        }
        try {
            recordLogoUri(clientId, new JsonObject(rawMetadata).getString("logo_uri"));
        } catch (Exception ignored) {
            // not fatal — logo association is best-effort
        }
    }
}
