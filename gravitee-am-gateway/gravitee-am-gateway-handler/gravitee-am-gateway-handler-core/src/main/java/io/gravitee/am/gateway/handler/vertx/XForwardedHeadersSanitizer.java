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
package io.gravitee.am.gateway.handler.vertx;

import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

class XForwardedHeadersSanitizer implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.request().headers().remove(HttpHeaders.X_FORWARDED_HOST);
        routingContext.request().headers().remove(HttpHeaders.X_FORWARDED_PORT);
        routingContext.request().headers().remove(HttpHeaders.X_FORWARDED_FOR);
        routingContext.request().headers().remove(HttpHeaders.X_FORWARDED_PREFIX);
        routingContext.request().headers().remove(HttpHeaders.X_FORWARDED_SERVER);
        routingContext.request().headers().remove(HttpHeaders.X_FORWARDED_PROTO);
        routingContext.next();
    }
}
