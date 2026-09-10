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
package io.gravitee.am.service.dataplane;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.service.DataPlaneDefinitionService;
import io.gravitee.am.service.model.DataPlaneDefinitionSummary;
import io.gravitee.am.service.model.NewDataPlaneDefinition;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * Changes a data plane definition and, on the node serving the request, the registry that runs it.
 * The other nodes pick the change up from the sync event the definition change publishes.
 *
 * @author GraviteeSource Team
 */
public class DataPlaneProvisioningService {

    private final DataPlaneDefinitionService dataPlaneDefinitionService;
    private final ProvisionedDataPlaneLoader provisionedDataPlaneLoader;

    public DataPlaneProvisioningService(DataPlaneDefinitionService dataPlaneDefinitionService,
                                        ProvisionedDataPlaneLoader provisionedDataPlaneLoader) {
        this.dataPlaneDefinitionService = dataPlaneDefinitionService;
        this.provisionedDataPlaneLoader = provisionedDataPlaneLoader;
    }

    public Single<DataPlaneDefinitionSummary> provision(NewDataPlaneDefinition definition, ManagedBy managedBy, User principal) {
        return dataPlaneDefinitionService.create(definition, managedBy, principal)
                .flatMap(summary -> provisionedDataPlaneLoader.activate(summary.id()).toSingleDefault(summary));
    }

    public Single<DataPlaneDefinitionSummary> reprovision(String id, NewDataPlaneDefinition definition, User principal) {
        return dataPlaneDefinitionService.update(id, definition, principal)
                .flatMap(summary -> provisionedDataPlaneLoader.activate(summary.id()).toSingleDefault(summary));
    }

    public Completable deprovision(String id, User principal) {
        return dataPlaneDefinitionService.delete(id, principal)
                .andThen(Completable.fromAction(() -> provisionedDataPlaneLoader.deactivate(id)));
    }
}
