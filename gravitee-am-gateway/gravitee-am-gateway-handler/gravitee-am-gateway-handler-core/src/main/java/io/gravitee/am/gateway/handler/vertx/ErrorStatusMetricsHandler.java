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

import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;

/**
 * Count every response sent with a 4xx or 5xx status code, whatever the handler which produced it.
 * The status is evaluated when the response headers are written, so each response is counted once.
 */
class ErrorStatusMetricsHandler implements Handler<RoutingContext> {

    private final GatewayMetricProvider metricProvider;

    ErrorStatusMetricsHandler() {
        this(new GatewayMetricProvider());
    }

    ErrorStatusMetricsHandler(GatewayMetricProvider metricProvider) {
        this.metricProvider = metricProvider;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.addHeadersEndHandler(v -> {
            if (routingContext.response().getStatusCode() >= HttpStatusCode.BAD_REQUEST_400) {
                metricProvider.incrementError();
            }
        });
        routingContext.next();
    }
}
