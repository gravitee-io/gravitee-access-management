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

import io.gravitee.am.gateway.reactor.impl.transaction.TransactionHandlerFactory;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.RequestOptions;
import io.vertx.rxjava3.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class DefaultReactorTest {

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

    private String hostSeenByRouter(boolean allowXForwardedHeaders) throws Exception {
        DefaultReactor reactor = new DefaultReactor();
        ReflectionTestUtils.setField(reactor, "vertx", vertx);
        ReflectionTestUtils.setField(reactor, "transactionHandlerFactory", new TransactionHandlerFactory());
        ReflectionTestUtils.setField(reactor, "allowXForwardedHeaders", allowXForwardedHeaders);
        reactor.afterPropertiesSet();
        reactor.route().get("/host").handler(ctx -> ctx.response().end(ctx.request().authority().host()));

        HttpServer server = vertx.getDelegate().createHttpServer().requestHandler(reactor.route().getDelegate()).listen(0)
                .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
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
