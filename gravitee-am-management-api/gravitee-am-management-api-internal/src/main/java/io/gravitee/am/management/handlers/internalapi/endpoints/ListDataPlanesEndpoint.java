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
import io.gravitee.am.service.DataPlaneDefinitionService;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.ext.web.RoutingContext;

/**
 * Lists the provisioned data planes: {@code GET /_node/dataplanes}.
 *
 * @author GraviteeSource Team
 */
public class ListDataPlanesEndpoint extends AbstractInternalApiEndpoint {

    private final DataPlaneDefinitionService dataPlaneDefinitionService;

    public ListDataPlanesEndpoint(DataPlaneDefinitionService dataPlaneDefinitionService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.dataPlaneDefinitionService = dataPlaneDefinitionService;
    }

    @Override
    public HttpMethod method() {
        return HttpMethod.GET;
    }

    @Override
    public String path() {
        return "/dataplanes";
    }

    @Override
    public void handle(RoutingContext context) {
        dataPlaneDefinitionService.findAll()
                .toList()
                .subscribe(
                        summaries -> respond(context, HttpStatusCode.OK_200, summaries),
                        throwable -> respondFailure(context, throwable, "Unable to list the data plane definitions"));
    }
}
