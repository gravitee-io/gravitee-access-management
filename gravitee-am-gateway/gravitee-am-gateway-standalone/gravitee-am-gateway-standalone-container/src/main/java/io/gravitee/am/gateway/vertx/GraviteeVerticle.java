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
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.reactivex.core.http.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GraviteeVerticle extends AbstractVerticle {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(GraviteeVerticle.class);

    @Autowired
    @Qualifier("gatewayHttpServer")
    private HttpServer httpServer;

    @Autowired
    private Reactor reactor;

    @Autowired
    private VertxHttpServerConfiguration httpServerConfiguration;

    @Override
    public void start(Promise<Void> startPromise) {
        httpServer.requestHandler(reactor.route());

        httpServer.listen(res -> {
            if (res.succeeded()) {
                logger.info("HTTP Server is now listening for requests on port {}",
                        httpServerConfiguration.getPort());
                startPromise.complete();
            } else {
                logger.error("Unable to start HTTP Server", res.cause());
                startPromise.fail(res.cause());
            }
        });
    }

    @Override
    public void stop() throws Exception {
        logger.info("Stopping HTTP Server...");
        httpServer.close(voidAsyncResult -> logger.info("HTTP Server has been correctly stopped"));
    }
}
