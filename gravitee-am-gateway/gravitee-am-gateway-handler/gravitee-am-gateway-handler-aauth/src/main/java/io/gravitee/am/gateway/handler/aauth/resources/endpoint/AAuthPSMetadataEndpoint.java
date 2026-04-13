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
package io.gravitee.am.gateway.handler.aauth.resources.endpoint;

import io.gravitee.am.gateway.handler.aauth.service.metadata.AAuthPSMetadata;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Serves the AAUTH Person Server (PS) metadata document at
 * /.well-known/aauth-person.json per the AAUTH protocol specification.
 *
 * @author GraviteeSource Team
 */
@Slf4j
public class AAuthPSMetadataEndpoint implements Handler<RoutingContext> {

    private static final String AAUTH_PATH = "/aauth";

    @Override
    public void handle(RoutingContext context) {
        String basePath = "/";
        try {
            basePath = UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH));
        } catch (Exception e) {
            log.error("Unable to resolve AAUTH PS metadata endpoint base path", e);
        }

        final String aAuthBase = basePath + AAUTH_PATH;

        final AAuthPSMetadata metadata = new AAuthPSMetadata(
                aAuthBase,
                aAuthBase + "/token",
                aAuthBase + "/.well-known/jwks.json"
        );

        context.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .putHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .end(Json.encodePrettily(metadata));
    }
}
