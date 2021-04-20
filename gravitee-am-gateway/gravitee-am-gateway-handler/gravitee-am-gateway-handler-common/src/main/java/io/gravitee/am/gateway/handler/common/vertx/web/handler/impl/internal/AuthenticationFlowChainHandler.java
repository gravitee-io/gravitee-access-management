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

import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationFlowChainHandler implements Handler<RoutingContext> {

    private final List<AuthenticationFlowStep> steps;

    public AuthenticationFlowChainHandler(List<AuthenticationFlowStep> steps) {
        this.steps = steps;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        new AuthenticationFlowChain(steps).exitHandler(stepHandler -> stepHandler.handle(routingContext)).handle(routingContext);
    }
}
