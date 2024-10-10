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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

public abstract class AmRequestHandler implements Handler<RoutingContext> {

    public abstract void handle(AmContext amContext);

    public final void handle(RoutingContext context) {
        if (context.get("_am") instanceof AmContext amContext) {
            handle(amContext);
        } else {
            AmContext.prepare(context);
            handle(context);
        }
    }

}
