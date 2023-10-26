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

package io.gravitee.am.gateway.handler.root.resources.handler;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.RoutingContext;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BodyHandler implements Handler<RoutingContext> {

    private final Handler<RoutingContext> delegatedBodyHandler;

    public BodyHandler(Handler<RoutingContext> delegatedBodyHandler) {
        this.delegatedBodyHandler = delegatedBodyHandler;
    }

    @Override
    public void handle(RoutingContext context) {
        if (!context.request().method().equals(HttpMethod.GET)) {
            delegatedBodyHandler.handle(context);
        } else {
            context.next();
        }

    }
}