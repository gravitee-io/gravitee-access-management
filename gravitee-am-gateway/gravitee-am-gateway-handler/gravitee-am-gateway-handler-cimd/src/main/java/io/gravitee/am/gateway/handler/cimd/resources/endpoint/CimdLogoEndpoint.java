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
package io.gravitee.am.gateway.handler.cimd.resources.endpoint;

import io.gravitee.am.gateway.handler.common.client.cimd.CachedLogo;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataDocumentManager;
import io.gravitee.am.gateway.handler.common.client.cimd.ClientIds;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Optional;

/**
 * Serves a pre-fetched CIMD client logo from the in-memory logo cache.
 *
 * The caller supplies the {@code clientId} (the CIMD metadata URL) as a query parameter.
 * This endpoint performs a pure cache lookup — it never fetches remotely.
 */
public class CimdLogoEndpoint implements Handler<RoutingContext> {

    private static final String PARAM_CLIENT_ID = "clientId";

    private final CimdMetadataDocumentManager cimdMetadataDocumentManager;

    public CimdLogoEndpoint(CimdMetadataDocumentManager cimdMetadataDocumentManager) {
        this.cimdMetadataDocumentManager = cimdMetadataDocumentManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final String clientId = routingContext.request().getParam(PARAM_CLIENT_ID);
        if (clientId == null || clientId.isBlank()) {
            routingContext.response().setStatusCode(400).end();
            return;
        }

        final Optional<CachedLogo> logo = cimdMetadataDocumentManager.getLogoByClientId(ClientIds.canonicalize(clientId));
        if (logo.isEmpty()) {
            routingContext.response().setStatusCode(404).end();
            return;
        }

        final CachedLogo cachedLogo = logo.get();
        routingContext.response()
                .putHeader("Content-Type", cachedLogo.contentType())
                .putHeader("Cache-Control", "max-age=" + cachedLogo.maxAgeSeconds())
                .setStatusCode(200)
                .end(Buffer.buffer(cachedLogo.bytes()));
    }
}
