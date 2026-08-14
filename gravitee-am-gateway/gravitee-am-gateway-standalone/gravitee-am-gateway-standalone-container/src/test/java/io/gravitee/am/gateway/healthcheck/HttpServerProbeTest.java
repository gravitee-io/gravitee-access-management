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
package io.gravitee.am.gateway.healthcheck;

import io.gravitee.node.api.healthcheck.Result;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.NetClient;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.net.NetServer;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class HttpServerProbeTest {

    private static final int TIMEOUT_SECONDS = 10;

    private Vertx vertx;
    private NetServer server;
    private CountDownLatch serverSideClose;
    private HttpServerProbe cut;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        serverSideClose = new CountDownLatch(1);
        server =
            vertx
                .createNetServer()
                .connectHandler(socket -> socket.closeHandler(event -> serverSideClose.countDown()))
                .rxListen(0, "localhost")
                .blockingGet();

        cut = new HttpServerProbe();
        setField(cut, "vertx", vertx);
        setField(cut, "host", "localhost");
        setField(cut, "port", server.actualPort());
    }

    @AfterEach
    void tearDown() {
        vertx.rxClose().blockingAwait();
    }

    @Test
    void shouldReportHealthyWhenServerAcceptsConnection() throws Exception {
        assertThat(check().isHealthy()).isTrue();
    }

    @Test
    void shouldReportUnhealthyWhenNoServerAcceptsConnection() throws Exception {
        server.rxClose().blockingAwait();

        assertThat(check().isHealthy()).isFalse();
    }

    @Test
    void shouldCloseConnectionItOpened() throws Exception {
        check();

        assertThat(serverSideClose.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void shouldNotRegisterNewNetClientOnEveryCheck() throws Exception {
        check();
        long afterFirstCheck = registeredNetClients();

        for (int i = 0; i < 9; i++) {
            check();
        }

        assertThat(registeredNetClients()).isEqualTo(afterFirstCheck);
    }

    private Result check() throws Exception {
        return cut.check().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private long registeredNetClients() throws Exception {
        CloseFuture closeFuture = ((VertxInternal) vertx.getDelegate()).closeFuture();
        Field childrenField = CloseFuture.class.getDeclaredField("children");
        childrenField.setAccessible(true);
        Map<?, ?> children = (Map<?, ?>) childrenField.get(closeFuture);
        return children == null ? 0 : children.keySet().stream().filter(NetClient.class::isInstance).count();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
