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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Policy;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.PolicyService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewPolicy;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"policy"})
public class PoliciesResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private PolicyService policyService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List registered policies for a security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered policies for a security domain", response = Policy.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(@PathParam("domain") String domain,
                     @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(irrelevant -> policyService.findByDomain(domain))
                .map(policies -> policies.stream().collect(Collectors.groupingBy(Policy::getExtensionPoint)))
                .map(result -> {
                    result.entrySet().stream().forEach(e -> e.getValue().sort(Comparator.comparing(Policy::getOrder)));
                    return result;
                })
                .map(result -> Response.ok(result).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a policy")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Policy successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("domain") String domain,
            @ApiParam(name = "policy", required = true) @Valid @NotNull final NewPolicy newPolicy,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(irrelevant -> policyService.create(domain, newPolicy, authenticatedUser))
                .map(policy -> Response
                        .created(URI.create("/domains/" + domain + "/policies/" + policy.getId()))
                        .entity(policy)
                        .build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update policies")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Policies successfully updated"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("domain") String domain,
            @ApiParam(name = "policies", required = true) @Valid @NotNull final List<Policy> policies,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(__ -> policyService.update(domain, policies, authenticatedUser))
                .map(policies1 -> policies1.stream().collect(Collectors.groupingBy(Policy::getExtensionPoint)))
                .map(result -> {
                    result.entrySet().stream().forEach(e -> e.getValue().sort(Comparator.comparing(Policy::getOrder)));
                    return result;
                })
                .map(result -> Response.ok(result).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @Path("{policy}")
    public PolicyResource getPolicyResource() {
        return resourceContext.getResource(PolicyResource.class);
    }
}
