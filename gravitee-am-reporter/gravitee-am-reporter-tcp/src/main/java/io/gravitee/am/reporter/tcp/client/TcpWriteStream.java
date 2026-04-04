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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.streams.WriteStream;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Managed TCP write stream that:
 * <ul>
 *   <li>maintains a single {@link NetSocket} to the remote TCP server</li>
 *   <li>handles reconnection with configurable attempts and delays</li>
 *   <li>notifies registered handlers when the connection is (re-)established</li>
 *   <li>implements {@link WriteStream} so it can be stored in the {@code WriteStreamRegistry}</li>
 * </ul>
 *
 * <p>All connection-level settings (host, port, TLS options, reconnect parameters) are
 * supplied at construction time from the caller; this class has no knowledge of the
 * {@code gravitee.yaml} configuration structure.</p>
 *
 * <p>This class is thread-safe: {@link #isConnected()} and {@link #write(Buffer)} may be
 * called from any thread.</p>
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class TcpWriteStream implements WriteStream<Buffer> {

    private final Vertx vertx;
    private final String host;
    private final int port;
    private final NetClientOptions netClientOptions;
    private final int reconnectAttempts;
    private final long reconnectInterval;
    private final long retryTimeout;

    private NetClient netClient;
    private volatile NetSocket currentSocket;
    private volatile boolean connected = false;
    private volatile boolean closed = false;

    /** Tracks consecutive reconnect attempts in the current cycle. */
    private final AtomicInteger currentAttemptCount = new AtomicInteger(0);

    /** Handlers invoked each time a connection is (re-)established. */
    private final List<Handler<Void>> reconnectHandlers = new CopyOnWriteArrayList<>();

    private volatile Handler<Throwable> exceptionHandler;

    /**
     * @param vertx             Vert.x instance
     * @param host              remote TCP server hostname or IP
     * @param port              remote TCP server port
     * @param netClientOptions  pre-configured {@link NetClientOptions} (TLS, timeouts, …); the
     *                          caller is responsible for setting all relevant options
     * @param reconnectAttempts maximum reconnect attempts per cycle; {@code -1} means infinite
     * @param reconnectInterval delay in ms between reconnect attempts
     * @param retryTimeout      delay in ms before a new reconnect cycle after exhausting attempts
     */
    public TcpWriteStream(Vertx vertx,
                          String host,
                          int port,
                          NetClientOptions netClientOptions,
                          int reconnectAttempts,
                          long reconnectInterval,
                          long retryTimeout) {
        this.vertx = vertx;
        this.host = host;
        this.port = port;
        this.netClientOptions = netClientOptions;
        this.reconnectAttempts = reconnectAttempts;
        this.reconnectInterval = reconnectInterval;
        this.retryTimeout = retryTimeout;
    }

    /**
     * Creates the underlying {@link NetClient} and initiates the first connection.
     *
     * @return a future that completes (succeeded or failed) when the first connection attempt ends
     */
    public Future<Void> initialize() {
        this.netClient = vertx.createNetClient(netClientOptions);
        return doConnect();
    }

    private Future<Void> doConnect() {
        if (closed) {
            return Future.failedFuture("TcpWriteStream is closed");
        }
        log.debug("Attempting TCP connection to {}:{}", host, port);
        return netClient.connect(port, host)
                .onSuccess(this::onConnectSuccess)
                .onFailure(err -> {
                    log.warn("TCP connection failed to {}:{} — {}", host, port, err.getMessage());
                    scheduleReconnect();
                })
                .mapEmpty();
    }

    private void onConnectSuccess(NetSocket socket) {
        this.currentSocket = socket;
        this.connected = true;
        this.currentAttemptCount.set(0);

        socket.exceptionHandler(err -> {
            log.warn("TCP socket error on {}:{}", host, port, err);
            handleDisconnect();
        });
        socket.closeHandler(v -> {
            if (connected) {
                log.warn("TCP socket closed unexpectedly on {}:{}", host, port);
                handleDisconnect();
            }
        });

        log.info("TCP connection established to {}:{}", host, port);
        reconnectHandlers.forEach(h -> h.handle(null));
    }

    private void handleDisconnect() {
        connected = false;
        currentSocket = null;
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (closed) {
            return;
        }
        int count = currentAttemptCount.getAndIncrement();
        long delay;

        if (reconnectAttempts < 0 || count < reconnectAttempts) {
            delay = reconnectInterval;
            log.debug("Scheduling TCP reconnect in {}ms (attempt {}/{})", delay, count + 1,
                    reconnectAttempts < 0 ? "∞" : reconnectAttempts);
        } else {
            // Exhausted reconnect attempts — wait retryTimeout and reset
            currentAttemptCount.set(0);
            delay = retryTimeout;
            log.warn("Exhausted {} reconnect attempts to {}:{}. Waiting {}ms before retrying.",
                    reconnectAttempts, host, port, delay);
        }

        vertx.setTimer(delay, id -> doConnect());
    }

    // -------------------------------------------------------------------------
    // WriteStream<Buffer> implementation
    // -------------------------------------------------------------------------

    @Override
    public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    @Override
    public Future<Void> write(Buffer data) {
        NetSocket socket = currentSocket;
        if (connected && socket != null) {
            return socket.write(data);
        }
        return Future.failedFuture("TCP not connected to " + host + ":" + port);
    }

    @Override
    public Future<Void> end() {
        NetSocket socket = currentSocket;
        if (socket != null) {
            return socket.end();
        }
        return Future.succeededFuture();
    }

    @Override
    public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
        NetSocket socket = currentSocket;
        if (socket != null) {
            socket.setWriteQueueMaxSize(maxSize);
        }
        return this;
    }

    @Override
    public boolean writeQueueFull() {
        NetSocket socket = currentSocket;
        return socket == null || !connected || socket.writeQueueFull();
    }

    @Override
    public WriteStream<Buffer> drainHandler(Handler<Void> handler) {
        NetSocket socket = currentSocket;
        if (socket != null) {
            socket.drainHandler(handler);
        }
        return this;
    }

    // -------------------------------------------------------------------------
    // Public management API
    // -------------------------------------------------------------------------

    /** @return {@code true} if a live socket is available. */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Registers a handler called every time the connection is (re-)established.
     * Multiple handlers are supported; all are invoked on each reconnect.
     */
    public TcpWriteStream addReconnectHandler(Handler<Void> handler) {
        reconnectHandlers.add(handler);
        return this;
    }

    /** Removes a previously registered reconnect handler. */
    public TcpWriteStream removeReconnectHandler(Handler<Void> handler) {
        reconnectHandlers.remove(handler);
        return this;
    }

    /**
     * Permanently closes this stream.
     * After this call no reconnection will be attempted and all write operations will fail.
     */
    public void close() {
        this.closed = true;
        this.connected = false;
        NetSocket socket = currentSocket;
        currentSocket = null;
        if (socket != null) {
            socket.close();
        }
        if (netClient != null) {
            netClient.close();
        }
    }

    /** Returns the remote host this stream connects to. */
    public String getHost() {
        return host;
    }

    /** Returns the remote port this stream connects to. */
    public int getPort() {
        return port;
    }
}
