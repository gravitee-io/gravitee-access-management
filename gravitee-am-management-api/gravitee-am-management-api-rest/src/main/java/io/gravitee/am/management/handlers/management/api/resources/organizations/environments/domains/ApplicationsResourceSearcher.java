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
import io.gravitee.am.management.handlers.management.api.resources.model.CursorApiRequest;
import io.gravitee.am.management.handlers.management.api.resources.model.FilteredApplication;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationCursorRequest;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.cursor.CursorRequest;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationSearcher;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static io.gravitee.am.service.model.ApplicationFilter.STATUS_ENABLED;

@Tag(name = "application")
public class ApplicationsResourceSearcher extends AbstractDomainResource {
    private static final String MAX_APPLICATIONS_SIZE_PER_PAGE_STRING = "50";

    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_EXPAND = "expand";
    private static final String PARAM_SORT = "sort";
    private static final String PARAM_DIR = "dir";
    private static final String PARAM_PAGE = "page";
    private static final String PARAM_QUERY = "q";
    private static final String PARAM_STATUS = "status";
    private static final String PARAM_OWNER_EMAIL = "owner.email";
    private static final String PARAM_TYPE = "type";
    private static final String PARAM_CURSOR = "cursor";

    @Autowired
    private ApplicationSearcher applicationSearcher;

    @RequiredArgsConstructor
    public static final class ApplicationCursorPage {
        private final List<FilteredApplication> data;
        private final String nextCursor;
        private final Long totalCount;
        private final Integer page;
    }

    @GET
    @Path("_cursor")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "searchApplicationsCursor",
            summary = "List applications with cursor-based pagination",
            description = "List applications using cursor-based pagination for improved performance at scale. " +
                    "User must have APPLICATION[LIST] permission on the specified domain, environment or organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List applications with cursor pagination",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApplicationCursorPage.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void cursorSearch(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @QueryParam(PARAM_LIMIT) @DefaultValue(MAX_APPLICATIONS_SIZE_PER_PAGE_STRING) int limit,
            @QueryParam(PARAM_EXPAND) List<String> expandsParam,
            @QueryParam(PARAM_SORT) @DefaultValue("updatedAt") String sort,
            @QueryParam(PARAM_DIR) @DefaultValue("DESC") String direction,
            @QueryParam(PARAM_PAGE) @DefaultValue("0") int page,

            @QueryParam(PARAM_QUERY) String query,
            @QueryParam(PARAM_STATUS) @Pattern(regexp = "^enabled|disabled$") String status,
            @QueryParam(PARAM_OWNER_EMAIL) String ownerEmail,
            @QueryParam(PARAM_TYPE) List<ApplicationType> types,
            @QueryParam(PARAM_CURSOR) String cursorEncoded,
            @Suspended final AsyncResponse response,
            @Context UriInfo uriInfo) {
        Set<ApplicationExpand> expands = ApplicationExpand.convertToApplicationExpands(expandsParam);
        CursorApiRequest cursorApiRequest = CursorApiRequest.decode(cursorEncoded);
        ApplicationCursorRequest cursorRequest = new ApplicationCursorRequest(
                cursorApiRequest.lastSortValue(),
                cursorApiRequest.id(),
                CursorRequest.SortDirection.valueOf(direction.toUpperCase()),
                sort,
                page,
                query,
                status == null ? null : STATUS_ENABLED.equalsIgnoreCase(status),
                types
        );
        processCursorRequest(organizationId, environmentId, domainId, cursorRequest, ownerEmail, limit, expands, uriInfo)
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "searchApplications",
            summary = "List applications with cursor-based pagination",
            description = "List applications using cursor-based pagination for improved performance at scale. " +
                    "User must have APPLICATION[LIST] permission on the specified domain, environment or organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List applications with cursor pagination",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApplicationCursorPage.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void search(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @QueryParam(PARAM_LIMIT) @DefaultValue(MAX_APPLICATIONS_SIZE_PER_PAGE_STRING) int limit,
            @QueryParam(PARAM_SORT) @DefaultValue("updatedAt") String sort,
            @QueryParam(PARAM_DIR) @DefaultValue("DESC") String direction,
            @QueryParam(PARAM_PAGE) @DefaultValue("0") int page,
            @QueryParam(PARAM_EXPAND) List<String> expandsParam,

            @QueryParam(PARAM_QUERY) String query,
            @QueryParam(PARAM_STATUS) @Pattern(regexp = "^enabled|disabled$") String status,
            @QueryParam(PARAM_OWNER_EMAIL) String ownerEmail,
            @QueryParam(PARAM_TYPE) List<ApplicationType> types,

            @Suspended final AsyncResponse response,
            @Context UriInfo uriInfo) {
        Set<ApplicationExpand> expands = ApplicationExpand.convertToApplicationExpands(expandsParam);
        Boolean enabled = status == null ? null : STATUS_ENABLED.equalsIgnoreCase(status);
        ApplicationCursorRequest cursorRequest = ApplicationCursorRequest.initialCursor(sort, direction, page, query, enabled, types);
        processCursorRequest(organizationId, environmentId, domainId, cursorRequest, ownerEmail, limit, expands, uriInfo)
                .subscribe(response::resume, response::resume);
    }

    private Single<ApplicationCursorPage> processCursorRequest(
            String organizationId,
            String environmentId,
            String domainId,
            ApplicationCursorRequest cursorRequest,
            String ownerEmail,
            int limit,
            Set<ApplicationExpand> expands,
            UriInfo uriInfo) {
        User authenticatedUser = getAuthenticatedUser();
        return checkAnyPermission(organizationId, environmentId, domainId, Permission.APPLICATION, Acl.LIST)
                .andThen(checkDomainExists(domainId).ignoreElement())
                .andThen(hasAnyPermission(authenticatedUser, organizationId, environmentId, domainId, Permission.APPLICATION, Acl.READ)
                        .filter(hasPermission -> hasPermission)
                        .flatMapSingle(__ -> applicationSearcher.searchByDomainCursor(organizationId, domainId, cursorRequest, ownerEmail, limit))
                        .switchIfEmpty(
                                getResourceIdsWithPermission(authenticatedUser, ReferenceType.APPLICATION, Permission.APPLICATION, Acl.READ)
                                        .toList()
                                        .flatMap(ids -> applicationSearcher.searchByDomainAndIdsCursor(organizationId, domainId, ids, cursorRequest, ownerEmail, limit))))
                .map(cursorPage ->
                        new ApplicationCursorPage(
                                cursorPage.getData().stream().map(a -> FilteredApplication.of(a, expands)).toList(),
                                nextCursorPath(
                                        cursorPage.getNextCursor(),
                                        uriInfo,
                                        "/organizations/%s/environments/%s/domains/%s/applications/search/_cursor".formatted(organizationId, environmentId, domainId)),
                                cursorPage.getTotalCount(),
                                cursorRequest.getPage()))
                .onErrorResumeNext(ex -> ex instanceof IllegalArgumentException
                        ? Single.error(new BadRequestException(ex.getMessage()))
                        : Single.error(ex));
    }

    private String nextCursorPath(ApplicationCursorRequest nextCursor,
                                  UriInfo uriInfo,
                                  String baseUri) {
        if(nextCursor == null){
            return null;
        }
        UriBuilder requestUriBuilder = uriInfo.getRequestUriBuilder();
        var uri = requestUriBuilder
                .replaceQueryParam(PARAM_CURSOR, new CursorApiRequest(nextCursor.getLastId(), nextCursor.getLastSortValue()).encode())
                .replaceQueryParam(PARAM_PAGE, nextCursor.getPage())
                .build();
        return baseUri + "?" + uri.getQuery();
    }


}
