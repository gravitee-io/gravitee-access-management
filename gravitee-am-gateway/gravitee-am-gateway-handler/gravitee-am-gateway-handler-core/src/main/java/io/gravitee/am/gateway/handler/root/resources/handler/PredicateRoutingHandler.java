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
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;

import java.util.function.Predicate;

@RequiredArgsConstructor
public class PredicateRoutingHandler implements Handler<RoutingContext> {
    private final Predicate<RoutingContext> predicate;
    private final Handler<RoutingContext> positiveHandler;
    private final Handler<RoutingContext> negativeHandler;

    @Override
    public void handle(RoutingContext routingContext) {
        if (predicate.test(routingContext)) {
            positiveHandler.handle(routingContext);

        } else {
            negativeHandler.handle(routingContext);
        }
    }

    public static PredicateRoutingHandler handleWhen(Predicate<RoutingContext> predicate, Handler<RoutingContext> positiveHandler) {
        return new PredicateRoutingHandler(predicate, positiveHandler, RoutingContext::next);
    }
}
