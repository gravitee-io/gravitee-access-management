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
import io.gravitee.am.management.service.IdentityProviderManager;
import io.gravitee.am.management.service.ReporterServiceProxy;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collection;
import java.util.stream.Collectors;

import static io.gravitee.am.management.service.permissions.Permissions.of;
import static io.gravitee.am.management.service.permissions.Permissions.or;
import static io.gravitee.am.repository.utils.RepositoryConstants.DEFAULT_MAX_CONCURRENCY;
import static io.gravitee.am.repository.utils.RepositoryConstants.DELAY_ERRORS;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tag(name = "domain")
public class DomainsResource extends AbstractDomainResource {

    private static final String MAX_DOMAINS_SIZE_PER_PAGE_STRING = "50";

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private ReporterServiceProxy reporterService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listDomains",
            summary = "List security domains for an environment",
            description = "List all the security domains accessible to the current user. " +
                    "User must have DOMAIN[LIST] permission on the specified environment or organization " +
                    "AND either DOMAIN[READ] permission on each security domain " +
                    "or DOMAIN[READ] permission on the specified environment " +
                    "or DOMAIN[READ] permission on the specified organization." +
                    "Each returned domain is filtered and contains only basic information such as id, name and description and isEnabled.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List accessible security domains for current user",
                    content = @Content(mediaType =  "application/json",
                            schema = @Schema(implementation = DomainPage.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue(MAX_DOMAINS_SIZE_PER_PAGE_STRING) int size,
            @QueryParam("q") String query,
            @Suspended final AsyncResponse response) {

        User authenticatedUser = getAuthenticatedUser();
        checkAnyPermission(organizationId, environmentId, Permission.DOMAIN, Acl.LIST)
                .andThen(query != null ? domainService.search(organizationId, environmentId, query) : domainService.findAllByEnvironment(organizationId, environmentId))
                .flatMapMaybe(domain -> hasPermission(authenticatedUser,
                        or(of(ReferenceType.DOMAIN, domain.getId(), Permission.DOMAIN, Acl.READ),
                                of(ReferenceType.ENVIRONMENT, environmentId, Permission.DOMAIN, Acl.READ),
                                of(ReferenceType.ORGANIZATION, organizationId, Permission.DOMAIN, Acl.READ)))
                        .filter(Boolean::booleanValue).map(permit -> domain), DELAY_ERRORS, DEFAULT_MAX_CONCURRENCY)
                .map(this::filterDomainInfos)
                .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName()))
                .toList()
                .map(domains -> new DomainPage(domains.stream().skip((long) page * size).limit(size).collect(Collectors.toList()), page, domains.size()))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createDomain",
            summary = "Create a security domain.",
            description = "Create a security domain. " +
                    "User must have DOMAIN[CREATE] permission on the specified environment " +
                    "or DOMAIN[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Domain successfully created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Domain.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @Parameter(name = "domain", required = true)
            @Valid @NotNull final NewDomain newDomain,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, Permission.DOMAIN, Acl.CREATE)
                .andThen(domainService.create(organizationId, environmentId, newDomain, authenticatedUser))
                .subscribe(domain -> response.resume(Response.created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain.getId()))
                        .entity(domain).build()), response::resume);
    }

    @GET
    @Path("_hrid/{hrid}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findDomainByHrid",
            summary = "Get a security domain by hrid",
            description = "User must have the DOMAIN[READ] permission on the specified domain, environment or organization. " +
                    "Domain will be filtered according to permissions (READ on DOMAIN_USER_ACCOUNT, DOMAIN_IDENTITY_PROVIDER, DOMAIN_FORM, DOMAIN_LOGIN_SETTINGS, " +
                    "DOMAIN_DCR, DOMAIN_SCIM, DOMAIN_SETTINGS)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Domain",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Domain.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(@PathParam("organizationId") String organizationId,
                    @PathParam("environmentId") String environmentId,
                    @PathParam("hrid") String hrid,
                    @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findByHrid(environmentId, hrid)
                .flatMap(domain ->
                        checkAnyPermission(authenticatedUser, organizationId, environmentId, domain.getId(), Permission.DOMAIN, Acl.READ)
                                .andThen(Single.defer(() ->
                                        findAllPermissions(authenticatedUser, organizationId, environmentId, domain.getId())
                                                .map(userPermissions -> filterDomainInfos(domain, userPermissions))))
                ).subscribe(response::resume, response::resume);
    }

    public static final class DomainPage extends Page<Domain> {
        public DomainPage(Collection<Domain> data, int currentPage, long totalCount) {
            super(data, currentPage, totalCount);
        }
    }


    @Path("{domain}")
    public DomainResource getDomainResource() {
        return resourceContext.getResource(DomainResource.class);
    }
}
