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
package io.gravitee.am.gateway.handler.oidc.resources.endpoint;

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oidc.service.clientregistration.DynamicClientRegistrationTemplate;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class DynamicClientRegistrationTemplateEndpoint implements Handler<RoutingContext> {

    private ClientSyncService clientSyncService;

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicClientRegistrationTemplateEndpoint.class);

    public DynamicClientRegistrationTemplateEndpoint(ClientSyncService clientSyncService) {
        this.clientSyncService = clientSyncService;
    }

    @Override
    public void handle(RoutingContext context) {
        LOGGER.debug("Dynamic client registration TEMPLATE endpoint");

        this.clientSyncService.findTemplates()
            .subscribe(
                templates ->
                    context
                        .response()
                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                        .putHeader(HttpHeaders.PRAGMA, "no-cache")
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .setStatusCode(HttpStatusCode.OK_200)
                        .end(Json.encodePrettily(DynamicClientRegistrationTemplate.from(templates))),
                error -> context.fail(error)
            );
    }
}
