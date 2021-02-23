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
package io.gravitee.am.gateway.handler.saml2;

import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.saml2.resources.endpoint.ServiceProviderMetadataEndpoint;
import io.gravitee.am.gateway.handler.saml2.service.sp.ServiceProviderService;
import io.gravitee.common.service.AbstractService;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.CorsHandler;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SAML2Provider extends AbstractService<ProtocolProvider> implements ProtocolProvider {

    @Autowired
    private Vertx vertx;

    @Autowired
    private Router router;

    @Autowired
    private CorsHandler corsHandler;

    @Autowired
    private ServiceProviderService serviceProviderService;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // Start SAML 2.0 provider
        startSAML2Protocol();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

    @Override
    public String path() {
        return "/saml2";
    }

    private void startSAML2Protocol() {
        // Create the SAML 2.0 router
        final Router saml2Router = Router.router(vertx);

        // SP Metadata endpoint
        saml2Router.route(HttpMethod.GET, "/sp/metadata/:providerId")
                .handler(corsHandler)
                .handler(new ServiceProviderMetadataEndpoint(serviceProviderService));

        router.mountSubRouter(path(), saml2Router);
    }

}
