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
import io.gravitee.am.management.handlers.management.api.security.Permission;
import io.gravitee.am.management.handlers.management.api.security.Permissions;
import io.gravitee.am.management.service.AuditReporterManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.RolePermission;
import io.gravitee.am.model.permissions.RolePermissionAction;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.PatchDomain;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get a security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Domain", response = Domain.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(@PathParam("domain") String domainId, @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.findById(domainId)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                .map(domain -> {
                    if (isAdmin(authenticatedUser)) {
                        return domain;
                    }
                    List<String> resourcePermissions = resourcePermissions(domain, ReferenceType.DOMAIN, authenticatedUser);
                    if (!hasPermission(resourcePermissions, RolePermission.DOMAIN_USER_ACCOUNT, RolePermissionAction.READ)) {
                        domain.setAccountSettings(null);
                    }
                    if (!hasPermission(resourcePermissions, RolePermission.DOMAIN_IDENTITY_PROVIDER, RolePermissionAction.READ)) {
                        domain.setIdentities(null);
                    }
                    if (!hasPermission(resourcePermissions, RolePermission.DOMAIN_FORM, RolePermissionAction.READ)) {
                        domain.setLoginForm(null);
                    }
                    if (!hasPermission(resourcePermissions, RolePermission.DOMAIN_LOGIN_SETTINGS, RolePermissionAction.READ)) {
                        domain.setLoginSettings(null);
                    }
                    if (!hasPermission(resourcePermissions, RolePermission.DOMAIN_DCR, RolePermissionAction.READ)) {
                        domain.setOidc(null);
                    }
                    if (!hasPermission(resourcePermissions, RolePermission.DOMAIN_SCIM, RolePermissionAction.READ)) {
                        domain.setScim(null);
                    }
                    if (!hasPermission(resourcePermissions, RolePermission.DOMAIN_SETTINGS, RolePermissionAction.READ)) {
                        domain.setTags(null);
                    }
                    return domain;
                })
                .map(domain -> Response.ok(domain).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update the security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Domain successfully updated", response = Domain.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.DOMAIN_SETTINGS, acls = RolePermissionAction.UPDATE)
    })
    public void update(
            @ApiParam(name = "domain", required = true) @Valid @NotNull final PatchDomain domainToPatch,
            @PathParam("domain") String domainId,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

         domainService.patch(domainId, domainToPatch, authenticatedUser)
                .subscribe(
                        domain -> response.resume(Response.ok(domain).build()),
                        error -> response.resume(error));
    }


    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Patch the security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Domain successfully patched", response = Domain.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.DOMAIN_SETTINGS, acls = RolePermissionAction.UPDATE)
    })
    public void patch(
            @ApiParam(name = "domain", required = true) @Valid @NotNull final PatchDomain domainToPatch,
            @PathParam("domain") String domainId,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.patch(domainId, domainToPatch, authenticatedUser)
                .subscribe(
                        domain -> response.resume(Response.ok(domain).build()),
                        error -> response.resume(error));
    }

    @DELETE
    @ApiOperation(value = "Delete the security domain")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Domain successfully deleted"),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.DOMAIN_SETTINGS, acls = RolePermissionAction.DELETE)
    })
    public void delete(@PathParam("domain") String domain,
                       @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        domainService.delete(domain, authenticatedUser)
                .doOnComplete(() -> auditReporterManager.removeReporter(domain))
                .subscribe(
                        () -> response.resume(Response.noContent().build()),
                        error -> response.resume(error));
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


}
