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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.model.Reporter;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"reporter"})
public class ReportersResource extends AbstractResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ReporterService reporterService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List registered reporters for a security domain")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List registered reporters for a security domain", response = Reporter.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(@PathParam("domain") String domain,
                     @QueryParam("userProvider") boolean userProvider,
                     @Suspended final AsyncResponse response) {
        domainService.findById(domain)
                .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                .flatMapSingle(irrelevant -> reporterService.findByDomain(domain))
                .map(reporters -> Response.ok(reporters).build())
                .subscribe(
                        result -> response.resume(result),
                        error -> response.resume(error));
    }

    @Path("{reporter}")
    public ReporterResource getReporterResource() {
        return resourceContext.getResource(ReporterResource.class);
    }
}
