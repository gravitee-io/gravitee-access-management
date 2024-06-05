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
import io.gravitee.am.management.handlers.management.api.model.FlowEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.resources.utils.FlowUtils;
import io.gravitee.am.management.service.PolicyPluginService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.FlowService;
import io.gravitee.am.service.exception.FlowNotFoundException;
import io.gravitee.am.service.validators.flow.FlowValidator;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FlowResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private FlowService flowService;

    @Autowired
    private PolicyPluginService policyPluginService;

    @Autowired
    private FlowValidator flowValidator;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get a flow",
            operationId = "getDomainFlow",
            description = "User must have the DOMAIN_FLOW[READ] permission on the specified domain " +
                    "or DOMAIN_FLOW[READ] permission on the specified environment " +
                    "or DOMAIN_FLOW[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flow",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FlowEntity.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("flow") String flow,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_FLOW, Acl.READ)
                .andThen(flowService.findById(ReferenceType.DOMAIN, domain, flow)
                        .switchIfEmpty(Maybe.error(new FlowNotFoundException(flow)))
                        .map(FlowEntity::new))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update a flow",
            operationId = "updateDomainFlow",
            description = "User must have the DOMAIN_FLOW[UPDATE] permission on the specified domain " +
                    "or DOMAIN_FLOW[UPDATE] permission on the specified environment " +
                    "or DOMAIN_FLOW[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Flow successfully updated",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FlowEntity.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("flow") String flow,
            @Parameter(name = "flow", required = true) @Valid @NotNull io.gravitee.am.service.model.Flow updateFlow,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_FLOW, Acl.UPDATE)
                .andThen(FlowUtils.checkPoliciesDeployed(policyPluginService, updateFlow))
                .andThen(flowValidator.validate(updateFlow))
                .andThen(flowService.update(ReferenceType.DOMAIN, domain, flow, convert(updateFlow), authenticatedUser)
                        .map(FlowEntity::new))
                .subscribe(response::resume, response::resume);
    }

    private static Flow convert(io.gravitee.am.service.model.Flow flow) {
        Flow flowToUpdate = new Flow();
        flowToUpdate.setType(flow.getType());
        flowToUpdate.setName(flow.getName());
        flowToUpdate.setEnabled(flow.isEnabled());
        flowToUpdate.setCondition(flow.getCondition());
        flowToUpdate.setPre(flow.getPre());
        flowToUpdate.setPost(flow.getPost());
        return flowToUpdate;
    }
}
