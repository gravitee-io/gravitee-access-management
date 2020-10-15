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

import io.gravitee.node.api.healthcheck.Probe;
import io.gravitee.node.api.healthcheck.Result;
import io.vertx.core.net.NetClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.net.NetClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.CompletableFuture;

/**
 * HTTP Probe used to check the gateway itself.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HttpServerProbe implements Probe {

    @Value("${http.port:8092}")
    private int port;

    @Value("${http.host:localhost}")
    private String host;

    @Autowired
    private Vertx vertx;

    @Override
    public String id() {
        return "http-server";
    }

    @Override
    public CompletableFuture<Result> check() {
        final CompletableFuture<Result> future = new CompletableFuture<>();

        NetClientOptions options = new NetClientOptions().setConnectTimeout(500);
        NetClient client = vertx.createNetClient(options);

        client.rxConnect(port, host)
                .doFinally(client::close)
                .subscribe(
                        socket -> future.complete(Result.healthy()),
                        error -> future.complete(Result.unhealthy(error.getCause())));
        return future;
    }
}
