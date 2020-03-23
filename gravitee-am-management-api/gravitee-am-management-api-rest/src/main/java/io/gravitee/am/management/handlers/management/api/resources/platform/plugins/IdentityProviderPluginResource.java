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
package io.gravitee.am.management.handlers.management.api.resources.platform.plugins;

import io.gravitee.am.management.service.IdentityProviderPluginService;
import io.gravitee.am.management.service.exception.IdentityProviderPluginNotFoundException;
import io.gravitee.am.management.service.exception.IdentityProviderPluginSchemaNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.Maybe;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Plugin", "Identity Provider"})
public class IdentityProviderPluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private IdentityProviderPluginService identityProviderPluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get an identity provider",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void get(
            @PathParam("identity") String identityProviderId,
            @Suspended final AsyncResponse response) {

        identityProviderPluginService.findById(identityProviderId)
                .switchIfEmpty(Maybe.error(new IdentityProviderPluginNotFoundException(identityProviderId)))
                .map(identityProviderPlugin -> Response.ok(identityProviderPlugin).build())
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("schema")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get an identity provider plugin's schema",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void getSchema(
            @PathParam("identity") String identityProviderId,
            @Suspended final AsyncResponse response) {

        // Check that the identity provider exists
        identityProviderPluginService.findById(identityProviderId)
                .switchIfEmpty(Maybe.error(new IdentityProviderPluginNotFoundException(identityProviderId)))
                .flatMap(irrelevant -> identityProviderPluginService.getSchema(identityProviderId))
                .switchIfEmpty(Maybe.error(new IdentityProviderPluginSchemaNotFoundException(identityProviderId)))
                .map(identityProviderPluginSchema -> Response.ok(identityProviderPluginSchema).build())
                .subscribe(response::resume, response::resume
                );
    }
}