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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import io.gravitee.am.management.handlers.management.api.model.AuditParam;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.management.service.AuditService;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.Collections;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"audit"})
public class AuditsResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private AuditService auditService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List audit logs for a security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List audit logs for a security domain", response = Audit.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.DOMAIN_AUDIT, acls = RolePermissionAction.READ)
    })
    public void list(@PathParam("domain") String domain,
                     @BeanParam AuditParam param,
                     @Suspended final AsyncResponse response) {

        AuditReportableCriteria.Builder queryBuilder = new AuditReportableCriteria.Builder()
                .from(param.getFrom())
                .to(param.getTo())
                .status(param.getStatus())
                .user(param.getUser());

        if (param.getType() != null) {
            queryBuilder.types(Collections.singletonList(param.getType()));
        }

        auditService.search(domain, queryBuilder.build(), param.getPage(), param.getSize())
                .map(pagedAudits -> Response.ok(pagedAudits).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @Path("{audit}")
    public AuditResource getAuditResource() {
        return resourceContext.getResource(AuditResource.class);
    }
}
