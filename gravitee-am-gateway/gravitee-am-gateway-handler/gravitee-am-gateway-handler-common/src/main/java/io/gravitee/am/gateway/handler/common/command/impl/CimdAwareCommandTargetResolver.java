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
package io.gravitee.am.gateway.handler.common.command.impl;

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataService;
import io.gravitee.am.gateway.handler.common.command.CommandTargetResolver;
import io.gravitee.am.model.CimdMetadataDocument;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.CimdMetadataDocumentService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;

/**
 * Adds the CIMD leg to the default target resolution. CIMD clients are synthesized
 * per-request and never enter the {@code ClientManager}, so they are enumerated from
 * the domain's persisted (non-expired) metadata documents — the durable store rather
 * than the per-node {@code CimdMetadataDocumentManager} cache, which is a size- and
 * TTL-bounded point-lookup cache that cannot enumerate and only holds what this node
 * has resolved. Only documents that themselves declare a {@code command_endpoint} are
 * targets — the opt-in is the RP's own metadata document, never inherited from the
 * template application.
 *
 * @author GraviteeSource Team
 */
@Slf4j
public class CimdAwareCommandTargetResolver implements CommandTargetResolver {

    private final DefaultCommandTargetResolver defaultCommandTargetResolver;
    private final Domain domain;
    private final String templateId;
    private final ClientSyncService clientSyncService;
    private final CimdMetadataDocumentService cimdMetadataDocumentService;
    private final CimdMetadataService cimdMetadataService;

    public CimdAwareCommandTargetResolver(DefaultCommandTargetResolver defaultCommandTargetResolver,
                                          Domain domain,
                                          String templateId,
                                          ClientSyncService clientSyncService,
                                          CimdMetadataDocumentService cimdMetadataDocumentService,
                                          CimdMetadataService cimdMetadataService) {
        this.defaultCommandTargetResolver = defaultCommandTargetResolver;
        this.domain = domain;
        this.templateId = templateId;
        this.clientSyncService = clientSyncService;
        this.cimdMetadataDocumentService = cimdMetadataDocumentService;
        this.cimdMetadataService = cimdMetadataService;
    }

    @Override
    public Flowable<Client> resolveTargets() {
        return Flowable.concat(defaultCommandTargetResolver.resolveTargets(), cimdTargets())
                .distinct(Client::getClientId);
    }

    private Flowable<Client> cimdTargets() {
        return clientSyncService.findById(templateId)
                .doOnComplete(() -> log.warn("CIMD template {} not found for domain {}, skipping CIMD command targets",
                        templateId, domain.getName()))
                .flatMapPublisher(template -> cimdMetadataDocumentService.findByDomain(domain.getId())
                        .filter(document -> !document.isExpired())
                        .filter(document -> document.getCommandEndpoint() != null && !document.getCommandEndpoint().isBlank())
                        .concatMapMaybe(document -> synthesize(document, template)));
    }

    /**
     * A single stale document must not sink the whole fan-out: synthesis failures are
     * skipped (the client will be re-validated on its next OIDC interaction anyway),
     * while stream-level errors (template lookup, document enumeration) propagate so
     * the job is retried.
     */
    private Maybe<Client> synthesize(CimdMetadataDocument document, Client template) {
        try {
            return Maybe.just(cimdMetadataService.synthesizeFromDocument(document, template));
        } catch (Exception e) {
            log.warn("Skipping CIMD command target {} of domain {}: stored metadata failed synthesis ({})",
                    document.getClientId(), domain.getName(), e.getMessage());
            return Maybe.empty();
        }
    }
}
