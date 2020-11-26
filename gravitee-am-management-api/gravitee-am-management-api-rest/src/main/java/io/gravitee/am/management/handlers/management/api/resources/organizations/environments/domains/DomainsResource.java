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
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.common.http.MediaType;
import io.reactivex.Single;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"domain"})
public class DomainsResource extends AbstractResource {

    @Autowired
    private DomainService domainService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private ReporterService reporterService;

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private Environment environment;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "List security domains",
            notes = "List all the security domains accessible to the current user. " +
                    "User must have DOMAIN[LIST] permission on the specified environment or organization " +
                    "AND either DOMAIN[READ] permission on each security domain " +
                    "or DOMAIN[READ] permission on the specified environment " +
                    "or DOMAIN[READ] permission on the specified organization." +
                    "Each returned domain is filtered and contains only basic information such as id, name and description and isEnabled.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List accessible security domains for current user", response = Domain.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @Suspended final AsyncResponse response) {

        User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, Permission.DOMAIN, Acl.LIST)
                .andThen(domainService.findAllByEnvironment(organizationId, environmentId)
                        .flatMapMaybe(domain -> hasPermission(authenticatedUser,
                                or(of(ReferenceType.DOMAIN, domain.getId(), Permission.DOMAIN, Acl.READ),
                                        of(ReferenceType.ENVIRONMENT, environmentId, Permission.DOMAIN, Acl.READ),
                                        of(ReferenceType.ORGANIZATION, organizationId, Permission.DOMAIN, Acl.READ)))
                                .filter(Boolean::booleanValue).map(permit -> domain))
                        .map(this::filterDomainInfos)
                        .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a security domain.",
            notes = "Create a security domain. " +
                    "User must have DOMAIN[CREATE] permission on the specified environment " +
                    "or DOMAIN[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Domain successfully created"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @ApiParam(name = "domain", required = true)
            @Valid @NotNull final NewDomain newDomain,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, Permission.DOMAIN, Acl.CREATE)
                .andThen(domainService.create(organizationId, environmentId, newDomain, authenticatedUser)
                        // create default idp (ignore if mongodb isn't the repositories backend)
                        .flatMap(domain -> identityProviderManager.create(domain.getId()).map(__ -> domain))
                        // create default reporter
                        .flatMap(domain -> reporterService.createDefault(domain.getId()).map(__ -> domain)))
                .subscribe(domain -> response.resume(Response.created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain.getId()))
                        .entity(domain).build()), response::resume);
    }

    @Path("{domain}")
    public DomainResource getDomainResource() {
        return resourceContext.getResource(DomainResource.class);
    }

    private Domain filterDomainInfos(Domain domain) {
        Domain filteredDomain = new Domain();
        filteredDomain.setId(domain.getId());
        filteredDomain.setName(domain.getName());
        filteredDomain.setDescription(domain.getDescription());
        filteredDomain.setEnabled(domain.isEnabled());

        return filteredDomain;
    }
}
