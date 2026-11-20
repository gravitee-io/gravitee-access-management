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
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.Router;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultReactorTest {

    private static final String DOMAIN_NOT_FOUND = "404 No endpoint matches the request URI.";
    private static final String NO_DOMAIN_FOUND = "404 No security domain matches the request URI.";

    private Vertx vertx;
    private DefaultReactor reactor;
    private HttpServer server;
    private HttpClient client;

    private VertxSecurityDomainHandler app1;
    private VertxSecurityDomainHandler app2;
    private VertxSecurityDomainHandler root;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        reactor = new DefaultReactor();
        inject("vertx", vertx);
        inject("environment", new StandardEnvironment());
        inject("transactionHandlerFactory", new TransactionHandlerFactory());
        reactor.afterPropertiesSet();

        server = await(vertx.getDelegate().createHttpServer().requestHandler(reactor.route().getDelegate()).listen(0));
        client = vertx.getDelegate().createHttpClient();

        app1 = app1();
        app2 = domainHandler("app2", "/app2");
        root = root();
    }

    @AfterEach
    void tearDown() throws Exception {
        await(vertx.getDelegate().close());
    }

    @Test
    void shouldRoutePathDomainsWhenRootVhostMountedBefore() throws Exception {
        reactor.mountDomain(app1);
        reactor.mountDomain(root);
        reactor.mountDomain(app2);

        assertExpectedRouting();
    }

    @Test
    void shouldRoutePathDomainsWhenRootVhostMountedFirst() throws Exception {
        reactor.mountDomain(root);
        reactor.mountDomain(app1);
        reactor.mountDomain(app2);

        assertExpectedRouting();
    }

    @Test
    void shouldRoutePathDomainsAfterPathDomainRemount() throws Exception {
        reactor.mountDomain(app1);
        reactor.mountDomain(root);
        reactor.mountDomain(app2);

        reactor.unMountDomain(app1);
        reactor.mountDomain(app1());

        assertExpectedRouting();
    }

    @Test
    void shouldRoutePathDomainsAfterRootVhostRemount() throws Exception {
        reactor.mountDomain(app1);
        reactor.mountDomain(root);
        reactor.mountDomain(app2);

        reactor.unMountDomain(root);
        reactor.mountDomain(root());

        assertExpectedRouting();
    }

    @Test
    void shouldRouteToRootVhostAfterPathDomainUnmount() throws Exception {
        reactor.mountDomain(root);
        reactor.mountDomain(app2);

        reactor.unMountDomain(app2);

        assertThat(call("host-a.localhost", "/app2/oidc/test")).isEqualTo(DOMAIN_NOT_FOUND);
        assertThat(call("host-b.localhost", "/app2/oidc/test")).isEqualTo(NO_DOMAIN_FOUND);
    }

    private void assertExpectedRouting() throws Exception {
        assertThat(call("host-a.localhost", "/app1/oidc/test")).isEqualTo("200 app1");
        assertThat(call("host-a.localhost", "/app2/oidc/test")).isEqualTo("200 app2");
        assertThat(call("host-a.localhost", "/oidc/test")).isEqualTo("200 root");
        assertThat(call("host-a.localhost", "/unknown/oidc/test")).isEqualTo(DOMAIN_NOT_FOUND);
        assertThat(call("host-b.localhost", "/app1/oidc/test")).isEqualTo("200 app1");
        assertThat(call("host-b.localhost", "/app2/oidc/test")).isEqualTo("200 app2");
        assertThat(call("host-b.localhost", "/root/oidc/test")).isEqualTo("200 root");
        assertThat(call("host-b.localhost", "/oidc/test")).isEqualTo(NO_DOMAIN_FOUND);
    }

    private String call(String host, String path) throws Exception {
        RequestOptions options = new RequestOptions()
                .setServer(SocketAddress.inetSocketAddress(server.actualPort(), "localhost"))
                .setHost(host)
                .setPort(80)
                .setURI(path);
        return await(client.request(options)
                .compose(request -> request.send())
                .compose(response -> response.body().map(body -> response.statusCode() + " " + body)));
    }

    private VertxSecurityDomainHandler app1() {
        return domainHandler("app1", "/app1");
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

    private void inject(String name, Object value) throws Exception {
        Field field = DefaultReactor.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(reactor, value);
    }

    private static <T> T await(io.vertx.core.Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
