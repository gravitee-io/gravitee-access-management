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
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.PolicyPluginService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Type;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.FlowService;
import io.gravitee.am.service.ProtectedResourceService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.exception.ProtectedResourceNotFoundException;
import io.gravitee.am.service.validators.flow.FlowValidator;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import jakarta.ws.rs.container.Suspended;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Policy Studio flows for a MCP server (protected resource).
 *
 * Only the {@link Type#TOKEN} flow is exposed: flows are persisted using the protected resource id
 * as the {@code application} discriminator so the gateway picks them up for the protected resource
 * acting as an OAuth client (see {@code ProtectedResource#toClient()} and {@code FlowManagerImpl}).
 *
 * @author GraviteeSource Team
 */
public class ProtectedResourceFlowsResource extends AbstractResource {

    @Autowired
    private DomainService domainService;

    @Autowired
    private ProtectedResourceService protectedResourceService;

    @Autowired
    private FlowService flowService;

    @Autowired
    private PolicyPluginService policyPluginService;

    @Autowired
    private FlowValidator flowValidator;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List registered flows for a protected resource",
            operationId = "listProtectedResourceFlows",
            description = "User must have the PROTECTED_RESOURCE_FLOW[LIST] permission on the specified resource " +
                    "or PROTECTED_RESOURCE_FLOW[LIST] permission on the specified domain " +
                    "or PROTECTED_RESOURCE_FLOW[LIST] permission on the specified environment " +
                    "or PROTECTED_RESOURCE_FLOW[LIST] permission on the specified organization. " +
                    "Except if user has PROTECTED_RESOURCE_FLOW[READ] permission on the domain, environment or organization, each returned flow is filtered and contains only basic information such as id and name and isEnabled.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List registered flows for a protected resource",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = FlowEntity.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("protected-resource") String protectedResourceId,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, ReferenceType.PROTECTED_RESOURCE, protectedResourceId, Permission.PROTECTED_RESOURCE_FLOW, Acl.LIST)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(__ -> protectedResourceService.findById(protectedResourceId))
                        .switchIfEmpty(Maybe.error(new ProtectedResourceNotFoundException(protectedResourceId)))
                        .ignoreElement())
                .andThen(hasAnyPermission(authenticatedUser, organizationId, environmentId, domainId, Permission.PROTECTED_RESOURCE_FLOW, Acl.READ)
                        .flatMapPublisher(hasPermission ->
                                flowService.findByApplication(ReferenceType.DOMAIN, domainId, protectedResourceId).map(flow -> filterFlowInfos(hasPermission, flow)))
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create or update list of flows",
            operationId = "defineProtectedResourceFlows",
            description = "User must have the PROTECTED_RESOURCE_FLOW[UPDATE] permission on the specified resource " +
                    "or PROTECTED_RESOURCE_FLOW[UPDATE] permission on the specified domain " +
                    "or PROTECTED_RESOURCE_FLOW[UPDATE] permission on the specified environment " +
                    "or PROTECTED_RESOURCE_FLOW[UPDATE] permission on the specified organization. " +
                    "Only the TOKEN flow can be configured for a protected resource.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flows successfully updated",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = FlowEntity.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("protected-resource") String protectedResourceId,
            @Parameter(name = "flows", required = true) @Valid @NotNull final List<io.gravitee.am.service.model.Flow> flows,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, ReferenceType.PROTECTED_RESOURCE, protectedResourceId, Permission.PROTECTED_RESOURCE_FLOW, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMap(__ -> protectedResourceService.findById(protectedResourceId))
                        .switchIfEmpty(Maybe.error(new ProtectedResourceNotFoundException(protectedResourceId)))
                        .ignoreElement())
                .andThen(checkTokenFlowOnly(flows))
                .andThen(FlowUtils.checkPoliciesDeployed(policyPluginService, flows))
                .andThen(flowValidator.validateAll(flows))
                .andThen(flowService.createOrUpdate(ReferenceType.DOMAIN, domainId, protectedResourceId, convert(flows), authenticatedUser)
                        .map(updatedFlows -> updatedFlows.stream().map(FlowEntity::new).collect(Collectors.toList())))
                .subscribe(response::resume, response::resume);
    }

    private static Completable checkTokenFlowOnly(List<io.gravitee.am.service.model.Flow> flows) {
        boolean onlyTokenFlows = flows.stream().allMatch(flow -> Type.TOKEN.equals(flow.getType()));
        if (!onlyTokenFlows) {
            return Completable.error(new InvalidParameterException("Only the TOKEN flow can be configured for a protected resource"));
        }
        return Completable.complete();
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
                .map(ProtectedResourceFlowsResource::convert)
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
