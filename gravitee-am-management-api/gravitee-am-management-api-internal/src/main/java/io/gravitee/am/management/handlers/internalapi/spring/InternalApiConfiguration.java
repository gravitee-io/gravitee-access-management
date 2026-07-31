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
package io.gravitee.am.management.handlers.internalapi.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.handlers.internalapi.endpoints.CreateDataPlaneEndpoint;
import io.gravitee.am.management.handlers.internalapi.endpoints.DeleteDataPlaneEndpoint;
import io.gravitee.am.management.handlers.internalapi.endpoints.GetDataPlaneEndpoint;
import io.gravitee.am.management.handlers.internalapi.InternalApiService;
import io.gravitee.am.management.handlers.internalapi.endpoints.ListDataPlanesEndpoint;
import io.gravitee.am.service.DataPlaneDefinitionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the endpoints served by the node technical API. {@link InternalApiService} is the
 * lifecycle component that registers them, so it has to be listed in the node's components as well.
 *
 * @author GraviteeSource Team
 */
@Configuration
public class InternalApiConfiguration {

    @Bean
    public CreateDataPlaneEndpoint createDataPlaneEndpoint(DataPlaneDefinitionService dataPlaneDefinitionService, ObjectMapper objectMapper) {
        return new CreateDataPlaneEndpoint(dataPlaneDefinitionService, objectMapper);
    }

    @Bean
    public ListDataPlanesEndpoint listDataPlanesEndpoint(DataPlaneDefinitionService dataPlaneDefinitionService, ObjectMapper objectMapper) {
        return new ListDataPlanesEndpoint(dataPlaneDefinitionService, objectMapper);
    }

    @Bean
    public GetDataPlaneEndpoint getDataPlaneEndpoint(DataPlaneDefinitionService dataPlaneDefinitionService, ObjectMapper objectMapper) {
        return new GetDataPlaneEndpoint(dataPlaneDefinitionService, objectMapper);
    }

    @Bean
    public DeleteDataPlaneEndpoint deleteDataPlaneEndpoint(DataPlaneDefinitionService dataPlaneDefinitionService, ObjectMapper objectMapper) {
        return new DeleteDataPlaneEndpoint(dataPlaneDefinitionService, objectMapper);
    }

    @Bean
    public InternalApiService internalApiService() {
        return new InternalApiService();
    }
}
