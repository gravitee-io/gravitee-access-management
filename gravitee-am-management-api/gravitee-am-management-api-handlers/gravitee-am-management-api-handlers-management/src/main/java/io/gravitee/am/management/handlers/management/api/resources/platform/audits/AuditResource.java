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
package io.gravitee.am.management.handlers.management.api.resources.platform.audits;

import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.management.service.AuditService;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.AuditNotFoundException;
import io.gravitee.am.service.exception.DomainMasterNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

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
public class AuditResource {

    @Autowired
    private AuditService auditService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get an audit log")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Audit log successfully fetched", response = Audit.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.MANAGEMENT_AUDIT, acls = RolePermissionAction.READ)
    })
    public void get(@PathParam("audit") String audit,
            @Suspended final AsyncResponse response) {

        domainService.findMaster()
                .switchIfEmpty(Maybe.error(new DomainMasterNotFoundException()))
                .flatMap(masterDomain -> auditService.findById(masterDomain.getId(), audit))
                .switchIfEmpty(Maybe.error(new AuditNotFoundException(audit)))
                .map(audit1 -> Response.ok(audit1).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

}
