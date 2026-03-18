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
package io.gravitee.am.gateway.handler.openshell;

import io.gravitee.am.gateway.handler.api.AbstractProtocolProvider;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.openshell.resources.endpoint.OpenShellPolicyEndpoint;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.CorsHandler;
import org.springframework.beans.factory.annotation.Autowired;

import static io.gravitee.am.gateway.handler.openshell.constants.OpenShellConstants.APP_ID_PARAM;
import static io.gravitee.am.gateway.handler.openshell.constants.OpenShellConstants.OPENSHELL_PATH;

/**
 * @author GraviteeSource Team
 */
public class OpenShellProvider extends AbstractProtocolProvider {

    @Autowired
    private Vertx vertx;

    @Autowired
    private Router router;

    @Autowired
    private CorsHandler corsHandler;

    @Autowired
    private ClientSyncService clientSyncService;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        startOpenShell();
    }

    @Override
    public String path() {
        return OPENSHELL_PATH;
    }

    private void startOpenShell() {
        final Router openShellRouter = Router.router(vertx);

        Handler<RoutingContext> policyEndpoint = new OpenShellPolicyEndpoint()
                .setClientSyncService(clientSyncService);

        openShellRouter.route().handler(corsHandler);
        openShellRouter.get("/:" + APP_ID_PARAM).handler(policyEndpoint);
        openShellRouter.route().failureHandler(new ErrorHandler());

        router.mountSubRouter(path(), openShellRouter);
    }
}
