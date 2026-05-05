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
package io.gravitee.am.gateway.handler.cimd;

import io.gravitee.am.gateway.handler.api.AbstractProtocolProvider;
import io.gravitee.am.gateway.handler.cimd.resources.endpoint.CimdLogoEndpoint;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdLogoCacheService;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataDocumentManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.handler.CorsHandler;
import org.springframework.beans.factory.annotation.Autowired;

public class CimdProvider extends AbstractProtocolProvider {

    @Autowired
    private Vertx vertx;

    @Autowired
    private Router router;

    @Autowired
    private CorsHandler corsHandler;

    @Autowired
    private CimdMetadataDocumentManager cimdMetadataDocumentManager;

    @Autowired
    private CimdLogoCacheService cimdLogoCacheService;

    @Autowired
    private Domain domain;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        final Router cimdRouter = Router.router(vertx);
        cimdRouter.route().handler(corsHandler);
        cimdRouter.get("/logo").handler(new CimdLogoEndpoint(domain, cimdMetadataDocumentManager, cimdLogoCacheService));
        cimdRouter.route().failureHandler(new ErrorHandler());

        router.route(subRouterPath()).subRouter(cimdRouter);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

    @Override
    public String path() {
        return "/cimd";
    }
}
