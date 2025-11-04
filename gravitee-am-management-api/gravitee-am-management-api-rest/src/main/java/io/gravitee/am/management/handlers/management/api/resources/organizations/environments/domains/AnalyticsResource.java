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

import io.gravitee.am.management.handlers.management.api.model.AnalyticsParam;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.management.service.AnalyticsService;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AnalyticsResource extends AbstractResource {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "findDomainAnalytics",
            summary = "Find domain analytics",
            description = "User must have DOMAIN_ANALYTICS[READ] permission on the specified domain " +
                    "or DOMAIN_ANALYTICS[READ] permission on the specified environment " +
                    "or DOMAIN_ANALYTICS[READ] permission on the specified organization")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analytics successfully fetched"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @BeanParam AnalyticsParam param,
            @Suspended final AsyncResponse response) {

        // validate param
        param.validate();

        AnalyticsQuery query = new AnalyticsQuery();
        query.setType(param.getType());
        query.setField(param.getField());
        query.setFrom(param.getFrom());
        query.setTo(param.getTo());
        query.setDomain(domainId);
        query.setInterval(param.getInterval());
        query.setSize(param.getSize());

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_ANALYTICS, Acl.READ)
                .andThen(domainService.findById(domainId)
                        .switchIfEmpty(Single.defer(() -> Single.error(new DomainNotFoundException(domainId))))
                        .flatMap(domain -> analyticsService.execute(domain, query)))
                .subscribe(response::resume, response::resume);
    }
}
