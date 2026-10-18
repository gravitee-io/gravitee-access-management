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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization;

import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class AuthorizationChainExtensionRegistry {

    @Autowired(required = false)
    private List<AuthorizationChainExtension> authorizationChainExtensions = List.of();

    public Handler<RoutingContext> completionHandler() {
        return authorizationChainExtensions.stream()
                .map(AuthorizationChainExtension::completionHandler)
                .reduce(AuthorizationChainExtensionRegistry::chain)
                .orElse(RoutingContext::next);
    }

    public Handler<RoutingContext> failureHandler() {
        return authorizationChainExtensions.stream()
                .map(AuthorizationChainExtension::failureHandler)
                .reduce(AuthorizationChainExtensionRegistry::chain)
                .orElse(RoutingContext::next);
    }

    private static Handler<RoutingContext> chain(Handler<RoutingContext> first, Handler<RoutingContext> second) {
        return context -> first.handle(new RoutingContext(context.getDelegate()) {
            @Override
            public void next() {
                second.handle(context);
            }
        });
    }
}
