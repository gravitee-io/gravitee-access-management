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
package io.gravitee.am.gateway.handler.discovery;

import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.discovery.resources.endpoint.ProviderConfigurationEndpoint;
import io.gravitee.am.gateway.handler.discovery.service.DiscoveryService;
import io.gravitee.common.service.AbstractService;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.CorsHandler;
import org.springframework.beans.factory.annotation.Autowired;

import static io.gravitee.am.gateway.handler.discovery.constants.DiscoveryConstants.DISCOVERY_PATH;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class DiscoveryProvider extends AbstractService<ProtocolProvider> implements ProtocolProvider {

    @Autowired
    private Vertx vertx;

    @Autowired
    private Router router;

    @Autowired
    private CorsHandler corsHandler;

    @Autowired
    private DiscoveryService discoveryService;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        //start discovery
        startDiscovery();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

    @Override
    public String path() {
        return DISCOVERY_PATH;
    }

    private void startDiscovery() {
        final Router discoveryRouter = Router.router(vertx);

        // UMA Provider configuration information endpoint
        Handler<RoutingContext> providerConfigurationEndpoint = new ProviderConfigurationEndpoint();
        ((ProviderConfigurationEndpoint) providerConfigurationEndpoint).setDiscoveryService(discoveryService);
        discoveryRouter.route().handler(corsHandler);
        discoveryRouter.get().handler(providerConfigurationEndpoint);

        // error handler
        discoveryRouter.route().failureHandler(new ErrorHandler());

        router.mountSubRouter(path(), discoveryRouter);
    }
}
