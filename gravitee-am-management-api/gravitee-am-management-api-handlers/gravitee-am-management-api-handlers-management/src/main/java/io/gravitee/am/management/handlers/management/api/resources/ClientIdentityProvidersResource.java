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

import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"domain", "oauth2"})
public class ClientIdentityProvidersResource {

    @Autowired
    private IdentityProviderService identityProviderService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private DomainService domainService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get identity providers associated to the client")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List identity providers associated to the client",
                    response = IdentityProvider.class, responseContainer = "Set"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public List<IdentityProvider> get(
            @PathParam("domain") String domain,
            @PathParam("client") String client) throws DomainNotFoundException {
        domainService.findById(domain);
        clientService.findById(client);

        return identityProviderService.findByClient(client);
    }
}
