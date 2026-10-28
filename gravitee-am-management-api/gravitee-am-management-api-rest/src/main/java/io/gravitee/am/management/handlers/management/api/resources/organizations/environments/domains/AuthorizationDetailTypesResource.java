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

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.handlers.management.api.resources.model.CursorApiPage;
import io.gravitee.am.management.handlers.management.api.resources.model.CursorPagination;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.model.oauth2.AuthorizationDetailTypeCursorRequest;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.AuthorizationDetailTypeService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewAuthorizationDetailType;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
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

import java.net.URI;
import java.util.List;

/**
 * @author GraviteeSource Team
 */
@Tag(name = "authorization detail type")
public class AuthorizationDetailTypesResource extends AbstractResource {

    private static final String PARAM_QUERY = "q";

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private AuthorizationDetailTypeService authorizationDetailTypeService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listAuthorizationDetailTypes",
            summary = "List the authorization detail types of a security domain with cursor-based pagination",
            description = "User must have the DOMAIN_AUTHORIZATION_DETAIL_TYPE[LIST] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_DETAIL_TYPE[LIST] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_DETAIL_TYPE[LIST] permission on the specified organization. " +
                    "Types are sorted ignoring case, then by id. Each returned type omits its schema; read the type by its id to get it. " +
                    "Follow nextCursor to get the next page.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of the domain's authorization detail types",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationDetailTypeCursorPage.class))),
            @ApiResponse(responseCode = "400", description = "Invalid limit, page, sort, dir or cursor"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(description = CursorPagination.LIMIT_DESCRIPTION)
            @QueryParam(CursorPagination.PARAM_LIMIT) @DefaultValue(CursorPagination.DEFAULT_LIMIT_STRING) @Min(1) int limit,
            @Parameter(description = "Field to sort by", schema = @Schema(allowableValues = {"type", "name"}))
            @QueryParam(CursorPagination.PARAM_SORT) @DefaultValue("type") String sort,
            @Parameter(description = "Sort direction", schema = @Schema(allowableValues = {"ASC", "DESC"}))
            @QueryParam(CursorPagination.PARAM_DIR) @DefaultValue("ASC") String direction,
            @Parameter(description = CursorPagination.PAGE_DESCRIPTION)
            @QueryParam(CursorPagination.PARAM_PAGE) @DefaultValue("0") @Min(0) int page,
            @Parameter(description = "Type to match, ignoring case; * matches any characters")
            @QueryParam(PARAM_QUERY) String query,
            @Parameter(description = CursorPagination.CURSOR_DESCRIPTION)
            @QueryParam(CursorPagination.PARAM_CURSOR) String cursorEncoded,
            @Context UriInfo uriInfo,
            @Suspended final AsyncResponse response) {
        final String filter = query == null || query.isBlank() ? null : query;
        final String basePath = "/organizations/%s/environments/%s/domains/%s/authorization-detail-types".formatted(organizationId, environmentId, domainId);

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_DETAIL_TYPE, Acl.LIST)
                .andThen(Single.fromCallable(() -> CursorPagination.cursorRequest(cursorEncoded, sort, direction,
                        () -> AuthorizationDetailTypeCursorRequest.initialCursor(sort, direction, page, filter),
                        cursor -> new AuthorizationDetailTypeCursorRequest(cursor.lastSortValue(), cursor.id(), cursor.sortDirection(), sort, page, filter))))
                .flatMap(cursor -> authorizationDetailTypeService.findByDomainCursor(domainId, cursor, CursorPagination.pageSize(limit))
                        .map(found -> new AuthorizationDetailTypeCursorPage(
                                found.getData().stream().map(type -> type.toBuilder().schema(null).build()).toList(),
                                CursorPagination.nextCursorPath(found.getNextCursor(), uriInfo, basePath),
                                found.getTotalCount(),
                                cursor.getPage())))
                .onErrorResumeNext(ex -> ex instanceof IllegalArgumentException
                        ? Single.error(new BadRequestException(ex.getMessage()))
                        : Single.error(ex))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createAuthorizationDetailType",
            summary = "Create an RFC 9396 authorization detail type on the security domain",
            description = "User must have the DOMAIN_AUTHORIZATION_DETAIL_TYPE[CREATE] permission on the specified domain " +
                    "or DOMAIN_AUTHORIZATION_DETAIL_TYPE[CREATE] permission on the specified environment " +
                    "or DOMAIN_AUTHORIZATION_DETAIL_TYPE[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Authorization detail type successfully created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthorizationDetailType.class))),
            @ApiResponse(responseCode = "400", description = "Invalid authorization detail type, or one with the same type already exists"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(name = "authorizationDetailType", required = true) @Valid @NotNull final NewAuthorizationDetailType newAuthorizationDetailType,
            @Suspended final AsyncResponse response) {
        final var authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_AUTHORIZATION_DETAIL_TYPE, Acl.CREATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId))))
                .flatMapSingle(domain -> authorizationDetailTypeService.create(domain, newAuthorizationDetailType, authenticatedUser))
                .map(created -> Response
                        .created(URI.create("/organizations/" + organizationId
                                + "/environments/" + environmentId
                                + "/domains/" + domainId
                                + "/authorization-detail-types/" + created.getId()))
                        .entity(created)
                        .build())
                .subscribe(response::resume, response::resume);
    }

    @Path("{authorizationDetailTypeId}")
    public AuthorizationDetailTypeResource getAuthorizationDetailTypeResource() {
        return resourceContext.getResource(AuthorizationDetailTypeResource.class);
    }

    public static final class AuthorizationDetailTypeCursorPage extends CursorApiPage<AuthorizationDetailType> {
        public AuthorizationDetailTypeCursorPage(List<AuthorizationDetailType> data, String nextCursor, Long totalCount, Integer page) {
            super(data, nextCursor, totalCount, page);
        }
    }
}
