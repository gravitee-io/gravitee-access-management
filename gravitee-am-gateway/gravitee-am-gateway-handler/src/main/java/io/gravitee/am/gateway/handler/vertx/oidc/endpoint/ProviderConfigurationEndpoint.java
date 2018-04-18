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
package io.gravitee.am.gateway.handler.vertx.oidc.endpoint;

import io.gravitee.am.gateway.handler.oidc.discovery.OpenIDDiscoveryService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ProviderConfigurationEndpoint implements Handler<RoutingContext> {

    private OpenIDDiscoveryService discoveryService;

    @Override
    public void handle(RoutingContext context) {
        context.response()
                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .end(Json.encodePrettily(discoveryService.getConfiguration()));
    }

    public OpenIDDiscoveryService getDiscoveryService() {
        return discoveryService;
    }

    public void setDiscoveryService(OpenIDDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }
}
