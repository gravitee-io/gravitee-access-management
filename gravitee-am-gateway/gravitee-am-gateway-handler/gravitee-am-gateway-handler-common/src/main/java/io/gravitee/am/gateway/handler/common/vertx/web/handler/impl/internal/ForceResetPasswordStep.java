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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal;


import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Optional;

import static java.util.Optional.ofNullable;

public class ForceResetPasswordStep extends AuthenticationFlowStep {

    private final JWTService jwtService;
    private final CertificateManager certificateManager;

    public ForceResetPasswordStep(Handler<RoutingContext> handler, JWTService jwtService, CertificateManager certificateManager) {
        super(handler);
        this.jwtService = jwtService;
        this.certificateManager = certificateManager;
    }

    @Override
    public void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {
        Optional<io.vertx.rxjava3.ext.auth.User> user = ofNullable(routingContext.user());
        if (user.isEmpty()) {
            flow.doNext(routingContext);
        } else {
            final User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser();
            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            if (endUser != null && Boolean.TRUE.equals(endUser.getForceResetPassword())) {
                JWT jwt = new JWT();
                jwt.setSub(endUser.getId());
                jwt.setAud(client.getId());
                jwt.setIat(System.currentTimeMillis());
                jwt.setClaimsRequestParameter(routingContext.request().uri());
                String jwtString = jwtService.encode(jwt, certificateManager.defaultCertificateProvider()).blockingGet();
                routingContext.put(ConstantKeys.TOKEN_CONTEXT_KEY, jwtString);
                routingContext.put(ConstantKeys.RETURN_URL_KEY, routingContext.request().uri());
                flow.exit(this);
            } else {
                flow.doNext(routingContext);
            }
        }

    }
}
