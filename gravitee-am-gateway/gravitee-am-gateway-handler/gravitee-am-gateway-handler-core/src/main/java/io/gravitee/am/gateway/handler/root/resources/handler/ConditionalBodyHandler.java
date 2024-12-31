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
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import org.springframework.core.env.Environment;

import static io.gravitee.am.gateway.core.LegacySettingsKeys.HANDLER_ALWAYS_APPLY_BODY_HDL;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ConditionalBodyHandler implements Handler<RoutingContext> {
    private final Handler<RoutingContext> delegatedBodyHandler;
    private final boolean alwaysApplyBodyHandler;

    public ConditionalBodyHandler(Environment env) {
        this(env, BodyHandler.create());
    }

    ConditionalBodyHandler(Environment env, Handler<RoutingContext> bodyHandler) {
        this.delegatedBodyHandler = bodyHandler;
        this.alwaysApplyBodyHandler = HANDLER_ALWAYS_APPLY_BODY_HDL.from(env);
    }

    @Override
    public void handle(RoutingContext context) {
        if (!context.request().method().equals(HttpMethod.GET) || alwaysApplyBodyHandler) {
            delegatedBodyHandler.handle(context);
        } else {
            context.next();
        }
    }
}
