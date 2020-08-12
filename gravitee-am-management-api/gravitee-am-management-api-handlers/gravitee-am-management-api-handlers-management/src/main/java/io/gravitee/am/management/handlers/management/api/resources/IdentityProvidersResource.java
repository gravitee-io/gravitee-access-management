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
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.reactivex.Observable;
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
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"identity provider"})
public class IdentityProvidersResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List registered identity providers for a security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered identity providers for a security domain", response = IdentityProvider.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(@PathParam("domain") String domain,
                     @QueryParam("userProvider") boolean userProvider,
                     @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(irrelevant -> identityProviderService.findByDomain(domain))
                .flatMapObservable(identities -> Observable.fromIterable(identities))
                .filter(identityProvider -> {
                    if (userProvider) {
                        return identityProviderManager.userProviderExists(identityProvider.getId());
                    }
                    return true;
                })
                .toList()
                .map(identities -> {
                    List<IdentityProvider> sortedIdentityProviders = identities.stream()
                            .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                            .collect(Collectors.toList());
                    return Response.ok(sortedIdentityProviders).build();
                })
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create an identity provider")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Identity provider successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("domain") String domain,
            @ApiParam(name = "identity", required = true)
            @Valid @NotNull final NewIdentityProvider newIdentityProvider,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(__ -> identityProviderService.create(domain, newIdentityProvider, authenticatedUser))
                .map(identityProvider -> Response
                        .created(URI.create("/domains/" + domain + "/identities/" + identityProvider.getId()))
                        .entity(identityProvider)
                        .build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @Path("{identity}")
    public IdentityProviderResource getIdentityProviderResource() {
        return resourceContext.getResource(IdentityProviderResource.class);
    }
}
