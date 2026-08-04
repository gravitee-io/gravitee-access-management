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
package io.gravitee.am.management.handlers.internalapi;

import io.gravitee.am.management.handlers.internalapi.endpoints.CreateDataPlaneEndpoint;
import io.gravitee.am.management.handlers.internalapi.endpoints.DeleteDataPlaneEndpoint;
import io.gravitee.am.management.handlers.internalapi.endpoints.GetDataPlaneEndpoint;
import io.gravitee.am.management.handlers.internalapi.endpoints.ListDataPlanesEndpoint;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.management.http.endpoint.ManagementEndpointManager;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Registers the Management API endpoints served by the node technical API, on whichever port
 * {@code services.core.http.port} resolves to.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public class InternalApiService extends AbstractService<InternalApiService> {

    @Autowired
    private ManagementEndpointManager endpointManager;

    @Autowired
    private CreateDataPlaneEndpoint createDataPlaneEndpoint;

    @Autowired
    private ListDataPlanesEndpoint listDataPlanesEndpoint;

    @Autowired
    private GetDataPlaneEndpoint getDataPlaneEndpoint;

    @Autowired
    private DeleteDataPlaneEndpoint deleteDataPlaneEndpoint;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        endpointManager.register(createDataPlaneEndpoint);
        endpointManager.register(listDataPlanesEndpoint);
        endpointManager.register(getDataPlaneEndpoint);
        endpointManager.register(deleteDataPlaneEndpoint);
        log.info("Internal API endpoints have been registered");
    }
}
