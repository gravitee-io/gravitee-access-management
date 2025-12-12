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
import io.gravitee.am.management.handlers.management.api.sort.SortParam;
import io.gravitee.am.model.*;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.PageSortRequest;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ProtectedResourceService;
import io.gravitee.am.service.model.NewProtectedResource;
import io.gravitee.am.service.model.NewProtectedResourceFeature;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import static io.gravitee.am.model.ProtectedResource.Type.fromString;

@Tag(name = "protected-resource")
public class ProtectedResourcesResource extends AbstractDomainResource {

    @Autowired
    private ProtectedResourceService service;

    @Path("{protected-resource}")
    public ProtectedResourceResource getProtectedResourceResource() {
        return resourceContext.getResource(ProtectedResourceResource.class);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createProtectedResource",
            summary = "Create a Protected Resource definition",
            description = "User must have PROTECTED_RESOURCE[CREATE] permission on the specified domain " +
                    "or PROTECTED_RESOURCE[CREATE] permission on the specified environment " +
                    "or PROTECTED_RESOURCE[CREATE] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Protected Resource successfully created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProtectedResourceSecret.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void createProtectedResource(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(name = "protected-resource", required = true)
            @Valid @NotNull final NewProtectedResource newProtectedResource,
            @Suspended final AsyncResponse response) {
        User authenticatedUser = getAuthenticatedUser();

        checkKeyUniqueness(newProtectedResource)
                .andThen(checkAnyPermission(organizationId, environmentId, domainId, Permission.PROTECTED_RESOURCE, Acl.CREATE))
                .andThen(checkDomainExists(domainId)
                        .flatMap(existingDomain -> service.create(existingDomain, authenticatedUser, newProtectedResource)
                                .map(protectedResource -> Response
                                        .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domainId + "/protected-resources/" + protectedResource.getId()))
                                        .entity(protectedResource)
                                        .build())))
                .subscribe(response::resume, response::resume);
    }

    private Completable checkKeyUniqueness(NewProtectedResource request){
        return Single.just(request).flatMapCompletable(req -> {
            List<NewProtectedResourceFeature> features = req.getFeatures();
            if(features != null && !features.isEmpty()){
                long uniqueSize = features.stream()
                        .map(NewProtectedResourceFeature::getKey)
                        .map(String::trim)
                        .distinct()
                        .count();
                if(uniqueSize < features.size()){
                    return Completable.error(new BadRequestException("Feature key names must be unique"));
                }
            }
            return Completable.complete();
        });
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listProtectedResources",
            summary = "List registered protected resources for a security domain",
            description = "User must have the PROTECTED_RESOURCE[LIST] permission on the specified domain, environment or organization " +
                    "AND either PROTECTED_RESOURCE[READ] permission on each domain's protected resource " +
                    "or PROTECTED_RESOURCE[READ] permission on the specified domain " +
                    "or PROTECTED_RESOURCE[READ] permission on the specified environment " +
                    "or PROTECTED_RESOURCE[READ] permission on the specified organization. " +
                    "Each returned protected resource is filtered and contains only basic information such as id, name, description and isEnabled.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "List registered protected resources for a security domain",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProtectedResourcesResource.ProtectedResourcePage.class))
            ),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @QueryParam("type") String type,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size,
            @Parameter(schema = @Schema(type = "string"))
            @QueryParam("sort") @DefaultValue("updatedAt.desc") SortParam sort,
            @QueryParam("q") String query,
            @Suspended final AsyncResponse response) {
        User authenticatedUser = getAuthenticatedUser();
        ProtectedResource.Type resourceType = fromString(type);

        PageSortRequest pageSortRequest = PageSortRequest.builder()
                .page(page)
                .size(size)
                .sortBy(sort.getSortBy())
                .asc(sort.isAscending())
                .build();

        checkAnyPermission(organizationId, environmentId, domainId, Permission.PROTECTED_RESOURCE, Acl.LIST)
                .andThen(checkDomainExists(domainId).ignoreElement())
                .andThen(hasAnyPermission(authenticatedUser, organizationId, environmentId, domainId, Permission.PROTECTED_RESOURCE, Acl.READ)
                        .filter(hasPermission -> hasPermission)
                        .flatMapSingle(__ -> listProtectedResources(domainId, resourceType, query, pageSortRequest))
                        .switchIfEmpty(
                                getResourceIdsWithPermission(authenticatedUser, ReferenceType.APPLICATION, Permission.PROTECTED_RESOURCE, Acl.READ)
                                        .toList()
                                        .flatMap(ids -> listProtectedResourcesByIds(domainId, resourceType, ids, query, pageSortRequest))))
                .subscribe(response::resume, response::resume);
    }

    private Single<Page<ProtectedResourcePrimaryData>> listProtectedResources(String domain, ProtectedResource.Type type, String query, PageSortRequest pageSortRequest) {
        if (StringUtils.hasText(query)) {
            return service.search(domain, type, query, pageSortRequest);
        } else {
            return service.findByDomainAndType(domain, type, pageSortRequest);
        }
    }

    private Single<Page<ProtectedResourcePrimaryData>> listProtectedResourcesByIds(String domain, ProtectedResource.Type type, List<String> ids, String query, PageSortRequest pageSortRequest) {
        if (StringUtils.hasText(query)) {
            return service.search(domain, type, ids, query, pageSortRequest);
        } else {
            return service.findByDomainAndTypeAndIds(domain, type, ids, pageSortRequest);
        }
    }



    @Schema
    public static final class ProtectedResourcePage extends Page<ProtectedResourcePrimaryData> {
        public ProtectedResourcePage(Collection<ProtectedResourcePrimaryData> data, int currentPage, long totalCount) {
            super(data, currentPage, totalCount);
        }
    }

}
