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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent;

import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

public class ConsentStageHandler implements Handler<RoutingContext> {

    private final Handler<RoutingContext> scopesHandler;
    private final Handler<RoutingContext> authorizationDetailsHandler;

    public ConsentStageHandler(Handler<RoutingContext> scopesHandler, Handler<RoutingContext> authorizationDetailsHandler) {
        this.scopesHandler = scopesHandler;
        this.authorizationDetailsHandler = authorizationDetailsHandler;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        if (ConsentStage.of(routingContext) == ConsentStage.AUTHORIZATION_DETAILS) {
            authorizationDetailsHandler.handle(routingContext);
        } else {
            scopesHandler.handle(routingContext);
        }
    }
}
