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
package io.gravitee.am.tooling.http;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static io.vertx.core.http.HttpMethod.POST;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 */
public class HttpProviderApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProviderApi.class);

    public static final String CONF_HOST = "host";
    public static final String CONF_PORT = "port";
    public static final String CONF_TRUST_STORE_PATH = "trustStorePath";
    public static final String CONF_TRUST_STORE_TYPE = "trustStoreType";
    public static final String CONF_TRUST_STORE_PASSWORD = "trustStorePassword";
    public static final String CONF_KEY_STORE_PATH = "keyStorePath";
    public static final String CONF_KEY_STORE_TYPE = "keyStoreType";
    public static final String CONF_KEY_STORE_PASSWORD = "keyStorePassword";
    public static final String CONF_CERT_HEADER = "certificateHeader";

    public static void main(String[] args) throws Exception {
        HttpServerOptions options = buildHttpOptions();

        Vertx vertx = Vertx.vertx();
        HttpServer server = vertx.createHttpServer(options);

        Router router = Router.router(vertx);
        router.route()
                .handler(StaticHandler.create());

        router.route("/login")
                .method(POST)
                .consumes("application/json")
                .produces("application/json")
                .handler(BodyHandler.create())
                .handler(new LoginHandler(true));

        router.route("/username")
                .method(POST)
                .consumes("application/json")
                .produces("application/json")
                .handler(BodyHandler.create())
                .handler(new LoginHandler(false));

        server.requestHandler(router).listen();
        LOGGER.info("Server listening on port {}", options.getPort());
    }

    private static HttpServerOptions buildHttpOptions() {
        Integer httpProviderPort = Optional.ofNullable(System.getenv("HTTP_PROVIDER_PORT")).map(Integer::parseInt).orElse(8080);
        HttpServerOptions options = new HttpServerOptions();
        options.setPort(httpProviderPort);
        options.setHost("0.0.0.0");
        options.setUseAlpn(false);
        return options;
    }
}
