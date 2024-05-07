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
package io.gravitee.am.gateway.vertx;

import io.gravitee.am.gateway.reactor.Reactor;
import io.gravitee.node.vertx.server.http.VertxHttpServer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.rxjava3.core.http.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GraviteeVerticle extends AbstractVerticle {

    private final Logger logger = LoggerFactory.getLogger(GraviteeVerticle.class);

    private final VertxHttpServer vertxHttpServer;
    private final Reactor reactor;
    private HttpServer httpServer;

    public GraviteeVerticle(VertxHttpServer vertxHttpServer, Reactor reactor) {
        this.vertxHttpServer = vertxHttpServer;
        this.reactor = reactor;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        httpServer = vertxHttpServer.newInstance();

        httpServer.requestHandler(reactor.route());
        httpServer.rxListen()
                .subscribe(
                        httpServer1 -> {
                            logger.info("HTTP Server is now listening for requests on port {}", vertxHttpServer.options().getPort());
                            startPromise.complete();
                        },
                        throwable -> {
                            logger.error("Unable to start HTTP Server", throwable);
                            startPromise.fail(throwable.getCause());
                        }
                );
    }

    @Override
    public void stop() throws Exception {
        logger.info("Stopping HTTP Server...");
        httpServer.close()
                .doFinally(() -> logger.info("HTTP Server has been correctly stopped"))
                .subscribe();
    }
}
