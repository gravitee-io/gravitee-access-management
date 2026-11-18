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
package io.gravitee.am.gateway.reactor.impl;

import io.gravitee.am.gateway.handler.vertx.VertxSecurityDomainHandler;
import io.gravitee.am.gateway.reactor.impl.transaction.TransactionHandlerFactory;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.VirtualHost;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.Router;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultReactorTest {

    private static final String DOMAIN_NOT_FOUND = "404 No endpoint matches the request URI.";
    private static final String NO_DOMAIN_FOUND = "404 No security domain matches the request URI.";

    private Vertx vertx;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
    }

    @After
    public void tearDown() {
        vertx.close().blockingAwait();
    }

    @Test
    public void shouldReadForwardedHostWhenForwardHeadersAllowed() throws Exception {
        assertEquals("auth.example.com", hostSeenByRouter(true));
    }

    @Test
    public void shouldIgnoreForwardedHostWhenForwardHeadersNotAllowed() throws Exception {
        assertEquals("localhost", hostSeenByRouter(false));
    }

    @Test
    public void shouldRoutePathDomainsWhenRootVhostMountedBefore() throws Exception {
        DefaultReactor reactor = reactor(false);
        reactor.mountDomain(app1());
        reactor.mountDomain(root());
        reactor.mountDomain(app2());

        assertExpectedRouting(reactor);
    }

    @Test
    public void shouldRoutePathDomainsWhenRootVhostMountedFirst() throws Exception {
        DefaultReactor reactor = reactor(false);
        reactor.mountDomain(root());
        reactor.mountDomain(app1());
        reactor.mountDomain(app2());

        assertExpectedRouting(reactor);
    }

    @Test
    public void shouldRoutePathDomainsAfterPathDomainRemount() throws Exception {
        DefaultReactor reactor = reactor(false);
        VertxSecurityDomainHandler app1 = app1();
        reactor.mountDomain(app1);
        reactor.mountDomain(root());
        reactor.mountDomain(app2());

        reactor.unMountDomain(app1);
        reactor.mountDomain(app1());

        assertExpectedRouting(reactor);
    }

    @Test
    public void shouldRoutePathDomainsAfterRootVhostRemount() throws Exception {
        DefaultReactor reactor = reactor(false);
        VertxSecurityDomainHandler root = root();
        reactor.mountDomain(app1());
        reactor.mountDomain(root);
        reactor.mountDomain(app2());

        reactor.unMountDomain(root);
        reactor.mountDomain(root());

        assertExpectedRouting(reactor);
    }

    @Test
    public void shouldRouteToRootVhostAfterPathDomainUnmount() throws Exception {
        DefaultReactor reactor = reactor(false);
        VertxSecurityDomainHandler app2 = app2();
        reactor.mountDomain(root());
        reactor.mountDomain(app2);

        reactor.unMountDomain(app2);

        HttpServer server = listen(reactor);
        try {
            assertEquals(DOMAIN_NOT_FOUND, call(server, "host-a.localhost", "/app2/oidc/test"));
            assertEquals(NO_DOMAIN_FOUND, call(server, "host-b.localhost", "/app2/oidc/test"));
        } finally {
            server.close();
        }
    }

    private void assertExpectedRouting(DefaultReactor reactor) throws Exception {
        HttpServer server = listen(reactor);
        try {
            assertEquals("200 app1", call(server, "host-a.localhost", "/app1/oidc/test"));
            assertEquals("200 app2", call(server, "host-a.localhost", "/app2/oidc/test"));
            assertEquals("200 root", call(server, "host-a.localhost", "/oidc/test"));
            assertEquals(DOMAIN_NOT_FOUND, call(server, "host-a.localhost", "/unknown/oidc/test"));
            assertEquals("200 app1", call(server, "host-b.localhost", "/app1/oidc/test"));
            assertEquals("200 app2", call(server, "host-b.localhost", "/app2/oidc/test"));
            assertEquals("200 root", call(server, "host-b.localhost", "/root/oidc/test"));
            assertEquals(NO_DOMAIN_FOUND, call(server, "host-b.localhost", "/oidc/test"));
        } finally {
            server.close();
        }
    }

    private DefaultReactor reactor(boolean allowXForwardedHeaders) {
        DefaultReactor reactor = new DefaultReactor();
        ReflectionTestUtils.setField(reactor, "vertx", vertx);
        ReflectionTestUtils.setField(reactor, "environment", new StandardEnvironment());
        ReflectionTestUtils.setField(reactor, "transactionHandlerFactory", new TransactionHandlerFactory());
        ReflectionTestUtils.setField(reactor, "allowXForwardedHeaders", allowXForwardedHeaders);
        reactor.afterPropertiesSet();
        return reactor;
    }

    private HttpServer listen(DefaultReactor reactor) throws Exception {
        return vertx.getDelegate().createHttpServer().requestHandler(reactor.route().getDelegate()).listen(0)
                .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private String call(HttpServer server, String host, String path) throws Exception {
        RequestOptions options = new RequestOptions()
                .setServer(SocketAddress.inetSocketAddress(server.actualPort(), "localhost"))
                .setHost(host)
                .setPort(80)
                .setURI(path);
        return vertx.getDelegate().createHttpClient()
                .request(options)
                .compose(request -> request.send())
                .compose(response -> response.body().map(body -> response.statusCode() + " " + body))
                .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private VertxSecurityDomainHandler app1() {
        return domainHandler("app1", "/app1");
    }

    private VertxSecurityDomainHandler app2() {
        return domainHandler("app2", "/app2");
    }

    private VertxSecurityDomainHandler root() {
        return domainHandler("root", "/root", vhost("host-a.localhost", "/"), vhost("host-b.localhost", "/root"));
    }

    private VertxSecurityDomainHandler domainHandler(String id, String path, VirtualHost... vhosts) {
        Domain domain = new Domain();
        domain.setId(id);
        domain.setPath(path);
        if (vhosts.length > 0) {
            domain.setVhostMode(true);
            domain.setVhosts(List.of(vhosts));
        }

        Router router = Router.router(vertx);
        router.route("/oidc/*").handler(context -> context.response().end(id));
        router.route().last().handler(context -> context.response().setStatusCode(404).end("No endpoint matches the request URI."));

        VertxSecurityDomainHandler handler = mock(VertxSecurityDomainHandler.class);
        when(handler.getDomain()).thenReturn(domain);
        when(handler.router()).thenReturn(router);
        return handler;
    }

    private VirtualHost vhost(String host, String path) {
        VirtualHost vhost = new VirtualHost();
        vhost.setHost(host);
        vhost.setPath(path);
        return vhost;
    }

    private String hostSeenByRouter(boolean allowXForwardedHeaders) throws Exception {
        DefaultReactor reactor = reactor(allowXForwardedHeaders);
        reactor.route().get("/host").handler(ctx -> ctx.response().end(ctx.request().authority().host()));

        HttpServer server = listen(reactor);
        try {
            RequestOptions options = new RequestOptions()
                    .setMethod(HttpMethod.GET)
                    .setHost("localhost")
                    .setPort(server.actualPort())
                    .setURI("/host")
                    .setHeaders(MultiMap.caseInsensitiveMultiMap().add("X-Forwarded-Host", "auth.example.com"));
            return vertx.getDelegate().createHttpClient()
                    .request(options)
                    .compose(request -> request.send())
                    .compose(HttpClientResponse::body)
                    .map(Object::toString)
                    .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally {
            server.close();
        }
    }
}
