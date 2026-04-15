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
package io.gravitee.am.gateway.handler.aauth;

import io.gravitee.am.gateway.handler.aauth.resources.endpoint.AAuthJWKSEndpoint;
import io.gravitee.am.gateway.handler.aauth.resources.endpoint.AAuthPSMetadataEndpoint;
import io.gravitee.am.gateway.handler.aauth.resources.endpoint.AAuthTokenEndpoint;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthAgentResolveHandler;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthSignatureHandler;
import io.gravitee.am.gateway.handler.aauth.resources.handler.AAuthTokenRequestParseHandler;
import io.gravitee.am.gateway.handler.api.AbstractProtocolProvider;
import io.gravitee.am.model.Domain;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.CorsHandler;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AAUTH protocol provider. Mounts the AAUTH sub-router on /aauth.
 * Implements the Person Server (PS) role per the AAUTH protocol specification.
 *
 * @author GraviteeSource Team
 */
public class AAuthProvider extends AbstractProtocolProvider {

    @Autowired
    private Domain domain;

    @Autowired
    private Vertx vertx;

    @Autowired
    private Router router;

    @Autowired
    private CorsHandler corsHandler;

    @Autowired
    private AAuthJWKSEndpoint aAuthJWKSEndpoint;

    @Autowired
    private AAuthSignatureHandler aAuthSignatureHandler;

    @Autowired
    private AAuthAgentResolveHandler aAuthAgentResolveHandler;

    @Autowired
    private AAuthTokenRequestParseHandler aAuthTokenRequestParseHandler;

    @Autowired
    private AAuthTokenEndpoint aAuthTokenEndpoint;

    @Override
    public String path() {
        return "/aauth";
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (domain.getAauth() != null && domain.getAauth().isEnabled()) {
            startAAuthProtocol();
        }
    }

    private void startAAuthProtocol() {
        final Router aAuthRouter = Router.router(vertx);

        // PS metadata endpoint
        aAuthRouter.route(HttpMethod.GET, "/.well-known/aauth-person.json")
                .handler(corsHandler)
                .handler(new AAuthPSMetadataEndpoint());

        // PS JWKS endpoint — domain signing keys for auth token verification
        aAuthRouter.route(HttpMethod.GET, "/.well-known/jwks.json")
                .handler(corsHandler)
                .handler(aAuthJWKSEndpoint);

        // PS token endpoint — issues aa-auth+jwt tokens (Section 7.1.3)
        aAuthRouter.route(HttpMethod.POST, "/token")
                .handler(corsHandler)
                .handler(BodyHandler.create())
                .handler(aAuthSignatureHandler)
                .handler(aAuthAgentResolveHandler)
                .handler(aAuthTokenRequestParseHandler)
                .handler(aAuthTokenEndpoint);

        router.route(subRouterPath()).subRouter(aAuthRouter);
    }
}
