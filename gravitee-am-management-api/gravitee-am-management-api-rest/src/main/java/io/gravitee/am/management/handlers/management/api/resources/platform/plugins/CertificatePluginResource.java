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

import io.gravitee.am.service.CertificatePluginService;
import io.gravitee.am.service.exception.CertificatePluginNotFoundException;
import io.gravitee.am.service.exception.CertificatePluginSchemaNotFoundException;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"Plugin", "Certificate"})
public class CertificatePluginResource {

    @Context
    private ResourceContext resourceContext;

    @Inject
    private CertificatePluginService certificatePluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get an certificate plugin",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void get(
            @PathParam("certificate") String certificateId,
            @Suspended final AsyncResponse response) {

        certificatePluginService.findById(certificateId)
                .map(extensionGrantPlugin -> Response.ok(certificateId).build())
                .switchIfEmpty(Maybe.error(new CertificatePluginNotFoundException(certificateId)))
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("schema")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get an certificate's schema",
            notes = "There is no particular permission needed. User must be authenticated.")
    public void getSchema(
            @PathParam("certificate") String certificateId,
            @Suspended final AsyncResponse response) {

        // Check that the certificate exists
        certificatePluginService.findById(certificateId)
                .switchIfEmpty(Maybe.error(new CertificatePluginNotFoundException(certificateId)))
                .flatMap(irrelevant -> certificatePluginService.getSchema(certificateId))
                .switchIfEmpty(Maybe.error(new CertificatePluginSchemaNotFoundException(certificateId)))
                .map(certificatePluginSchema -> Response.ok(certificatePluginSchema).build())
                .subscribe(response::resume, response::resume);
    }
}