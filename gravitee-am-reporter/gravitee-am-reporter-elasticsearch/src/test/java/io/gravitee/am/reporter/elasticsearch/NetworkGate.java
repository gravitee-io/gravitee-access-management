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
package io.gravitee.am.reporter.elasticsearch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A TCP forwarder in front of Elasticsearch that a test can cut and restore, so an outage and its
 * recovery can be exercised without restarting the container (which would move its port).
 *
 * @author GraviteeSource Team
 */
public final class NetworkGate implements AutoCloseable {

    private final String targetHost;
    private final int targetPort;
    private final int listenPort;
    private final ExecutorService connections = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "network-gate");
        thread.setDaemon(true);
        return thread;
    });
    private final List<Socket> live = new CopyOnWriteArrayList<>();

    private volatile ServerSocket listener;
    private volatile boolean open;

    private NetworkGate(String targetHost, int targetPort, ServerSocket listener) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.listener = listener;
        this.listenPort = listener.getLocalPort();
        this.open = true;
        accept(listener);
    }

    public static NetworkGate inFrontOf(String host, int port) throws IOException {
        return new NetworkGate(host, port, new ServerSocket(0));
    }

    public String endpoint() {
        return "http://localhost:" + listenPort;
    }

    /** Drops the listener and every live connection, so Elasticsearch looks unreachable. */
    public synchronized void disconnect() throws IOException {
        if (!open) {
            return;
        }
        open = false;
        listener.close();
        live.forEach(socket -> {
            try {
                socket.close();
            } catch (IOException ignored) {
                // already gone
            }
        });
        live.clear();
    }

    /** Puts the listener back on the same port, so retries start succeeding again. */
    public synchronized void reconnect() throws IOException {
        if (open) {
            return;
        }
        listener = new ServerSocket(listenPort);
        open = true;
        accept(listener);
    }

    @Override
    public void close() throws IOException {
        disconnect();
        connections.shutdownNow();
    }

    private void accept(ServerSocket serverSocket) {
        connections.submit(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket inbound = serverSocket.accept();
                    Socket outbound = new Socket(targetHost, targetPort);
                    live.add(inbound);
                    live.add(outbound);
                    pipe(inbound, outbound);
                    pipe(outbound, inbound);
                } catch (IOException e) {
                    // the listener was closed by disconnect(), or the target refused: stop accepting
                    return;
                }
            }
        });
    }

    private void pipe(Socket from, Socket to) {
        connections.submit(() -> {
            byte[] buffer = new byte[8192];
            try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    out.flush();
                }
            } catch (IOException ignored) {
                // either side closed
            } finally {
                try {
                    to.close();
                } catch (IOException ignored) {
                    // already gone
                }
            }
        });
    }
}
