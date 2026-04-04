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
package io.gravitee.am.reporter.tcp.client;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TcpWriteStream}.
 *
 * <p>Uses a real embedded Vert.x server to verify TCP connectivity.</p>
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TcpWriteStreamTest {

    // Use an ephemeral-style port unlikely to be in use
    private static final int TEST_PORT = 29999;
    private static final String TEST_HOST = "localhost";

    private Vertx vertx;
    private NetServer server;
    private TcpWriteStream writeStream;
    private final List<String> receivedMessages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        writeStream = new TcpWriteStream(
                vertx,
                TEST_HOST,
                TEST_PORT,
                new NetClientOptions().setConnectTimeout(2000),
                3,    // reconnectAttempts
                200L, // reconnectInterval ms
                500L  // retryTimeout ms
        );
        receivedMessages.clear();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        writeStream.close();
        if (server != null) {
            CountDownLatch latch = new CountDownLatch(1);
            server.close().onComplete(ar -> latch.countDown());
            latch.await(2, TimeUnit.SECONDS);
            server = null;
        }
        CountDownLatch latch = new CountDownLatch(1);
        vertx.close().onComplete(ar -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // Connection state
    // -------------------------------------------------------------------------

    @Test
    @Order(1)
    void isConnected_shouldBeFalse_beforeInitialize() {
        assertThat(writeStream.isConnected()).isFalse();
    }

    @Test
    @Order(2)
    void writeQueueFull_shouldBeTrue_whenNotConnected() {
        assertThat(writeStream.writeQueueFull()).isTrue();
    }

    @Test
    @Order(3)
    void initialize_shouldConnect_whenServerAvailable() throws InterruptedException {
        startServer();

        CountDownLatch latch = new CountDownLatch(1);
        writeStream.initialize().onComplete(ar -> latch.countDown());

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(writeStream.isConnected()).isTrue();
    }

    @Test
    @Order(4)
    void initialize_shouldScheduleReconnect_whenNoServerAvailable() throws InterruptedException {
        // No server — connect should fail but not throw
        CountDownLatch initLatch = new CountDownLatch(1);
        writeStream.initialize().onComplete(ar -> initLatch.countDown());

        // The future from initialize() completes (failed) when connect fails
        assertThat(initLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(writeStream.isConnected()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    @Test
    @Order(5)
    void write_shouldReturnFailedFuture_whenNotConnected() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        writeStream.write(Buffer.buffer("data")).onFailure(err -> latch.countDown());
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @Order(6)
    void write_shouldSendDataToServer() throws InterruptedException {
        startServer();

        CountDownLatch connectLatch = new CountDownLatch(1);
        writeStream.initialize().onComplete(ar -> connectLatch.countDown());
        assertThat(connectLatch.await(3, TimeUnit.SECONDS)).isTrue();

        CountDownLatch messageLatch = new CountDownLatch(1);
        writeStream.write(Buffer.buffer("hello tcp\r\n"))
                .onSuccess(v -> messageLatch.countDown())
                .onFailure(err -> messageLatch.countDown());

        assertThat(messageLatch.await(2, TimeUnit.SECONDS)).isTrue();

        // Allow server to process
        Thread.sleep(100);
        assertThat(receivedMessages).anyMatch(m -> m.contains("hello tcp"));
    }

    // -------------------------------------------------------------------------
    // Reconnect handlers
    // -------------------------------------------------------------------------

    @Test
    @Order(7)
    void addReconnectHandler_shouldBeCalledOnConnect() throws InterruptedException {
        startServer();

        CountDownLatch handlerLatch = new CountDownLatch(1);
        writeStream.addReconnectHandler(v -> handlerLatch.countDown());

        writeStream.initialize();

        assertThat(handlerLatch.await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @Order(8)
    void multipleReconnectHandlers_allShouldBeCalled() throws InterruptedException {
        startServer();

        AtomicInteger count = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);
        writeStream.addReconnectHandler(v -> { count.incrementAndGet(); latch.countDown(); });
        writeStream.addReconnectHandler(v -> { count.incrementAndGet(); latch.countDown(); });

        writeStream.initialize();

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(count.get()).isEqualTo(2);
    }

    @Test
    @Order(9)
    void removeReconnectHandler_shouldNotBeCalled() throws InterruptedException {
        startServer();

        AtomicInteger count = new AtomicInteger(0);
        io.vertx.core.Handler<Void> handler = v -> count.incrementAndGet();
        writeStream.addReconnectHandler(handler);
        writeStream.removeReconnectHandler(handler);

        CountDownLatch connectLatch = new CountDownLatch(1);
        writeStream.initialize().onComplete(ar -> connectLatch.countDown());
        connectLatch.await(3, TimeUnit.SECONDS);

        // Allow a bit of time for the handler to (not) fire
        Thread.sleep(200);
        assertThat(count.get()).isZero();
    }

    // -------------------------------------------------------------------------
    // Close
    // -------------------------------------------------------------------------

    @Test
    @Order(10)
    void close_shouldDisconnect() throws InterruptedException {
        startServer();

        CountDownLatch connectLatch = new CountDownLatch(1);
        writeStream.initialize().onComplete(ar -> connectLatch.countDown());
        connectLatch.await(3, TimeUnit.SECONDS);
        assertThat(writeStream.isConnected()).isTrue();

        writeStream.close();

        Thread.sleep(100);
        assertThat(writeStream.isConnected()).isFalse();
    }

    @Test
    @Order(11)
    void close_shouldPreventReconnect() throws InterruptedException {
        AtomicInteger reconnectCount = new AtomicInteger(0);
        writeStream.addReconnectHandler(v -> reconnectCount.incrementAndGet());

        // Close before any connection attempt
        writeStream.close();

        // Start server — reconnect should not happen
        startServer();
        Thread.sleep(600);

        assertThat(reconnectCount.get()).isZero();
    }

    // -------------------------------------------------------------------------
    // Reconnect after server restart
    // -------------------------------------------------------------------------

    @Test
    @Order(12)
    void shouldReconnect_afterServerRestart() throws InterruptedException {
        startServer();

        AtomicInteger reconnectCount = new AtomicInteger(0);
        CountDownLatch firstConnect = new CountDownLatch(1);
        CountDownLatch secondConnect = new CountDownLatch(2);

        writeStream.addReconnectHandler(v -> {
            reconnectCount.incrementAndGet();
            firstConnect.countDown();
            secondConnect.countDown();
        });

        writeStream.initialize();
        assertThat(firstConnect.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(writeStream.isConnected()).isTrue();

        // Stop server — triggers disconnect
        CountDownLatch stopLatch = new CountDownLatch(1);
        server.close().onComplete(ar -> stopLatch.countDown());
        stopLatch.await(2, TimeUnit.SECONDS);
        server = null;

        Thread.sleep(100);

        // Restart server on same port
        startServer();

        // Wait for reconnection
        assertThat(secondConnect.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(writeStream.isConnected()).isTrue();
        assertThat(reconnectCount.get()).isGreaterThanOrEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void startServer() {
        CountDownLatch latch = new CountDownLatch(1);
        server = vertx.createNetServer(new NetServerOptions().setPort(TEST_PORT).setHost("localhost"));
        server.connectHandler(socket -> socket.handler(buf -> receivedMessages.add(buf.toString())));
        server.listen().onComplete(ar -> latch.countDown());
        try {
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
