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
import io.gravitee.am.management.service.trustdomain.TrustedIssuerProjection;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.cursor.CursorRequest;
import io.gravitee.am.model.oidc.TrustedDomain;
import io.gravitee.am.model.oidc.TrustedDomainCursorRequest;
import io.gravitee.am.model.oidc.TrustedDomainUsage;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.TrustDomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.NewTrustedDomain;
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
import java.util.function.Supplier;

@Tag(name = "trusted-domain")
public class TrustedDomainsResource extends AbstractResource {

    private static final int UNBOUNDED_LIMIT = Integer.MAX_VALUE - 1;
    private static final String SORT_FIELD_NAME = "name";

    private static final String PARAM_USAGE = "usage";

    private static final String LIST_PERMISSION_DESCRIPTION = "User must have the DOMAIN_TRUST_DOMAIN[LIST] permission on the specified domain " +
            "or DOMAIN_TRUST_DOMAIN[LIST] permission on the specified environment " +
            "or DOMAIN_TRUST_DOMAIN[LIST] permission on the specified organization";

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private DomainService domainService;

    @Autowired
    private TrustDomainService trustDomainService;

    @Autowired
    private TrustedIssuerProjection trustedIssuerProjection;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listTrustedDomains",
            summary = "List trusted domains registered against the security domain",
            description = "List trusted domains using cursor-based pagination. Follow nextCursor to get the next page. " +
                    LIST_PERMISSION_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of trusted domains",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TrustedDomainPage.class))),
            @ApiResponse(responseCode = "400", description = "Invalid limit, sort, dir, page, usage or cursor"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(description = CursorPagination.LIMIT_DESCRIPTION + ". Omitting it returns every trusted domain in a single page.")
            @QueryParam(CursorPagination.PARAM_LIMIT) @Min(1) Integer limit,
            @Parameter(description = "Sort field. Trusted domains can only be sorted by name.")
            @QueryParam(CursorPagination.PARAM_SORT) @DefaultValue(SORT_FIELD_NAME) String sort,
            @Parameter(description = "Sort direction, ASC or DESC.")
            @QueryParam(CursorPagination.PARAM_DIR) @DefaultValue("ASC") String direction,
            @Parameter(description = CursorPagination.PAGE_DESCRIPTION + " Requires limit.")
            @QueryParam(CursorPagination.PARAM_PAGE) @DefaultValue("0") @Min(0) int page,
            @Parameter(description = "Keeps only the trusted domains trusted for this usage. Empty keeps every trusted domain.")
            @QueryParam(PARAM_USAGE) TrustedDomainUsage usage,
            @Parameter(description = CursorPagination.CURSOR_DESCRIPTION)
            @QueryParam(CursorPagination.PARAM_CURSOR) String cursorEncoded,
            @Suspended final AsyncResponse response,
            @Context UriInfo uriInfo) {
        listPage(organizationId, environmentId, domainId,
                () -> {
                    if (cursorEncoded == null && limit == null && page > 0) {
                        throw new IllegalArgumentException("Query parameter 'page' requires 'limit'");
                    }
                    return cursorRequest(cursorEncoded, sort, direction, page, usage);
                },
                limit == null ? UNBOUNDED_LIMIT : CursorPagination.pageSize(limit), "", uriInfo)
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("_cursor")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listTrustedDomainsCursor",
            deprecated = true,
            summary = "List the next page of trusted domains registered against the security domain",
            description = "Deprecated: pass the cursor to listTrustedDomains instead. " + LIST_PERMISSION_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of trusted domains",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TrustedDomainPage.class))),
            @ApiResponse(responseCode = "400", description = "Invalid cursor, limit, sort, dir, page or usage"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void cursorList(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(description = CursorPagination.LIMIT_DESCRIPTION)
            @QueryParam(CursorPagination.PARAM_LIMIT) @DefaultValue(CursorPagination.DEFAULT_LIMIT_STRING) @Min(1) int limit,
            @Parameter(description = "Sort field. Trusted domains can only be sorted by name.")
            @QueryParam(CursorPagination.PARAM_SORT) @DefaultValue(SORT_FIELD_NAME) String sort,
            @Parameter(description = "Sort direction, ASC or DESC.")
            @QueryParam(CursorPagination.PARAM_DIR) @DefaultValue("ASC") String direction,
            @Parameter(description = "Zero-based index of the page to return.")
            @QueryParam(CursorPagination.PARAM_PAGE) @DefaultValue("0") @Min(0) int page,
            @Parameter(description = "Keeps only the trusted domains trusted for this usage. Empty keeps every trusted domain.")
            @QueryParam(PARAM_USAGE) TrustedDomainUsage usage,
            @Parameter(description = CursorPagination.CURSOR_DESCRIPTION)
            @QueryParam(CursorPagination.PARAM_CURSOR) String cursorEncoded,
            @Suspended final AsyncResponse response,
            @Context UriInfo uriInfo) {
        // a blank cursor fails decoding, so a missing one is rejected
        String cursor = cursorEncoded == null ? "" : cursorEncoded;
        listPage(organizationId, environmentId, domainId,
                () -> cursorRequest(cursor, sort, direction, page, usage),
                CursorPagination.pageSize(limit), "/_cursor", uriInfo)
                .subscribe(response::resume, response::resume);
    }

    private Single<TrustedDomainPage> listPage(String organizationId,
                                               String environmentId,
                                               String domainId,
                                               Supplier<TrustedDomainCursorRequest> cursorRequestSupplier,
                                               int pageSize,
                                               String path,
                                               UriInfo uriInfo) {
        String basePath = "/organizations/%s/environments/%s/domains/%s/trusted-domains%s".formatted(organizationId, environmentId, domainId, path);
        return checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_TRUST_DOMAIN, Acl.LIST)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Single.error(new DomainNotFoundException(domainId))))
                .flatMap(domain -> Single.fromCallable(cursorRequestSupplier::get))
                .flatMap(cursorRequest -> trustDomainService.findByReferenceCursor(ReferenceType.DOMAIN, domainId, cursorRequest, pageSize)
                        .map(cursorPage -> new TrustedDomainPage(
                                cursorPage.getData(),
                                CursorPagination.nextCursorPath(cursorPage.getNextCursor(), uriInfo, basePath),
                                cursorPage.getTotalCount(),
                                cursorRequest.getPage())))
                .onErrorResumeNext(ex -> ex instanceof IllegalArgumentException
                        ? Single.error(new BadRequestException(ex.getMessage()))
                        : Single.error(ex));
    }

    private static TrustedDomainCursorRequest cursorRequest(String cursorEncoded, String sort, String direction, int page, TrustedDomainUsage usage) {
        return CursorPagination.cursorRequest(cursorEncoded, sort, direction,
                () -> TrustedDomainCursorRequest.initialCursor(sort, direction, page, usage),
                cursor -> new TrustedDomainCursorRequest(cursor.lastSortValue(), cursor.id(), cursor.sortDirection(), sort, page, usage));
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createTrustedDomain",
            summary = "Register a trusted domain on the security domain",
            description = "User must have the DOMAIN_TRUST_DOMAIN[CREATE] permission on the specified domain " +
                    "or DOMAIN_TRUST_DOMAIN[CREATE] permission on the specified environment " +
                    "or DOMAIN_TRUST_DOMAIN[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Trusted domain successfully created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TrustedDomain.class))),
            @ApiResponse(responseCode = "400", description = "Invalid trusted domain configuration"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(name = "trustedDomain", required = true) @Valid @NotNull final NewTrustedDomain newTrustedDomain,
            @Suspended final AsyncResponse response) {
        final var authenticatedUser = getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_TRUST_DOMAIN, Acl.CREATE)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId))))
                .flatMapSingle(domain -> trustDomainService.create(domain, newTrustedDomain, authenticatedUser)
                        .flatMap(td -> trustedIssuerProjection.syncStored(domain.getId()).andThen(Single.just(td))))
                .map(td -> Response
                        .created(URI.create("/organizations/" + organizationId
                                + "/environments/" + environmentId
                                + "/domains/" + domainId
                                + "/trusted-domains/" + td.getId()))
                        .entity(td)
                        .build())
                .subscribe(response::resume, response::resume);
    }

    @Path("{trustedDomainId}")
    public TrustedDomainResource getTrustedDomainResource() {
        return resourceContext.getResource(TrustedDomainResource.class);
    }

    public static final class TrustedDomainPage extends CursorApiPage<TrustedDomain> {
        public TrustedDomainPage(List<TrustedDomain> data, String nextCursor, Long totalCount, Integer page) {
            super(data, nextCursor, totalCount, page);
        }
    }
}
