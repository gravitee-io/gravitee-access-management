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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.gravitee.am.service.DataPlaneDefinitionService;
import io.gravitee.am.service.dataplane.ProvisionedDataPlaneLoader;
import io.gravitee.am.service.model.NewDataPlaneDefinition;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import io.vertx.ext.web.RoutingContext;
import lombok.CustomLog;

/**
 * Provisions a data plane definition: {@code POST /_node/dataplanes}. Responds with the
 * credential-free summary.
 *
 * Registering the definition is a separate step from persisting it, because the service deliberately
 * keeps the raw configuration to itself: the loader reads the definition back out of the repository.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public class CreateDataPlaneEndpoint extends AbstractInternalApiEndpoint {

    private final DataPlaneDefinitionService dataPlaneDefinitionService;
    private final ProvisionedDataPlaneLoader provisionedDataPlaneLoader;

    public CreateDataPlaneEndpoint(DataPlaneDefinitionService dataPlaneDefinitionService,
                                   ProvisionedDataPlaneLoader provisionedDataPlaneLoader,
                                   ObjectMapper objectMapper) {
        super(objectMapper);
        this.dataPlaneDefinitionService = dataPlaneDefinitionService;
        this.provisionedDataPlaneLoader = provisionedDataPlaneLoader;
    }

    @Override
    public HttpMethod method() {
        return HttpMethod.POST;
    }

    @Override
    public String path() {
        return "/dataplanes";
    }

    @Override
    public void handle(RoutingContext context) {
        NewDataPlaneDefinition payload;
        try {
            payload = objectMapper.readValue(context.body().asString(), NewDataPlaneDefinition.class);
        } catch (UnrecognizedPropertyException e) {
            log.warn("Unable to read the submitted data plane definition", e);
            respondError(context, HttpStatusCode.BAD_REQUEST_400, "Unable to read the data plane definition, unknown field [" + e.getPropertyName() + "]");
            return;
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException e) {
            // the body holds credentials, so parse detail stays in the log
            log.warn("Unable to read the submitted data plane definition", e);
            respondError(context, HttpStatusCode.BAD_REQUEST_400, "Unable to read the data plane definition, expected a JSON object");
            return;
        }

        dataPlaneDefinitionService.create(payload)
                .flatMap(summary -> provisionedDataPlaneLoader.register(summary.id()).andThen(Single.just(summary)))
                .subscribe(
                        summary -> respond(context, HttpStatusCode.CREATED_201, summary),
                        throwable -> respondFailure(context, throwable, "Unable to create the data plane definition"));
    }
}
