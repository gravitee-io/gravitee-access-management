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
package io.gravitee.am.management.handlers.management.api.resources.platform.reporters;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.management.service.AuditReporterManager;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.exception.DomainMasterNotFoundException;
import io.gravitee.am.service.exception.ReporterNotFoundException;
import io.gravitee.am.service.model.UpdateReporter;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ReporterResource extends AbstractResource {

    @Autowired
    private ReporterService reporterService;

    @Autowired
    private AuditReporterManager auditReporterManager;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a reporter")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Reporter successfully fetched", response = Reporter.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_REPORTER, acls = RolePermissionAction.READ)
    })
    public void get(@PathParam("reporter") String reporter,
                    @Suspended final AsyncResponse response) {
        domainService.findMaster()
                .switchIfEmpty(Maybe.error(new DomainMasterNotFoundException()))
                .flatMap(masterDomain -> reporterService.findById(reporter)
                        .switchIfEmpty(Maybe.error(new ReporterNotFoundException(reporter)))
                        .map(reporter1 -> {
                            if (!reporter1.getDomain().equalsIgnoreCase(masterDomain.getId())) {
                                throw new BadRequestException("Reporter does not belong to domain");
                            }
                            return Response.ok(reporter1).build();
                        })
                )
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update a reporter")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Reporter successfully updated", response = Reporter.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_REPORTER, acls = RolePermissionAction.UPDATE)
    })
    public void update(@PathParam("reporter") String reporter,
                       @ApiParam(name = "reporter", required = true) @Valid @NotNull UpdateReporter updateReporter,
                       @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findMaster()
                .switchIfEmpty(Maybe.error(new DomainMasterNotFoundException()))
                .flatMapSingle(masterDomain -> reporterService.update(masterDomain.getId(), reporter, updateReporter, authenticatedUser))
                .doOnSuccess(reporter1 -> auditReporterManager.reloadReporter(reporter1))
                .map(reporter1 -> Response.ok(reporter1).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }
}
