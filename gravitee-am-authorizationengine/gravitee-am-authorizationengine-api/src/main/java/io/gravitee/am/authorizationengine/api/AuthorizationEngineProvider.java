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
package io.gravitee.am.authorizationengine.api;

import io.gravitee.am.authorizationengine.api.model.AuthorizationEngineRequest;
import io.gravitee.am.authorizationengine.api.model.AuthorizationEngineResponse;
import io.gravitee.am.common.plugin.AmPluginProvider;
import io.gravitee.common.service.Service;
import io.reactivex.rxjava3.core.Single;

import java.util.Optional;

/**
 * @author GraviteeSource Team
 */
public interface AuthorizationEngineProvider extends Service<AuthorizationEngineProvider>, AmPluginProvider {

    /**
     * Check authorization for a given request.
     * This method is called by the Gateway to verify if a subject has permission
     * to perform an action on a resource.
     *
     * @param request Authorization request
     * @return Single<AuthorizationResponse> containing the decision and optional context
     */
    Single<AuthorizationEngineResponse> check(AuthorizationEngineRequest request);

    /**
     * Hot-reload policy, data, and schema configuration without restarting the provider.
     * Implementations that support zero-downtime updates (e.g., sidecar-based engines)
     * override this method to push new configuration to the running engine.
     *
     * @param policy Engine-specific policy text (e.g., Cedar policy syntax)
     * @param data   Engine-specific data payload (JSON string, e.g., entity definitions)
     * @param schema Optional schema definition (JSON string)
     */
    default void updateConfig(String policy, String data, String schema) {
        // No-op by default; providers supporting hot-reload override this
    }

    /**
     * Get the JAX-RS management resource instance for Management API.
     * The plugin can return a JAX-RS resource object with @Path annotations
     * to expose custom REST endpoints for managing the authorization engine.
     *
     * @return Optional resource instance, empty if the plugin doesn't expose REST API
     */
    default Optional<Object> getManagementResource() {
        return Optional.empty();
    }
}
