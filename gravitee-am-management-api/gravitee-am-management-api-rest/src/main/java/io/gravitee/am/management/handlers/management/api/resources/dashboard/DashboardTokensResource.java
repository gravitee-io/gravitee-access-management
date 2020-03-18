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

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.service.TokenService;
import io.gravitee.am.service.model.TotalToken;
import io.gravitee.common.http.MediaType;
import io.reactivex.Single;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"dashboard"})
public class DashboardTokensResource extends AbstractResource {

    @Autowired
    private TokenService tokenService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get access tokens count")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Get access tokens count",
                    response = TotalToken.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void listTokensCount(@QueryParam("domainId") String domainId,
                                @Suspended final AsyncResponse response) {
        Single<TotalToken> totalTokenSingle;
        if (domainId != null) {
            totalTokenSingle = tokenService.findTotalTokensByDomain(domainId);
        } else {
            totalTokenSingle = tokenService.findTotalTokens();
        }
        totalTokenSingle.subscribe(
                result -> response.resume(result),
                error -> response.resume(error));
    }

}