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
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.FlowService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationFlowsResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private FlowService flowService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List registered flows for an application",
            nickname = "listAppFlows",
            notes = "User must have the APPLICATION_FLOW[LIST] permission on the specified domain " +
                    "or APPLICATION_FLOW[LIST] permission on the specified environment " +
                    "or APPLICATION_FLOW[LIST] permission on the specified organization. " +
                    "Except if user has APPLICATION_FLOW[READ] permission on the domain, environment or organization, each returned flow is filtered and contains only basic information such as id and name and isEnabled.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered flows for an application", response = FlowEntity.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @Suspended final AsyncResponse response) {

        User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.APPLICATION_FLOW, Acl.LIST)
                .andThen(hasAnyPermission(authenticatedUser, organizationId, environmentId, domain, Permission.APPLICATION_FLOW, Acl.READ)
                        .flatMapPublisher(hasPermission ->
                                flowService.findByApplication(ReferenceType.DOMAIN, domain, application).map(flow -> filterFlowInfos(hasPermission, flow)))
                        .toList())
                        .subscribe(response::resume, response::resume);
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create or update list of flows",
            nickname = "defineAppFlows",
            notes = "User must have the APPLICATION_FLOW[UPDATE] permission on the specified domain " +
                    "or APPLICATION_FLOW[UPDATE] permission on the specified environment " +
                    "or APPLICATION_FLOW[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Flows successfully updated", response = FlowEntity.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("application") String application,
            @ApiParam(name = "flows", required = true) @Valid @NotNull final List<io.gravitee.am.service.model.Flow> flows,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.APPLICATION_FLOW, Acl.UPDATE)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> flowService.createOrUpdate(ReferenceType.DOMAIN, domain, application, convert(flows), authenticatedUser))
                        .map(updatedFlows -> updatedFlows.stream().map(FlowEntity::new).collect(Collectors.toList())))
                .subscribe(response::resume, response::resume);
    }

    @Path("{flow}")
    public ApplicationFlowResource getFlowResource() {
        return resourceContext.getResource(ApplicationFlowResource.class);
    }

    private FlowEntity filterFlowInfos(Boolean hasPermission, Flow flow) {
        if (hasPermission) {
            return new FlowEntity(flow);
        }

        FlowEntity filteredFlow = new FlowEntity();
        filteredFlow.setId(flow.getId());
        filteredFlow.setName(flow.getName());
        filteredFlow.setEnabled(flow.isEnabled());

        return filteredFlow;
    }

    private static List<Flow> convert(List<io.gravitee.am.service.model.Flow> flows) {
        return flows.stream()
                .map(ApplicationFlowsResource::convert)
                .collect(Collectors.toList());
    }

    private static Flow convert(io.gravitee.am.service.model.Flow flow) {
        Flow flowToUpsert = new Flow();
        flowToUpsert.setId(flow.getId());
        flowToUpsert.setType(flow.getType());
        flowToUpsert.setName(flow.getName());
        flowToUpsert.setEnabled(flow.isEnabled());
        flowToUpsert.setCondition(flow.getCondition());
        flowToUpsert.setPre(flow.getPre());
        flowToUpsert.setPost(flow.getPost());
        return flowToUpsert;
    }
}
