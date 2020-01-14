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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.management.handlers.management.api.model.AnalyticsParam;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.management.service.AnalyticsService;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AnalyticsResource extends AbstractResource {

    @Autowired
    private AnalyticsService analyticsService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Find domain analytics")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Analytics successfully fetched"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.DOMAIN_ANALYTICS, acls = RolePermissionAction.READ)
    })
    public void get(@PathParam("domain") String domain,
                    @BeanParam AnalyticsParam param,
                    @Suspended final AsyncResponse response) {

        // validate param
        param.validate();

        AnalyticsQuery query = new AnalyticsQuery();
        query.setType(param.getType());
        query.setField(param.getField());
        query.setFrom(param.getFrom());
        query.setTo(param.getTo());
        query.setDomain(domain);
        query.setInterval(param.getInterval());
        query.setSize(param.getSize());

        analyticsService.execute(query)
                .map(result -> Response.ok(result).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));

    }
}
