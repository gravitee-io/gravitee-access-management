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

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.AuditService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuditResource extends AbstractResource {

    @Autowired
    private AuditService auditService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get an audit log",
            description = "User must have the DOMAIN_AUDIT[READ] permission on the specified domain " +
                    "or DOMAIN_AUDIT[READ] permission on the specified environment " +
                    "or DOMAIN_AUDIT[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit log successfully fetched",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Audit.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("audit") String audit,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_AUDIT, Acl.READ)
                .andThen(auditService.findById(domain, audit))
                .subscribe(response::resume, response::resume);
    }
}