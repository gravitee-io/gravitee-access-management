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

import io.gravitee.am.gateway.handler.common.utils.RedirectUrlResolver;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import static io.gravitee.common.http.HttpHeaders.LOCATION;

@Slf4j
public class FinalRedirectLocationHandler implements Handler<RoutingContext> {
    private final RedirectUrlResolver redirectUrlResolver = new RedirectUrlResolver();

    @Override
    public void handle(RoutingContext routingContext) {
        final String url = redirectUrlResolver.resolveRedirectUrl(routingContext);
        routingContext.response().putHeader(LOCATION, url).setStatusCode(302).end();
    }
}
