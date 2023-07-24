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
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.FactorPluginService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.FactorService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewFactor;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
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

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"factor"})
public class FactorsResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private FactorService factorService;

    @Autowired
    private FactorPluginService factorPluginService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List registered factors for a security domain",
            nickname = "listFactors",
            notes = "User must have the DOMAIN_FACTOR[LIST] permission on the specified domain " +
                    "or DOMAIN_FACTOR[LIST] permission on the specified environment " +
                    "or DOMAIN_FACTOR[LIST] permission on the specified organization " +
                    "Each returned factor is filtered and contains only basic information such as id, name and factor type.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered factors for a security domain", response = Factor.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_FACTOR, Acl.LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapPublisher(___ -> factorService.findByDomain(domain))
                        .map(this::filterFactorInfos)
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a factor",
            nickname = "createFactor",
            notes = "User must have the DOMAIN_FACTOR[CREATE] permission on the specified domain " +
                    "or DOMAIN_FACTOR[CREATE] permission on the specified environment " +
                    "or DOMAIN_FACTOR[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Factor successfully created", response = Factor.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @ApiParam(name = "factor", required = true) @Valid @NotNull final NewFactor newFactor,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_FACTOR, Acl.CREATE)
                .andThen(factorPluginService.checkPluginDeployment(newFactor.getType()))
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> factorService.create(domain, newFactor, authenticatedUser))
                        .map(factor -> Response
                                .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/factors/" + factor.getId()))
                                .entity(factor)
                                .build()))
                .subscribe(response::resume, response::resume);
    }

    @Path("{factor}")
    public FactorResource getFactorResource() {
        return resourceContext.getResource(FactorResource.class);
    }

    private Factor filterFactorInfos(Factor factor) {
        Factor filteredFactor = new Factor();
        filteredFactor.setId(factor.getId());
        filteredFactor.setName(factor.getName());
        filteredFactor.setFactorType(factor.getFactorType());

        return filteredFactor;
    }
}
