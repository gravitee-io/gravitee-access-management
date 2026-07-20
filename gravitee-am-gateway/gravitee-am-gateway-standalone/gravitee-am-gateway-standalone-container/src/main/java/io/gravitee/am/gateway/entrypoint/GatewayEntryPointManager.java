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
package io.gravitee.am.gateway.entrypoint;

import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.management.api.EntrypointRepository;
import io.gravitee.am.repository.management.api.EnvironmentRepository;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.gravitee.am.service.impl.AbstractEntryPointManager;
import io.gravitee.common.event.EventManager;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Gateway entrypoint cache, backed directly by the management repositories (the service layer is not
 * available in the gateway container context).
 *
 * @author GraviteeSource Team
 */
public class GatewayEntryPointManager extends AbstractEntryPointManager {

    private final EntrypointRepository entrypointRepository;
    private final OrganizationRepository organizationRepository;
    private final EnvironmentRepository environmentRepository;

    public GatewayEntryPointManager(EntrypointRepository entrypointRepository,
                                    OrganizationRepository organizationRepository,
                                    EnvironmentRepository environmentRepository,
                                    EventManager eventManager,
                                    Node node) {
        super(eventManager, node);
        this.entrypointRepository = entrypointRepository;
        this.organizationRepository = organizationRepository;
        this.environmentRepository = environmentRepository;
    }

    @Override
    protected Flowable<Entrypoint> loadOrganizationEntrypoints(String organizationId) {
        return entrypointRepository.findAll(organizationId);
    }

    @Override
    protected Flowable<Entrypoint> loadEnvironmentEntrypoints(String environmentId) {
        return environmentRepository.findById(environmentId)
                .flatMapPublisher(environment -> entrypointRepository.findByEnvironment(environment.getOrganizationId(), environmentId));
    }

    @Override
    protected Flowable<String> allOrganizationIds() {
        return organizationRepository.findAll().map(Organization::getId);
    }

    @Override
    protected Maybe<Entrypoint> findEntrypointById(String entrypointId, ReferenceType referenceType, String referenceId) {
        return entrypointRepository.findById(entrypointId);
    }
}
