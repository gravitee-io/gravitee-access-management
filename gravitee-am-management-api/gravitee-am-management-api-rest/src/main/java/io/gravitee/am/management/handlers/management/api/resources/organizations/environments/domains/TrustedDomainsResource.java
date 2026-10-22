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
import io.gravitee.am.management.handlers.management.api.resources.model.CursorApiRequest;
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
import jakarta.validation.constraints.Max;
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
import java.util.Collection;
import java.util.function.Supplier;

@Tag(name = "trusted-domain")
public class TrustedDomainsResource extends AbstractResource {

    private static final String DEFAULT_LIMIT = "50";
    private static final int MAX_LIMIT = 100;
    private static final int UNBOUNDED_LIMIT = Integer.MAX_VALUE - 1;
    private static final String SORT_FIELD_NAME = "name";

    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_SORT = "sort";
    private static final String PARAM_DIR = "dir";
    private static final String PARAM_PAGE = "page";
    private static final String PARAM_USAGE = "usage";
    private static final String PARAM_CURSOR = "cursor";

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
            description = "List trusted domains using cursor-based pagination. " +
                    "User must have the DOMAIN_TRUST_DOMAIN[LIST] permission on the specified domain " +
                    "or DOMAIN_TRUST_DOMAIN[LIST] permission on the specified environment " +
                    "or DOMAIN_TRUST_DOMAIN[LIST] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of trusted domains",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TrustedDomainPage.class))),
            @ApiResponse(responseCode = "400", description = "Invalid limit, sort, dir, page or usage"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(description = "Maximum number of trusted domains per page. When omitted, every trusted domain is returned in a single page.")
            @QueryParam(PARAM_LIMIT) @Min(1) @Max(MAX_LIMIT) Integer limit,
            @Parameter(description = "Sort field. Trusted domains can only be sorted by name.")
            @QueryParam(PARAM_SORT) @DefaultValue(SORT_FIELD_NAME) String sort,
            @Parameter(description = "Sort direction, ASC or DESC.")
            @QueryParam(PARAM_DIR) @DefaultValue("ASC") String direction,
            @Parameter(description = "Zero-based index of the page to return. Requires limit.")
            @QueryParam(PARAM_PAGE) @DefaultValue("0") @Min(0) int page,
            @Parameter(description = "Keeps only the trusted domains trusted for this usage. Empty keeps every trusted domain.")
            @QueryParam(PARAM_USAGE) TrustedDomainUsage usage,
            @Suspended final AsyncResponse response,
            @Context UriInfo uriInfo) {
        processCursorRequest(organizationId, environmentId, domainId,
                () -> {
                    if (limit == null && page > 0) {
                        throw new IllegalArgumentException("Query parameter 'page' requires 'limit'");
                    }
                    return TrustedDomainCursorRequest.initialCursor(sort, direction, page, usage);
                },
                limit == null ? UNBOUNDED_LIMIT : limit, uriInfo)
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("_cursor")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listTrustedDomainsCursor",
            summary = "List the next page of trusted domains registered against the security domain",
            description = "Follows the nextCursor returned by the trusted domains list. " +
                    "User must have the DOMAIN_TRUST_DOMAIN[LIST] permission on the specified domain " +
                    "or DOMAIN_TRUST_DOMAIN[LIST] permission on the specified environment " +
                    "or DOMAIN_TRUST_DOMAIN[LIST] permission on the specified organization")
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
            @Parameter(description = "Maximum number of trusted domains per page.")
            @QueryParam(PARAM_LIMIT) @DefaultValue(DEFAULT_LIMIT) @Min(1) @Max(MAX_LIMIT) int limit,
            @Parameter(description = "Sort field. Trusted domains can only be sorted by name.")
            @QueryParam(PARAM_SORT) @DefaultValue(SORT_FIELD_NAME) String sort,
            @Parameter(description = "Sort direction, ASC or DESC.")
            @QueryParam(PARAM_DIR) @DefaultValue("ASC") String direction,
            @Parameter(description = "Zero-based index of the page to return.")
            @QueryParam(PARAM_PAGE) @DefaultValue("0") @Min(0) int page,
            @Parameter(description = "Keeps only the trusted domains trusted for this usage. Empty keeps every trusted domain.")
            @QueryParam(PARAM_USAGE) TrustedDomainUsage usage,
            @Parameter(description = "Cursor of the page to return, as found in the nextCursor of the previous page.")
            @QueryParam(PARAM_CURSOR) String cursorEncoded,
            @Suspended final AsyncResponse response,
            @Context UriInfo uriInfo) {
        processCursorRequest(organizationId, environmentId, domainId,
                () -> {
                    CursorApiRequest cursorApiRequest = CursorApiRequest.decode(cursorEncoded);
                    return new TrustedDomainCursorRequest(
                            cursorApiRequest.lastSortValue(),
                            cursorApiRequest.id(),
                            CursorRequest.SortDirection.valueOf(direction.toUpperCase()),
                            sort,
                            page,
                            usage);
                },
                limit, uriInfo)
                .subscribe(response::resume, response::resume);
    }

    private Single<TrustedDomainPage> processCursorRequest(String organizationId,
                                                           String environmentId,
                                                           String domainId,
                                                           Supplier<TrustedDomainCursorRequest> cursorRequestSupplier,
                                                           int limit,
                                                           UriInfo uriInfo) {
        return checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_TRUST_DOMAIN, Acl.LIST)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Single.error(new DomainNotFoundException(domainId))))
                .flatMap(domain -> Single.fromCallable(cursorRequestSupplier::get))
                .flatMap(cursorRequest -> trustDomainService.findByReferenceCursor(ReferenceType.DOMAIN, domainId, cursorRequest, limit)
                        .map(cursorPage -> new TrustedDomainPage(
                                cursorPage.getData(),
                                nextCursorPath(cursorPage.getNextCursor(), uriInfo,
                                        "/organizations/%s/environments/%s/domains/%s/trusted-domains/_cursor".formatted(organizationId, environmentId, domainId)),
                                cursorPage.getTotalCount(),
                                cursorRequest.getPage())))
                .onErrorResumeNext(ex -> ex instanceof IllegalArgumentException
                        ? Single.error(new BadRequestException(ex.getMessage()))
                        : Single.error(ex));
    }

    private String nextCursorPath(TrustedDomainCursorRequest nextCursor, UriInfo uriInfo, String baseUri) {
        if (nextCursor == null) {
            return null;
        }
        var uri = uriInfo.getRequestUriBuilder()
                .replaceQueryParam(PARAM_CURSOR, new CursorApiRequest(nextCursor.getLastId(), nextCursor.getLastSortValue()).encode())
                .replaceQueryParam(PARAM_PAGE, nextCursor.getPage())
                .build();
        return baseUri + "?" + uri.getRawQuery();
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

    public record TrustedDomainPage(Collection<TrustedDomain> data, String nextCursor, Long totalCount, int page) {
    }
}
