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
package io.gravitee.am.service.impl;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.CimdMetadataDocumentRepository;
import io.gravitee.am.service.CimdMetadataDocumentService;
import io.gravitee.am.service.EventService;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

import static io.gravitee.am.common.event.Type.CIMD_METADATA;

/**
 * @author GraviteeSource Team
 */
@Component
public class CimdMetadataDocumentServiceImpl implements CimdMetadataDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(CimdMetadataDocumentServiceImpl.class);

    @Lazy
    @Autowired
    private CimdMetadataDocumentRepository repository;

    @Autowired
    private EventService eventService;

    @Override
    public Maybe<CimdMetadataDocument> findByDomainAndClientId(String domainId, String clientId) {
        return repository.findByDomainAndClientId(domainId, clientId)
                .onErrorResumeNext(ex -> {
                    logger.error("Error finding CIMD document for domain {} clientId {}", domainId, clientId, ex);
                    return Maybe.empty();
                });
    }

    @Override
    public Flowable<CimdMetadataDocument> findByDomain(String domainId) {
        return repository.findByDomain(domainId)
                .onErrorResumeNext(ex -> {
                    logger.error("Error listing CIMD documents for domain {}", domainId, ex);
                    return Flowable.empty();
                });
    }

    @Override
    public Single<CimdMetadataDocument> upsert(Domain domain, String clientId, String metadataJson, Duration ttl) {
        final Date now = new Date();
        final Date expiresAt = new Date(now.getTime() + ttl.toMillis());

        return repository.findByDomainAndClientId(domain.getId(), clientId)
                .flatMapSingle(existing -> {
                    existing.setMetadata(metadataJson);
                    existing.setFetchedAt(now);
                    existing.setExpiresAt(expiresAt);
                    existing.setUpdatedAt(now);
                    return repository.update(existing)
                            .flatMap(saved -> publishEvent(saved, domain, Action.UPDATE));
                })
                .switchIfEmpty(Single.defer(() -> {
                    CimdMetadataDocument doc = new CimdMetadataDocument();
                    doc.setDomainId(domain.getId());
                    doc.setClientId(clientId);
                    doc.setMetadata(metadataJson);
                    doc.setFetchedAt(now);
                    doc.setExpiresAt(expiresAt);
                    doc.setUpdatedAt(now);
                    return repository.create(doc)
                            .flatMap(saved -> publishEvent(saved, domain, Action.CREATE));
                }))
                .onErrorResumeNext(ex -> {
                    logger.error("Error upserting CIMD document for domain {} clientId {}", domain.getId(), clientId, ex);
                    return Single.error(ex);
                });
    }

    @Override
    public Completable delete(String domainId, String clientId) {
        return repository.deleteByDomainAndClientId(domainId, clientId)
                .andThen(Single.defer(() -> {
                    Event event = new Event(CIMD_METADATA, new Payload(clientId, ReferenceType.DOMAIN, domainId, Action.DELETE));
                    Domain stub = new Domain();
                    stub.setId(domainId);
                    return eventService.create(event, stub);
                }))
                .ignoreElement()
                .onErrorResumeNext(ex -> {
                    logger.error("Error deleting CIMD document for domain {} clientId {}", domainId, clientId, ex);
                    return Completable.error(ex);
                });
    }

    private Single<CimdMetadataDocument> publishEvent(CimdMetadataDocument saved, Domain domain, Action action) {
        // Payload id = clientId so gateway managers can evict the local cache entry by key directly.
        Event event = new Event(CIMD_METADATA, new Payload(saved.getClientId(), ReferenceType.DOMAIN, domain.getId(), action));
        return eventService.create(event, domain).flatMap(e -> Single.just(saved));
    }
}
