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
package io.gravitee.am.management.handlers.management.api.resources.dashboard;

import io.swagger.annotations.Api;

import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Path("/dashboard")
@Api(tags = {"dashboard"})
public class DashboardResource {

    @Context
    private ResourceContext resourceContext;

    @Deprecated
    @Path("clients")
    public DashboardClientsResource getDashboardClientsResource() {
        return resourceContext.getResource(DashboardClientsResource.class);
    }

    @Path("applications")
    public DashboardApplicationsResource getDashboardApplicationsResource() {
        return resourceContext.getResource(DashboardApplicationsResource.class);
    }

    @Path("tokens")
    public DashboardTokensResource getDashboardTokensResource() {
        return resourceContext.getResource(DashboardTokensResource.class);
    }

}
