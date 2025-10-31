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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.model.UserAuditParam;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.resources.utils.FilterUtils;
import io.gravitee.am.management.service.AuditService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.common.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuditsResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private AuditService auditService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listUserAuditLogs",
            summary = "Get a user audit logs",
            description = "User must have the DOMAIN_USER[READ] permission on the specified domain " +
                    "or DOMAIN_USER[READ] permission on the specified environment " +
                    "or DOMAIN_USER[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User audit logs successfully fetched",
                    content = @Content(mediaType =  "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = Audit.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("user") String user,
            @BeanParam UserAuditParam param,
            @Suspended final AsyncResponse response) {

        AuditReportableCriteria.Builder queryBuilder = new AuditReportableCriteria.Builder()
                .from(param.getFrom())
                .to(param.getTo())
                .status(param.getStatus())
                .userId(user);

        if (param.getType() != null) {
            queryBuilder.types(Collections.singletonList(param.getType()));
        }

        User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_USER, Acl.READ)
                .andThen(auditService.search(domain, queryBuilder.build(), param.getPage(), param.getSize()))
                .flatMap(auditPage -> hasPermission(authenticatedUser, ReferenceType.ORGANIZATION, organizationId, Permission.ORGANIZATION_AUDIT, Acl.READ)
                        .map(hasPermission -> {
                            if (hasPermission) {
                                return auditPage;
                            } else {
                                return new Page<>(auditPage.getData().stream().map(FilterUtils::filterAuditInfos).collect(Collectors.toList()), auditPage.getCurrentPage(), auditPage.getTotalCount());
                            }
                        }))
                .subscribe(response::resume, response::resume);
    }

    @Path("{audit}")
    public UserAuditResource getUserAuditResource() {
        return resourceContext.getResource(UserAuditResource.class);
    }

}
