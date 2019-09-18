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
package io.gravitee.am.management.handlers.management.api.resources.dashboard;

import io.gravitee.am.management.handlers.management.api.model.ApplicationListItem;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.model.TopApplication;
import io.gravitee.am.service.model.TotalApplication;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"dashboard"})
public class DashboardApplicationsResource extends AbstractResource {

    private static final int MAX_APPS_FOR_DASHBOARD = 25;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List last updated applications")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List last updated applications",
                    response = ApplicationListItem.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void listApplications(@QueryParam("page") @DefaultValue("0") int page,
                            @QueryParam("size") @DefaultValue("10") int size,
                            @QueryParam("domainId") String domainId,
                            @Suspended final AsyncResponse response) {
        int selectedSize = Math.min(size, MAX_APPS_FOR_DASHBOARD);

        Single.just(Optional.ofNullable(domainId))
            .flatMap(optDomainId -> {
                if (optDomainId.isPresent()) {
                    return domainService.findById(domainId)
                            .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                            .flatMapSingle(domain -> applicationService.findByDomain(domainId, page, selectedSize));
                } else {
                    return applicationService.findAll(page, selectedSize);
                }
            })
            .map(pagedApplication -> {
                List<ApplicationListItem> applications = pagedApplication.getData()
                        .stream()
                        .map(ApplicationListItem::convert)
                        .sorted((a1, a2) -> a2.getUpdatedAt().compareTo(a1.getUpdatedAt()))
                        .collect(Collectors.toList());
                return new Page(applications, pagedApplication.getCurrentPage(), pagedApplication.getTotalCount());
            })
            .subscribe(
                    result -> response.resume(result),
                    error -> response.resume(error)
            );
    }

    @Path("top")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List top applications by access tokens count")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List top applications by access tokens count",
                    response = TopApplication.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void listTopApplications(@QueryParam("size") @DefaultValue("10") int size,
                               @QueryParam("domainId") String domainId,
                               @Suspended final AsyncResponse response) {
        int selectedSize = Math.min(size, MAX_APPS_FOR_DASHBOARD);

        Single.just(Optional.ofNullable(domainId))
                .flatMap(optDomainId -> {
                    if (optDomainId.isPresent()) {
                        return domainService.findById(domainId)
                                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domainId)))
                                .flatMapSingle(domain -> applicationService.findTopApplicationsByDomain(domainId));
                    } else {
                        return applicationService.findTopApplications();
                    }
                })
                .map(topApplications -> topApplications.stream()
                        .sorted((c1, c2) -> Long.compare(c2.getAccessTokens(), c1.getAccessTokens()))
                        .limit(selectedSize)
                        .collect(Collectors.toList()))
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error)
                );
    }

    @Path("total")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List applications count")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List applications count", response = TotalApplication.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void listTotalClients(@QueryParam("domainId") String domainId,
                                 @Suspended final AsyncResponse response) {
        Single.just(Optional.ofNullable(domainId))
                .flatMap(optDomainId -> {
                    if (optDomainId.isPresent()) {
                        return applicationService.countByDomain(domainId);
                    } else {
                        return applicationService.count();
                    }
                })
                .map(applications -> new TotalApplication(applications))
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

}
