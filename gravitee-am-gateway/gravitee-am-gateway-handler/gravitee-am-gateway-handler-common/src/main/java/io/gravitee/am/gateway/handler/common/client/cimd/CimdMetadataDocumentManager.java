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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Per-domain in-memory cache for CIMD metadata documents.
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

    private final Cache<String, CimdMetadataDocument> localCache;

    public CimdMetadataDocumentManager(CIMDSettings settings) {
        this.localCache = CacheBuilder.newBuilder()
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
                        doc -> localCache.put(doc.getClientId(), doc),
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
                    localCache.invalidate(clientId);
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
        CimdMetadataDocument doc = localCache.getIfPresent(clientId);
        if (doc == null || doc.isExpired()) {
            if (doc != null) {
                localCache.invalidate(clientId);
            }
            return Optional.empty();
        }
        return Optional.of(doc);
    }

    /**
     * Stores a document in the local cache for the given effective TTL.
     */
    public void put(String clientId, CimdMetadataDocument doc) {
        localCache.put(clientId, doc);
    }

    /**
     * Constructs and stores a document in the local cache for the given effective TTL.
     */
    public void put(String clientId, String rawMetadata, Duration ttl) {
        put(clientId, CimdMetadataDocument.of(domain.getId(), clientId, rawMetadata, ttl));
    }
}
