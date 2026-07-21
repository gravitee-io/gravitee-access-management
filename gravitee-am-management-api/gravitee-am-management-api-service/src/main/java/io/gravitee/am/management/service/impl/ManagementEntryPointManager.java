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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.EntrypointService;
import io.gravitee.am.service.EnvironmentService;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.exception.EntrypointNotFoundException;
import io.gravitee.am.service.exception.EnvironmentNotFoundException;
import io.gravitee.am.service.impl.AbstractEntryPointManager;
import io.gravitee.common.event.EventManager;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.stereotype.Component;

/**
 * Management API entrypoint cache, backed by the service layer (the repositories are not directly
 * injectable in this context).
 *
 * @author GraviteeSource Team
 */
@Component
public class ManagementEntryPointManager extends AbstractEntryPointManager {

    private final EntrypointService entrypointService;
    private final OrganizationService organizationService;
    private final EnvironmentService environmentService;

    public ManagementEntryPointManager(EntrypointService entrypointService,
                                       OrganizationService organizationService,
                                       EnvironmentService environmentService,
                                       EventManager eventManager,
                                       Node node) {
        super(eventManager, node);
        this.entrypointService = entrypointService;
        this.organizationService = organizationService;
        this.environmentService = environmentService;
    }

    @Override
    protected Flowable<Entrypoint> findEntrypointsByOrganization(String organizationId) {
        return entrypointService.findAll(organizationId);
    }

    @Override
    protected Flowable<Entrypoint> findEntrypointsByEnvironment(String organizationId, String environmentId) {
        return entrypointService.findByEnvironment(organizationId, environmentId);
    }

    @Override
    protected Maybe<Environment> findEnvironmentById(String environmentId) {
        return environmentService.findById(environmentId).toMaybe();
    }

    @Override
    protected Flowable<Organization> findAllOrganizations() {
        return organizationService.findAll();
    }

    @Override
    protected Maybe<Entrypoint> findEntrypointById(String entrypointId, ReferenceType referenceType, String referenceId) {
        Single<String> organizationId = referenceType == ReferenceType.ENVIRONMENT
                ? environmentService.findById(referenceId).map(Environment::getOrganizationId)
                : Single.just(referenceId);

        return organizationId.flatMapMaybe(orgId -> entrypointService.findById(entrypointId, orgId)
                .toMaybe()
                .onErrorComplete(EntrypointNotFoundException.class::isInstance))
                .onErrorComplete(EnvironmentNotFoundException.class::isInstance);
    }
}
