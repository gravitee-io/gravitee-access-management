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
import io.gravitee.am.management.handlers.management.api.resources.model.FilteredIdentityProviderInfo;
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.IdentityProviderServiceProxy;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewIdentityProvider;
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
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"identity provider"})
public class IdentityProvidersResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private IdentityProviderServiceProxy identityProviderService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "listIdentityProviders",
            value = "List registered identity providers for a security domain",
            notes = "User must have the DOMAIN_IDENTITY_PROVIDER[LIST] permission on the specified domain " +
                    "or DOMAIN_IDENTITY_PROVIDER[LIST] permission on the specified environment " +
                    "or DOMAIN_IDENTITY_PROVIDER[LIST] permission on the specified organization. " +
                    "Each returned identity provider is filtered and contains only basic information such as id, name and type.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered identity providers for a security domain", response = FilteredIdentityProviderInfo.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @QueryParam("userProvider") boolean userProvider,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_IDENTITY_PROVIDER, Acl.LIST)
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapPublisher(__ -> identityProviderService.findByDomain(domain))
                        .flatMapMaybe(identityProvider -> {
                            if (userProvider) {
                                // if userProvider, we only want to manage IDP with a UserProvider
                                // as UserProvider may be disabled by configuration we have to check the existence of the instance
                                return identityProviderManager.getUserProvider(identityProvider.getId())
                                        .map(ignorable -> identityProvider);
                            } else {
                                return Maybe.just(identityProvider);
                            }
                        })
                        .map(this::filterIdentityProviderInfos)
                        .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "createIdentityProvider",
            value = "Create an identity provider",
            notes = "User must have the DOMAIN_IDENTITY_PROVIDER[CREATE] permission on the specified domain " +
                    "or DOMAIN_IDENTITY_PROVIDER[CREATE] permission on the specified environment " +
                    "or DOMAIN_IDENTITY_PROVIDER[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Identity provider successfully created", response = IdentityProvider.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @ApiParam(name = "identity", required = true)
            @Valid @NotNull final NewIdentityProvider newIdentityProvider,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_IDENTITY_PROVIDER, Acl.CREATE)
                .andThen(identityProviderManager.checkPluginDeployment(newIdentityProvider.getType()))
                .andThen(domainService.findById(domain)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                        .flatMapSingle(__ -> identityProviderService.create(domain, newIdentityProvider, authenticatedUser))
                        .map(identityProvider -> Response.created(URI.create("/organizations/" + organizationId + "/environments/"
                                                    + environmentId + "/domains/" + domain + "/identities/" + identityProvider.getId()))
                                            .entity(identityProvider)
                                            .build()
                        ))
                .subscribe(response::resume, response::resume);
    }

    @Path("{identity}")
    public IdentityProviderResource getIdentityProviderResource() {
        return resourceContext.getResource(IdentityProviderResource.class);
    }

    private FilteredIdentityProviderInfo filterIdentityProviderInfos(IdentityProvider identityProvider) {
        return new FilteredIdentityProviderInfo(identityProvider.getId(),
                identityProvider.getName(), identityProvider.getType(), identityProvider.isSystem(), identityProvider.isExternal());
    }

}
