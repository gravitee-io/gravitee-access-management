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

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ErrorStatusMetricsHandlerTest extends RxWebTestBase {

    private final GatewayMetricProvider metricProvider = mock(GatewayMetricProvider.class);

    @Override
    public void setUp() throws Exception {
        super.setUp();
        router.route().handler(new ErrorStatusMetricsHandler(metricProvider));
        router.route("/ok").handler(rc -> rc.response().setStatusCode(200).end());
        router.route("/redirect").handler(rc -> rc.response().setStatusCode(302).end());
        router.route("/bad").handler(rc -> rc.response().setStatusCode(400).end());
        router.route("/fail").handler(rc -> rc.fail(503));
    }

    @Test
    public void shouldNotCountSuccessResponse() throws Exception {
        testRequest(HttpMethod.GET, "/ok", HttpStatusCode.OK_200, "OK");
        verify(metricProvider, never()).incrementError();
    }

    @Test
    public void shouldNotCountRedirect() throws Exception {
        testRequest(HttpMethod.GET, "/redirect", HttpStatusCode.FOUND_302, "Found");
        verify(metricProvider, never()).incrementError();
    }

    @Test
    public void shouldCountClientErrorOnce() throws Exception {
        testRequest(HttpMethod.GET, "/bad", HttpStatusCode.BAD_REQUEST_400, "Bad Request");
        verify(metricProvider, times(1)).incrementError();
    }

    @Test
    public void shouldCountServerErrorOnce() throws Exception {
        testRequest(HttpMethod.GET, "/fail", HttpStatusCode.SERVICE_UNAVAILABLE_503, "Service Unavailable");
        verify(metricProvider, times(1)).incrementError();
    }
}
