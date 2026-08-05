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
package io.gravitee.am.management.handlers.internalapi.endpoints;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.DataPlaneDefinitionService;
import io.gravitee.am.service.dataplane.ProvisionedDataPlaneLoader;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.ext.web.RoutingContext;

/**
 * Removes a provisioned data plane: {@code DELETE /_node/dataplanes/:id}. Refused with a 409 while
 * a domain still points at it.
 *
 * @author GraviteeSource Team
 */
public class DeleteDataPlaneEndpoint extends AbstractInternalApiEndpoint {

    static final String PARAM_ID = "id";

    private final DataPlaneDefinitionService dataPlaneDefinitionService;
    private final DataPlaneRegistry dataPlaneRegistry;
    private final ProvisionedDataPlaneLoader provisionedDataPlaneLoader;

    public DeleteDataPlaneEndpoint(DataPlaneDefinitionService dataPlaneDefinitionService,
                                   DataPlaneRegistry dataPlaneRegistry,
                                   ProvisionedDataPlaneLoader provisionedDataPlaneLoader,
                                   ObjectMapper objectMapper) {
        super(objectMapper);
        this.dataPlaneDefinitionService = dataPlaneDefinitionService;
        this.dataPlaneRegistry = dataPlaneRegistry;
        this.provisionedDataPlaneLoader = provisionedDataPlaneLoader;
    }

    @Override
    public HttpMethod method() {
        return HttpMethod.DELETE;
    }

    @Override
    public String path() {
        return "/dataplanes/:" + PARAM_ID;
    }

    @Override
    public void handle(RoutingContext context) {
        String id = context.pathParam(PARAM_ID);

        dataPlaneDefinitionService.delete(id)
                .subscribe(
                        () -> {
                            // this node stops serving it now; the others follow on the sync event
                            dataPlaneRegistry.unregister(id);
                            provisionedDataPlaneLoader.forget(id);
                            context.response().setStatusCode(HttpStatusCode.NO_CONTENT_204).end();
                        },
                        throwable -> respondFailure(context, throwable, "Unable to delete the data plane definition"));
    }
}
