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
package io.gravitee.am.gateway.handler.openshell.resources.endpoint;

import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.openshell.constants.OpenShellConstants;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Public endpoint that returns the OpenShell sandbox policy YAML for an agent application.
 *
 * Route: GET /openshell-policy/:appId
 *
 * No authentication required — the policy describes sandbox constraints, not secrets.
 *
 * @author GraviteeSource Team
 */
public class OpenShellPolicyEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(OpenShellPolicyEndpoint.class);

    private ClientSyncService clientSyncService;

    @Override
    public void handle(RoutingContext context) {
        final String appId = context.pathParam(OpenShellConstants.APP_ID_PARAM);

        clientSyncService.findById(appId)
                .subscribe(
                        client -> {
                            final String policy = client.getOpenShellPolicy();
                            if (policy == null || policy.isBlank()) {
                                context.response()
                                        .setStatusCode(404)
                                        .end("No OpenShell policy configured for this application");
                                return;
                            }
                            context.response()
                                    .setStatusCode(200)
                                    .putHeader("Content-Type", "text/plain; charset=utf-8")
                                    .putHeader("Cache-Control", "no-store")
                                    .end(policy);
                        },
                        error -> {
                            logger.error("Error fetching client {} for OpenShell policy", appId, error);
                            context.response().setStatusCode(500).end("Internal server error");
                        },
                        () -> context.response().setStatusCode(404).end("Application not found")
                );
    }

    public ClientSyncService getClientSyncService() {
        return clientSyncService;
    }

    public OpenShellPolicyEndpoint setClientSyncService(ClientSyncService clientSyncService) {
        this.clientSyncService = clientSyncService;
        return this;
    }
}
