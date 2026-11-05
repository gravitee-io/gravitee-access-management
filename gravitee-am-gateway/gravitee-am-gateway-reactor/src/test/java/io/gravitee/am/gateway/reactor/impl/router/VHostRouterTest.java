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
package io.gravitee.am.gateway.reactor.impl.router;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.VirtualHost;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.AllowForwardHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VHostRouterTest {

    private Vertx vertx;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
    }

    @After
    public void tearDown() {
        vertx.close();
    }

    @Test
    public void shouldMatchVhostHostIgnoringCase() throws Exception {
        Router delegate = mock(Router.class);
        RoutingContext routingContext = routingContext("VALID.HOST.GRAVITEE.IO", "/test/login");
        VHostRouter router = router(delegate, "valid.host.gravitee.io", "/test");

        router.handleContext(routingContext);

        verify(delegate).handleContext(routingContext);
        verify(routingContext, never()).next();
    }

    @Test
    public void shouldMatchVhostOnForwardedHostWhenForwardHeadersAllowed() throws Exception {
        int status = send(AllowForwardHeaders.X_FORWARD, "/test/login", MultiMap.caseInsensitiveMultiMap()
                .add("X-Forwarded-Host", "auth.example.com"));

        assertEquals(200, status);
    }

    @Test
    public void shouldIgnoreForwardedHostWhenForwardHeadersNotAllowed() throws Exception {
        int status = send(AllowForwardHeaders.NONE, "/test/login", MultiMap.caseInsensitiveMultiMap()
                .add("X-Forwarded-Host", "auth.example.com"));

        assertEquals(404, status);
    }

    @Test
    public void shouldMatchVhostOnFirstForwardedHostWithoutDefaultPort() throws Exception {
        int status = send(AllowForwardHeaders.X_FORWARD, "/test/login", MultiMap.caseInsensitiveMultiMap()
                .add("X-Forwarded-Host", "auth.example.com:443, edge.example.com")
                .add("X-Forwarded-Proto", "https"));

        assertEquals(200, status);
    }

    @Test
    public void shouldRouteFailureOnForwardedHostAfterTheHeaderIsRemoved() throws Exception {
        int status = send(AllowForwardHeaders.X_FORWARD, "/test/fail", MultiMap.caseInsensitiveMultiMap()
                .add("X-Forwarded-Host", "auth.example.com"));

        assertEquals(418, status);
    }

    private int send(AllowForwardHeaders allowForward, String path, MultiMap headers) throws Exception {
        Router domainRouter = Router.router(vertx);
        domainRouter.route("/login").handler(ctx -> ctx.response().end());
        domainRouter.route("/fail").handler(ctx -> {
            ctx.request().headers().remove("X-Forwarded-Host");
            ctx.fail(500);
        });
        domainRouter.route().failureHandler(ctx -> ctx.response().setStatusCode(418).end());

        Router root = Router.router(vertx).allowForward(allowForward);
        root.route("/test/*").subRouter(router(domainRouter, "auth.example.com", "/test"));

        HttpServer server = vertx.createHttpServer().requestHandler(root).listen(0)
                .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        HttpClient client = vertx.createHttpClient();
        try {
            RequestOptions options = new RequestOptions()
                    .setMethod(HttpMethod.GET)
                    .setHost("localhost")
                    .setPort(server.actualPort())
                    .setURI(path)
                    .setHeaders(headers);
            return client
                    .request(options)
                    .compose(request -> request.send())
                    .map(HttpClientResponse::statusCode)
                    .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally {
            client.close();
            server.close();
        }
    }

    private RoutingContext routingContext(String host, String path) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.authority()).thenReturn(HostAndPort.authority(host));
        when(request.path()).thenReturn(path);

        RoutingContext routingContext = mock(RoutingContext.class);
        when(routingContext.request()).thenReturn(request);

        return routingContext;
    }

    private VHostRouter router(Router delegate, String host, String path) throws Exception {
        VirtualHost virtualHost = new VirtualHost();
        virtualHost.setHost(host);
        virtualHost.setPath(path);

        Constructor<VHostRouter> constructor = VHostRouter.class.getDeclaredConstructor(Vertx.class, Domain.class, VirtualHost.class, Router.class);
        constructor.setAccessible(true);

        return constructor.newInstance(vertx, new Domain(), virtualHost, delegate);
    }
}
