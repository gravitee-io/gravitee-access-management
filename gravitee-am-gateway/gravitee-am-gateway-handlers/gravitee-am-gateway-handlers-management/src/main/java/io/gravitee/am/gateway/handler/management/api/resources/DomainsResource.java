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
package io.gravitee.am.gateway.handler.management.api.resources;

import io.gravitee.am.gateway.service.DomainService;
import io.gravitee.am.gateway.service.exception.DomainAlreadyExistsException;
import io.gravitee.am.gateway.service.model.NewDomain;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Path("/domains")
@Api(tags = {"domain"})
public class DomainsResource extends AbstractResource {

    @Autowired
    private DomainService domainService;

    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "List security domains",
            notes = "List all the security domains accessible to the current user.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List accessible security domains for current user", response = Domain.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public List<Domain> listDomains() {
        return domainService.findAll()
                .stream()
                .map(domain -> {
                    domain.setLoginForm(null);
                    return domain;
                })
                .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                .collect(Collectors.toList());
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a security domain")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Domain successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response createApi(
            @ApiParam(name = "domain", required = true)
            @Valid @NotNull final NewDomain newDomain) throws DomainAlreadyExistsException {
        Domain domain = domainService.create(newDomain);
        if (domain != null) {
            return Response
                    .created(URI.create("/domains/" + domain.getId()))
                    .entity(domain)
                    .build();
        }

        return Response.serverError().build();
    }

    @Path("{domain}")
    public DomainResource getDomainResource() {
        return resourceContext.getResource(DomainResource.class);
    }

}
