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
import io.gravitee.am.management.handlers.management.api.resources.model.ApplicationExpand;
import io.gravitee.am.management.handlers.management.api.resources.model.CursorApiPage;
import io.gravitee.am.management.handlers.management.api.resources.model.CursorPagination;
import io.gravitee.am.management.handlers.management.api.resources.model.FilteredApplication;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationCursorRequest;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationSearcher;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static io.gravitee.am.service.model.ApplicationFilter.STATUS_ENABLED;

@Tag(name = "application")
public class ApplicationsResourceSearcher extends AbstractDomainResource {
    private static final String PARAM_EXPAND = "expand";
    private static final String PARAM_QUERY = "q";
    private static final String PARAM_STATUS = "status";
    private static final String PARAM_OWNER_EMAIL = "owner.email";
    private static final String PARAM_TYPE = "type";

    private static final String LIST_DESCRIPTION = "User must have APPLICATION[LIST] permission on the specified domain, environment or organization. " +
            "Users without APPLICATION[READ] on the domain, environment or organization only get the applications they can read.";

    @Autowired
    private ApplicationSearcher applicationSearcher;

    public static final class ApplicationCursorPage extends CursorApiPage<FilteredApplication> {
        public ApplicationCursorPage(List<FilteredApplication> data, String nextCursor, Long totalCount, Integer page) {
            super(data, nextCursor, totalCount, page);
        }
    }

    @GET
    @Path("_cursor")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "searchApplicationsCursor",
            deprecated = true,
            summary = "List the next page of applications with cursor-based pagination",
            description = "Deprecated: pass the cursor to searchApplications instead. " + LIST_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List applications with cursor pagination",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApplicationCursorPage.class))),
            @ApiResponse(responseCode = "400", description = "Invalid limit, status or cursor"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void cursorSearch(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(description = CursorPagination.LIMIT_DESCRIPTION)
            @QueryParam(CursorPagination.PARAM_LIMIT) @DefaultValue(CursorPagination.DEFAULT_LIMIT_STRING) @Min(1) int limit,
            @QueryParam(PARAM_EXPAND) List<String> expandsParam,
            @QueryParam(CursorPagination.PARAM_SORT) @DefaultValue("updatedAt") String sort,
            @QueryParam(CursorPagination.PARAM_DIR) @DefaultValue("DESC") String direction,
            @QueryParam(CursorPagination.PARAM_PAGE) @DefaultValue("0") int page,

            @QueryParam(PARAM_QUERY) String query,
            @QueryParam(PARAM_STATUS) @Pattern(regexp = "^enabled|disabled$") String status,
            @QueryParam(PARAM_OWNER_EMAIL) String ownerEmail,
            @QueryParam(PARAM_TYPE) List<ApplicationType> types,
            @Parameter(description = CursorPagination.CURSOR_DESCRIPTION)
            @QueryParam(CursorPagination.PARAM_CURSOR) String cursorEncoded,
            @Suspended final AsyncResponse response,
            @Context UriInfo uriInfo) {
        // a blank cursor fails decoding, so a missing one is rejected
        String cursor = cursorEncoded == null ? "" : cursorEncoded;
        searchPage(organizationId, environmentId, domainId,
                () -> cursorRequest(cursor, sort, direction, page, query, status, types),
                ownerEmail, limit, expandsParam, "/search/_cursor", uriInfo)
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "searchApplications",
            summary = "List applications with cursor-based pagination",
            description = "List applications using cursor-based pagination for improved performance at scale. " +
                    "Follow nextCursor to get the next page. " + LIST_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List applications with cursor pagination",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApplicationCursorPage.class))),
            @ApiResponse(responseCode = "400", description = "Invalid limit, status or cursor"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void search(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(description = CursorPagination.LIMIT_DESCRIPTION)
            @QueryParam(CursorPagination.PARAM_LIMIT) @DefaultValue(CursorPagination.DEFAULT_LIMIT_STRING) @Min(1) int limit,
            @Parameter(description = "Field to sort by: updatedAt or name")
            @QueryParam(CursorPagination.PARAM_SORT) @DefaultValue("updatedAt") String sort,
            @Parameter(description = "Sort direction, ASC or DESC")
            @QueryParam(CursorPagination.PARAM_DIR) @DefaultValue("DESC") String direction,
            @Parameter(description = CursorPagination.PAGE_DESCRIPTION)
            @QueryParam(CursorPagination.PARAM_PAGE) @DefaultValue("0") int page,
            @Parameter(description = "Extra application fields to return: clientId")
            @QueryParam(PARAM_EXPAND) List<String> expandsParam,

            @Parameter(description = "Name or client id to match; * matches any characters")
            @QueryParam(PARAM_QUERY) String query,
            @Parameter(description = "enabled or disabled; keeps only the applications with that status")
            @QueryParam(PARAM_STATUS) @Pattern(regexp = "^enabled|disabled$") String status,
            @Parameter(description = "Keeps only the applications owned by the user with this email")
            @QueryParam(PARAM_OWNER_EMAIL) String ownerEmail,
            @Parameter(description = "Keeps only the applications of these types")
            @QueryParam(PARAM_TYPE) List<ApplicationType> types,
            @Parameter(description = CursorPagination.CURSOR_DESCRIPTION)
            @QueryParam(CursorPagination.PARAM_CURSOR) String cursorEncoded,

            @Suspended final AsyncResponse response,
            @Context UriInfo uriInfo) {
        searchPage(organizationId, environmentId, domainId,
                () -> cursorRequest(cursorEncoded, sort, direction, page, query, status, types),
                ownerEmail, limit, expandsParam, "/search", uriInfo)
                .subscribe(response::resume, response::resume);
    }

    private Single<ApplicationCursorPage> searchPage(
            String organizationId,
            String environmentId,
            String domainId,
            Supplier<ApplicationCursorRequest> cursorRequestSupplier,
            String ownerEmail,
            int limit,
            List<String> expandsParam,
            String path,
            UriInfo uriInfo) {
        Set<ApplicationExpand> expands = ApplicationExpand.convertToApplicationExpands(expandsParam);
        int pageSize = CursorPagination.pageSize(limit);
        String basePath = "/organizations/%s/environments/%s/domains/%s/applications%s".formatted(organizationId, environmentId, domainId, path);
        User authenticatedUser = getAuthenticatedUser();
        return checkAnyPermission(organizationId, environmentId, domainId, Permission.APPLICATION, Acl.LIST)
                .andThen(checkDomainExists(domainId).ignoreElement())
                .andThen(Single.fromCallable(cursorRequestSupplier::get))
                .flatMap(cursorRequest -> hasAnyPermission(authenticatedUser, organizationId, environmentId, domainId, Permission.APPLICATION, Acl.READ)
                        .filter(hasPermission -> hasPermission)
                        .flatMapSingle(__ -> applicationSearcher.searchByDomainCursor(organizationId, domainId, cursorRequest, ownerEmail, pageSize))
                        .switchIfEmpty(
                                getResourceIdsWithPermission(authenticatedUser, ReferenceType.APPLICATION, Permission.APPLICATION, Acl.READ)
                                        .toList()
                                        .flatMap(ids -> applicationSearcher.searchByDomainAndIdsCursor(organizationId, domainId, ids, cursorRequest, ownerEmail, pageSize)))
                        .map(cursorPage ->
                                new ApplicationCursorPage(
                                        cursorPage.getData().stream().map(a -> FilteredApplication.of(a, expands)).toList(),
                                        CursorPagination.nextCursorPath(cursorPage.getNextCursor(), uriInfo, basePath),
                                        cursorPage.getTotalCount(),
                                        cursorRequest.getPage())))
                .onErrorResumeNext(ex -> ex instanceof IllegalArgumentException
                        ? Single.error(new BadRequestException(ex.getMessage()))
                        : Single.error(ex));
    }

    private static ApplicationCursorRequest cursorRequest(String cursorEncoded, String sort, String direction, int page,
                                                          String query, String status, List<ApplicationType> types) {
        Boolean enabled = status == null ? null : STATUS_ENABLED.equalsIgnoreCase(status);
        return CursorPagination.cursorRequest(cursorEncoded, sort, direction,
                () -> ApplicationCursorRequest.initialCursor(sort, direction, page, query, enabled, types),
                cursor -> new ApplicationCursorRequest(cursor.lastSortValue(), cursor.id(), cursor.sortDirection(), sort, page, query, enabled, types));
    }
}
