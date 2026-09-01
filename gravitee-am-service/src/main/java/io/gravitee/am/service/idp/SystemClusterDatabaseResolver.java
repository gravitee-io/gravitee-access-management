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
package io.gravitee.am.service.idp;

import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import static io.gravitee.am.repository.BackendConfigurationUtils.SYSTEM_CLUSTER;
import static io.gravitee.am.repository.BackendConfigurationUtils.DEFAULT_SYSTEM_CLUSTER;

import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.Scope;
import lombok.CustomLog;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static io.gravitee.am.plugins.dataplane.core.DataPlaneUtility.evaluateDataPlaneId;
import static io.gravitee.am.repository.BackendConfigurationUtils.getMongoDatabaseName;

/**
 * Names the database a mongo identity provider bound to the system cluster actually reads at
 * runtime. The node that serves the provider is the authority on that name, so this resolver
 * follows the same settings it does:
 *
 * <ul>
 *     <li>{@code repositories.system-cluster: management} — the provider shares the management
 *     store, whose database comes from the {@code repositories.management.mongodb} block;</li>
 *     <li>{@code repositories.system-cluster: gateway} — the provider shares the data plane's store.</li>
 * </ul>
 *
 * @author GraviteeSource Team
 */
@Component
@CustomLog
public class SystemClusterDatabaseResolver {
    static final String MONGODB_TYPE = "mongodb";
    static final String DEFAULT_DATABASE = "gravitee-am";

    private final RepositoriesEnvironment environment;
    private final DataPlaneRegistry dataPlaneRegistry;

    public SystemClusterDatabaseResolver(RepositoriesEnvironment environment, @Lazy DataPlaneRegistry dataPlaneRegistry) {
        this.environment = environment;
        this.dataPlaneRegistry = dataPlaneRegistry;
    }

    /**
     * @return the database the identity provider reads at runtime, or {@code null} when the
     * platform cannot name one — the caller then leaves the stored value alone rather than
     * replacing it with a guess.
     */
    public String resolve(IdentityProvider identityProvider) {
        final String scope = environment.getProperty(SYSTEM_CLUSTER, String.class, DEFAULT_SYSTEM_CLUSTER);
        if (Scope.GATEWAY.getName().equals(scope)) {
            final String fromDataPlane = resolveFromDataPlane(identityProvider);
            if (fromDataPlane != null) {
                return fromDataPlane;
            }
        }
        return resolveFromScope(scope);
    }

    private String resolveFromDataPlane(IdentityProvider identityProvider) {
        final String dataPlaneId = evaluateDataPlaneId(identityProvider.getDataPlaneId(), IdentityProvider.class, identityProvider.getId());
        final Optional<DataPlaneDescription> description = findDataPlane(dataPlaneId);
        if (description.isEmpty()) {
            log.warn("Identity provider {} names the unknown data plane {}: falling back to the system cluster scope settings",
                    identityProvider.getId(), dataPlaneId);
            return null;
        }
        if (!MONGODB_TYPE.equalsIgnoreCase(description.get().type())) {
            return null;
        }
        return getMongoDatabaseName(description.get().propertiesBase(), environment);
    }

    private Optional<DataPlaneDescription> findDataPlane(String dataPlaneId) {
        // getDataPlanes rather than getProviderById: naming a database must not start a connection.
        return dataPlaneRegistry.getDataPlanes().stream()
                .filter(description -> dataPlaneId.equals(description.id()))
                .findFirst();
    }

    /**
     * Mirrors {@code MongoConnectionProvider#getDatabaseName}, which is private to the repository
     * plugin. A node that resolves the database differently from this method reads one value and
     * this one names another, so the two must stay in step.
     */
    private String resolveFromScope(String scope) {
        final String resolvedScope = resolveDatabaseScope(scope);
        // Only a mongo repository names a database. The packaged gravitee.yml still carries a mongodb
        // block on a jdbc platform, so without this we would name a database that has no store.
        if (!MONGODB_TYPE.equalsIgnoreCase(environment.getProperty("repositories." + resolvedScope + ".type", MONGODB_TYPE))) {
            return null;
        }
        return getMongoDatabaseName("repositories." + resolvedScope, environment);
    }

    /**
     * The gateway scope reuses the management settings unless it is told not to, so the database of
     * a provider on the gateway scope usually comes from the management block.
     */
    private String resolveDatabaseScope(String scope) {
        if (DEFAULT_SYSTEM_CLUSTER.equals(scope)) {
            return scope;
        }
        final boolean useManagementSettings =
                environment.getProperty("repositories." + scope + ".use-management-settings", Boolean.class, true);
        return useManagementSettings ? DEFAULT_SYSTEM_CLUSTER : scope;
    }
}
