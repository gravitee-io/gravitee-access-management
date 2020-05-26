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
import io.gravitee.am.management.service.AuditReporterManager;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.EntrypointService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.exception.EntrypointNotFoundException;
import io.gravitee.am.service.model.PatchDomain;
import io.gravitee.common.http.MediaType;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainResource extends AbstractResource {

    @Autowired
    private DomainService domainService;

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private AuditReporterManager auditReporterManager;

    @Autowired
    private EntrypointService entrypointService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a security domain",
            notes = "User must have the DOMAIN[READ] permission on the specified domain, environment or organization. " +
                    "Domain will be filtered according to permissions (READ on DOMAIN_USER_ACCOUNT, DOMAIN_IDENTITY_PROVIDER, DOMAIN_FORM, DOMAIN_LOGIN_SETTINGS, " +
                    "DOMAIN_DCR, DOMAIN_SCIM, DOMAIN_SETTINGS)")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Domain", response = Domain.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(domain -> findAllPermissions(authenticatedUser, organizationId, environmentId, domainId)
                                .map(userPermissions -> filterDomainInfos(domain, userPermissions))))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update the security domain",
            notes = "User must have the DOMAIN_SETTINGS[UPDATE] permission on the specified domain " +
                    "or DOMAIN_SETTINGS[UPDATE] permission on the specified environment " +
                    "or DOMAIN_SETTINGS[UPDATE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Domain successfully updated", response = Domain.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @ApiParam(name = "domain", required = true) @Valid @NotNull final PatchDomain domainToPatch,
            @Suspended final AsyncResponse response) {

        updateInternal(organizationId, environmentId, domainId, domainToPatch, response);
    }

    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Patch the security domain",
            notes = "User must have the DOMAIN_SETTINGS[UPDATE] permission on the specified domain " +
                    "or DOMAIN_SETTINGS[UPDATE] permission on the specified environment " +
                    "or DOMAIN_SETTINGS[UPDATE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Domain successfully patched", response = Domain.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void patch(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @ApiParam(name = "domain", required = true) @Valid @NotNull final PatchDomain domainToPatch,
            @Suspended final AsyncResponse response) {

        updateInternal(organizationId, environmentId, domainId, domainToPatch, response);
    }

    @DELETE
    @ApiOperation(value = "Delete the security domain",
            notes = "User must have the DOMAIN[DELETE] permission on the specified domain " +
                    "or DOMAIN[DELETE] permission on the specified environment " +
                    "or DOMAIN[DELETE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Domain successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN, Acl.DELETE)
                .andThen(domainService.delete(domain, authenticatedUser)
                        .doOnComplete(() -> auditReporterManager.removeReporter(domain)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @GET
    @Path("/entrypoints")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get the matching gateway entrypoint of the domain",
            notes = "User must have the DOMAIN[READ] permission on the specified domain, environment or organization. " +
                    "Domain will be filtered according to permissions (READ on DOMAIN_USER_ACCOUNT, DOMAIN_IDENTITY_PROVIDER, DOMAIN_FORM, DOMAIN_LOGIN_SETTINGS, " +
                    "DOMAIN_DCR, DOMAIN_SCIM, DOMAIN_SETTINGS)")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Domain entrypoint", response = Entrypoint.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void getEntrypoints(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapSingle(domain -> entrypointService.findAll(organizationId)
                                .toList()
                                .map(entrypoints -> filterEntrypoints(entrypoints, domain))))
                .subscribe(response::resume, response::resume);
    }

    @Path("clients")
    public ClientsResource getClientsResource() {
        return resourceContext.getResource(ClientsResource.class);
    }

    @Path("applications")
    public ApplicationsResource getApplicationsResource() {
        return resourceContext.getResource(ApplicationsResource.class);
    }

    @Path("identities")
    public IdentityProvidersResource getIdentityProvidersResource() {
        return resourceContext.getResource(IdentityProvidersResource.class);
    }

    @Path("certificates")
    public CertificatesResource getCertificatesResource() {
        return resourceContext.getResource(CertificatesResource.class);
    }

    @Path("roles")
    public RolesResource getRolesResource() {
        return resourceContext.getResource(RolesResource.class);
    }

    @Path("users")
    public UsersResource getUsersResource() {
        return resourceContext.getResource(UsersResource.class);
    }

    @Path("extensionGrants")
    public ExtensionGrantsResource getTokenGrantersResource() {
        return resourceContext.getResource(ExtensionGrantsResource.class);
    }

    @Path("scopes")
    public ScopesResource getScopesResource() {
        return resourceContext.getResource(ScopesResource.class);
    }

    @Path("forms")
    public FormsResource getPagesResource() {
        return resourceContext.getResource(FormsResource.class);
    }

    @Path("groups")
    public GroupsResource getGroupsResource() {
        return resourceContext.getResource(GroupsResource.class);
    }

    @Path("emails")
    public EmailsResource getEmailsResource() {
        return resourceContext.getResource(EmailsResource.class);
    }

    @Path("audits")
    public AuditsResource getAuditsResource() {
        return resourceContext.getResource(AuditsResource.class);
    }

    @Path("reporters")
    public ReportersResource getReportersResource() {
        return resourceContext.getResource(ReportersResource.class);
    }

    @Path("policies")
    public PoliciesResource getPoliciesResource() {
        return resourceContext.getResource(PoliciesResource.class);
    }

    @Path("members")
    public MembersResource getMembersResource() {
        return resourceContext.getResource(MembersResource.class);
    }

    @Path("analytics")
    public AnalyticsResource getAnalyticsResource() {
        return resourceContext.getResource(AnalyticsResource.class);
    }

    @Path("factors")
    public FactorsResource getFactorsResource() {
        return resourceContext.getResource(FactorsResource.class);
    }

    private void updateInternal(String organizationId, String environmentId, String domainId, final PatchDomain patchDomain, final AsyncResponse response) {

        final User authenticatedUser = getAuthenticatedUser();
        Set<Permission> requiredPermissions = patchDomain.getRequiredPermissions();

        if (requiredPermissions.isEmpty()) {
            // If there is no require permission, it means there is nothing to update. This is not a valid request.
            response.resume(new BadRequestException("You need to specify at least one value to update."));
        } else {
            Completable.merge(requiredPermissions.stream()
                    .map(permission -> checkAnyPermission(organizationId, environmentId, domainId, permission, Acl.UPDATE))
                    .collect(Collectors.toList()))
                    .andThen(domainService.patch(domainId, patchDomain, authenticatedUser)
                            .flatMap(domain -> findAllPermissions(authenticatedUser, organizationId, environmentId, domainId)
                                    .map(userPermissions -> filterDomainInfos(domain, userPermissions))))
                    .subscribe(response::resume, response::resume);
        }
    }

    private Domain filterDomainInfos(Domain domain, Map<ReferenceType, Map<Permission, Set<Acl>>> userPermissions) {

        Domain filteredDomain = new Domain();

        if (hasAnyPermission(userPermissions, Permission.DOMAIN, Acl.READ)) {
            filteredDomain.setId(domain.getId());
            filteredDomain.setName(domain.getName());
            filteredDomain.setDescription(domain.getDescription());
            filteredDomain.setEnabled(domain.isEnabled());
            filteredDomain.setCreatedAt(domain.getCreatedAt());
            filteredDomain.setUpdatedAt(domain.getUpdatedAt());
            filteredDomain.setPath(domain.getPath());
            filteredDomain.setReferenceType(domain.getReferenceType());
            filteredDomain.setReferenceId(domain.getReferenceId());
        }

        if (hasAnyPermission(userPermissions, Permission.DOMAIN_OPENID, Acl.READ)) {
            filteredDomain.setOidc(domain.getOidc());
        }

        if (hasAnyPermission(userPermissions, Permission.DOMAIN_UMA, Acl.READ)) {
            filteredDomain.setUma(domain.getUma());
        }

        if (hasAnyPermission(userPermissions, Permission.DOMAIN_SCIM, Acl.READ)) {
            filteredDomain.setScim(domain.getScim());
        }

        if (hasAnyPermission(userPermissions, Permission.DOMAIN_SETTINGS, Acl.READ)) {
            filteredDomain.setLoginSettings(domain.getLoginSettings());
            filteredDomain.setAccountSettings(domain.getAccountSettings());
            filteredDomain.setTags(domain.getTags());
        }

        return filteredDomain;
    }

    /**
     * Filter a list of entrypoints depending on domain tags.
     * Given a domain with tags [ A, B ], then entrypoint must has either A or B tag defined.
     * If no entrypoint has been retained, the default entrypoint is returned.
     *
     * @param domain the domain.
     * @return a filtered list of entrypoints.
     */
    private List<Entrypoint> filterEntrypoints(List<Entrypoint> entrypoints, Domain domain) {

        List<Entrypoint> filteredEntrypoints = entrypoints.stream().filter(entrypoint -> entrypoint.isDefaultEntrypoint()
                || (entrypoint.getTags() != null && !entrypoint.getTags().isEmpty() && domain.getTags() != null && entrypoint.getTags().stream().anyMatch(tag -> domain.getTags().contains(tag)))).collect(Collectors.toList());

        if (filteredEntrypoints.size() > 1) {
            // Remove default entrypoint if another entrypoint has matched.
            filteredEntrypoints.removeIf(Entrypoint::isDefaultEntrypoint);
        }

        return filteredEntrypoints;
    }
}
