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

import io.gravitee.am.management.service.PolicyPluginService;
import io.gravitee.am.management.service.exception.PolicyPluginNotFoundException;
import io.gravitee.am.management.service.exception.PolicyPluginSchemaNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import jakarta.inject.Inject;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Tags({@Tag(name= "Plugin"), @Tag(name= "Policy")})
public class PolicyPluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private PolicyPluginService policyPluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get a policy plugin",
            description = "There is no particular permission needed. User must be authenticated.")
    public void get(
            @PathParam("policy") String policyId,
            @Suspended final AsyncResponse response) {

        policyPluginService.findById(policyId)
                .switchIfEmpty(Maybe.error(new PolicyPluginNotFoundException(policyId)))
                .map(policyPlugin -> Response.ok(policyPlugin).build())
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("schema")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get a policy plugin's schema")
    public void getSchema(
            @PathParam("policy") String policyId,
            @Suspended final AsyncResponse response) {

        // Check that the policy exists
        policyPluginService.findById(policyId)
                .switchIfEmpty(Maybe.error(new PolicyPluginNotFoundException(policyId)))
                .flatMap(irrelevant -> policyPluginService.getSchema(policyId))
                .switchIfEmpty(Maybe.error(new PolicyPluginSchemaNotFoundException(policyId)))
                .map(policyPluginSchema -> Response.ok(policyPluginSchema).build())
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("documentation")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Get a policy plugin's documentation")
    public void getDocumentation(
        @PathParam("policy") String policyId,
        @Suspended final AsyncResponse response) {

        // Check that the policy exists
        policyPluginService.findById(policyId)
            .switchIfEmpty(Maybe.error(new PolicyPluginNotFoundException(policyId)))
            .flatMap(irrelevant -> policyPluginService.getDocumentation(policyId))
            .map(policyPluginDocumentation -> Response.ok(policyPluginDocumentation).build())
            .subscribe(response::resume, response::resume);
    }

}
