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
import io.gravitee.am.management.handlers.management.api.adapter.AuthorizationDetailApprovalAdapter;
import io.gravitee.am.management.handlers.management.api.model.AuthorizationDetailApprovalEntity;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.resources.model.CursorApiPage;
import io.gravitee.am.management.handlers.management.api.resources.model.CursorPagination;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.oauth2.AuthorizationDetailApprovalCursorRequest;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class UserAuthorizationDetailConsentsResource extends AbstractResource {

    @Context
    private DomainService domainService;

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private AuthorizationDetailApprovalAdapter approvalAdapter;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listUserAuthorizationDetailConsents",
            summary = "List a user's authorization detail consents with cursor-based pagination",
            description = "Lists the authorization details the user approved or denied that have not expired. " +
                    "User must have the DOMAIN_USER[READ] permission on the specified domain " +
                    "or DOMAIN_USER[READ] permission on the specified environment " +
                    "or DOMAIN_USER[READ] permission on the specified organization. " +
                    "Consents are sorted by the requested field, then by id. Follow nextCursor to get the next page.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of the user's authorization detail consents",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationDetailConsentCursorPage.class))),
            @ApiResponse(responseCode = "400", description = "Invalid limit, page, sort, dir or cursor"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @Parameter(description = "Keeps only the consents given to this client")
            @QueryParam("clientId") String clientId,
            @Parameter(description = "Type to match, ignoring case; * matches any characters")
            @QueryParam("type") String type,
            @Parameter(description = CursorPagination.LIMIT_DESCRIPTION)
            @QueryParam(CursorPagination.PARAM_LIMIT) @DefaultValue(CursorPagination.DEFAULT_LIMIT_STRING) @Min(1) int limit,
            @Parameter(description = "Field to sort by", schema = @Schema(allowableValues = {
                    AuthorizationDetailApprovalCursorRequest.SORT_FIELD_UPDATED_AT,
                    AuthorizationDetailApprovalCursorRequest.SORT_FIELD_EXPIRES_AT,
                    AuthorizationDetailApprovalCursorRequest.SORT_FIELD_TYPE,
                    AuthorizationDetailApprovalCursorRequest.SORT_FIELD_STATUS}))
            @QueryParam(CursorPagination.PARAM_SORT) @DefaultValue(AuthorizationDetailApprovalCursorRequest.SORT_FIELD_UPDATED_AT) String sort,
            @Parameter(description = "Sort direction", schema = @Schema(allowableValues = {"ASC", "DESC"}))
            @QueryParam(CursorPagination.PARAM_DIR) @DefaultValue("DESC") String direction,
            @Parameter(description = CursorPagination.PAGE_DESCRIPTION)
            @QueryParam(CursorPagination.PARAM_PAGE) @DefaultValue("0") @Min(0) int page,
            @Parameter(description = CursorPagination.CURSOR_DESCRIPTION)
            @QueryParam(CursorPagination.PARAM_CURSOR) String cursorEncoded,
            @Context UriInfo uriInfo,
            @Suspended final AsyncResponse response) {
        final String clientFilter = clientId == null || clientId.isBlank() ? null : clientId;
        final String typeFilter = type == null || type.isBlank() ? null : type;
        final String basePath = "/organizations/%s/environments/%s/domains/%s/users/%s/consents/authorization-details"
                .formatted(organizationId, environmentId, domainId, user);

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.READ)
                .andThen(Single.fromCallable(() -> CursorPagination.cursorRequest(cursorEncoded, sort, direction,
                        () -> AuthorizationDetailApprovalCursorRequest.initialCursor(sort, direction, page, clientFilter, typeFilter),
                        cursor -> new AuthorizationDetailApprovalCursorRequest(cursor.lastSortValue(), cursor.id(), cursor.sortDirection(),
                                sort, page, clientFilter, typeFilter))))
                .onErrorResumeNext(ex -> ex instanceof IllegalArgumentException
                        ? Single.error(new BadRequestException(ex.getMessage()))
                        : Single.error(ex))
                .flatMap(cursor -> domainService.findById(domainId)
                        .switchIfEmpty(Single.error(new DomainNotFoundException(domainId)))
                        .flatMap(domain -> approvalAdapter.getUserConsents(domain, user, cursor, CursorPagination.pageSize(limit)))
                        .map(found -> new AuthorizationDetailConsentCursorPage(
                                found.getData(),
                                CursorPagination.nextCursorPath(found.getNextCursor(), uriInfo, basePath),
                                found.getTotalCount(),
                                cursor.getPage())))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(
            operationId = "revokeUserAuthorizationDetailConsents",
            summary = "Revoke a user's authorization detail consents",
            description = "Revokes the approved and denied authorization details of the user, then the user's tokens. " +
                    "User must have the DOMAIN_USER[UPDATE] permission on the specified domain " +
                    "or DOMAIN_USER[UPDATE] permission on the specified environment " +
                    "or DOMAIN_USER[UPDATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User authorization detail consents successfully revoked"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("user") String user,
            @Parameter(description = "Revokes only the consents given to this client")
            @QueryParam("clientId") String clientId,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_USER, Acl.UPDATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                        .flatMapCompletable(domain -> approvalAdapter.revokeUserConsents(domain, user, clientId, authenticatedUser)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }

    @Path("{consent}")
    public UserAuthorizationDetailConsentResource getUserAuthorizationDetailConsentResource() {
        return resourceContext.getResource(UserAuthorizationDetailConsentResource.class);
    }

    public static final class AuthorizationDetailConsentCursorPage extends CursorApiPage<AuthorizationDetailApprovalEntity> {
        public AuthorizationDetailConsentCursorPage(List<AuthorizationDetailApprovalEntity> data, String nextCursor, Long totalCount, Integer page) {
            super(data, nextCursor, totalCount, page);
        }
    }
}
