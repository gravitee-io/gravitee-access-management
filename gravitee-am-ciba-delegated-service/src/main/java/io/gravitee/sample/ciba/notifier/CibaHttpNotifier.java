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
package io.gravitee.sample.ciba.notifier;

import io.gravitee.sample.ciba.notifier.http.CibaNotifierApiHandler;
import io.gravitee.sample.ciba.notifier.http.CibaNotifierWebSockerHandler;
import io.gravitee.sample.ciba.notifier.http.domain.CibaDomainManager;
import io.gravitee.sample.ciba.notifier.http.domain.CibaDomainManagerHandler;
import io.gravitee.sample.ciba.notifier.http.mock.CibaAutomatedTestActionApiHandler;
import io.gravitee.sample.ciba.notifier.http.mock.CibaMockNotifierApiHandler;
import io.vertx.core.Vertx;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static io.vertx.core.http.HttpMethod.POST;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CibaHttpNotifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(CibaHttpNotifier.class);

    public static final String CONF_HOST = "host";
    public static final String CONF_PORT = "port";
    public static final String CONF_SECURE = "secure";
    public static final String CONF_BEARER = "bearer";
    public static final String CONF_TRUST_STORE_PATH = "trustStorePath";
    public static final String CONF_TRUST_STORE_TYPE = "trustStoreType";
    public static final String CONF_TRUST_STORE_PASSWORD = "trustStorePassword";
    public static final String CONF_KEY_STORE_PATH = "keyStorePath";
    public static final String CONF_KEY_STORE_TYPE = "keyStoreType";
    public static final String CONF_KEY_STORE_PASSWORD = "keyStorePassword";
    public static final String CONF_CLIENT_KEY_STORE_PATH = "clientKeyStorePath";
    public static final String CONF_CLIENT_KEY_STORE_TYPE = "clientKeyStoreType";
    public static final String CONF_CLIENT_KEY_STORE_PASSWORD = "clientKeyStorePassword";
    public static final String CONF_CLIENT_TRUST_STORE_PATH = "clientTrustStorePath";
    public static final String CONF_CLIENT_TRUST_STORE_TYPE = "clientTrustStoreType";
    public static final String CONF_CLIENT_TRUST_STORE_PASSWORD = "clientTrustStorePassword";
    public static final String CONF_CERT_HEADER = "certificateHeader";
    public static final String PATH_CIBA_NOTIFY = "/ciba/notify";
    public static final String PATH_CIBA_DOMAINS = "/ciba/domains";
    public static final String DEFAULT_LISTENING_PORT = "8080";

    public static void main(String[] args) throws Exception {
        CommandLine cmd = parseArgs(args);

        HttpServerOptions options = buildHttpOptions(cmd);
        WebClientOptions clientOptions = buildClientHttpOptions(cmd);

        Vertx vertx = Vertx.vertx();
        HttpServer server = vertx.createHttpServer(options);

        Router router = Router.router(vertx);
        router.route().handler(StaticHandler.create());

        CibaDomainManager domainManager = new CibaDomainManager();

        router.route(PATH_CIBA_NOTIFY + "/actionize")
                .method(POST)
                .produces("application/json")
                .handler(BodyHandler.create())
                .handler(new CibaAutomatedTestActionApiHandler(domainManager, vertx, clientOptions));

        router.route(PATH_CIBA_NOTIFY + "/accept-all")
                .method(POST)
                .consumes("application/x-www-form-urlencoded")
                .produces("application/json")
                .handler(BodyHandler.create())
                .handler(new CibaMockNotifierApiHandler(true, cmd, domainManager, vertx, clientOptions));

        router.route(PATH_CIBA_NOTIFY + "/reject-all")
                .method(POST)
                .consumes("application/x-www-form-urlencoded")
                .produces("application/json")
                .handler(BodyHandler.create())
                .handler(new CibaMockNotifierApiHandler(false, cmd, domainManager, vertx, clientOptions));

        router.route(PATH_CIBA_DOMAINS)
                .method(POST)
                .produces("application/json")
                .consumes("application/json")
                .handler(new CibaDomainManagerHandler(domainManager));

        router.route(PATH_CIBA_NOTIFY)
                .method(POST)
                .consumes("application/x-www-form-urlencoded")
                .produces("application/json")
                .handler(BodyHandler.create())
                .handler(new CibaNotifierApiHandler(cmd, vertx));

        server.webSocketHandler(new CibaNotifierWebSockerHandler(vertx, domainManager, clientOptions));

        server.requestHandler(router)
                .listen();

        LOGGER.info("Server listening on port {}", cmd.getOptionValue(CONF_PORT, DEFAULT_LISTENING_PORT));
    }

    private static HttpServerOptions buildHttpOptions(CommandLine cmd) {
        HttpServerOptions options = new HttpServerOptions();
        options.setPort(Integer.parseInt(cmd.getOptionValue(CONF_PORT, DEFAULT_LISTENING_PORT)));
        options.setHost(cmd.getOptionValue(CONF_HOST, "0.0.0.0"));
        options.setSsl(Boolean.parseBoolean(cmd.getOptionValue(CONF_SECURE, "false")));
        options.setUseAlpn(false);

        if (options.isSsl()) {
            options.setEnabledSecureTransportProtocols(Set.of("TLSv1.2", "TLSv1.3"));
            options.addEnabledCipherSuite("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256")
                    .addEnabledCipherSuite("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
                    .addEnabledCipherSuite("TLS_DHE_RSA_WITH_AES_256_GCM_SHA384")
                    .addEnabledCipherSuite("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384");
            options.setClientAuth(ClientAuth.REQUEST);

            if (cmd.getOptionValue(CONF_TRUST_STORE_TYPE, "pkcs12").equalsIgnoreCase("pkcs12")) {
                options.setPfxTrustOptions(new PfxOptions()
                        .setPath(cmd.getOptionValue(CONF_TRUST_STORE_PATH))
                        .setPassword(cmd.getOptionValue(CONF_TRUST_STORE_PASSWORD)));
            } else {
                options.setTrustStoreOptions(new JksOptions()
                        .setPath(cmd.getOptionValue(CONF_TRUST_STORE_PATH))
                        .setPassword(cmd.getOptionValue(CONF_TRUST_STORE_PASSWORD)));
            }

            if (cmd.getOptionValue(CONF_KEY_STORE_TYPE, "pkcs12").equalsIgnoreCase("pkcs12")) {
                options.setPfxKeyCertOptions(new PfxOptions()
                        .setPath(cmd.getOptionValue(CONF_KEY_STORE_PATH))
                        .setPassword(cmd.getOptionValue(CONF_KEY_STORE_PASSWORD)));
            } else {
                options.setKeyStoreOptions(new JksOptions()
                        .setPath(cmd.getOptionValue(CONF_KEY_STORE_PATH))
                        .setPassword(cmd.getOptionValue(CONF_KEY_STORE_PASSWORD)));
            }
        }

        return options;
    }

    private static WebClientOptions buildClientHttpOptions(CommandLine cmd) {
        WebClientOptions options = new WebClientOptions().setUserAgent("AM CIBA Delegate HTTP Service / Conformance Automated Test");
        options.setKeepAlive(false);
        options.setSsl(cmd.getOptionValue(CONF_CLIENT_KEY_STORE_TYPE) != null);

        if (options.isSsl()) {
            if (cmd.getOptionValue(CONF_CLIENT_KEY_STORE_TYPE).equalsIgnoreCase("pkcs12")) {
                options.setPfxKeyCertOptions(new PfxOptions()
                        .setPath(cmd.getOptionValue(CONF_CLIENT_KEY_STORE_PATH))
                        .setPassword(cmd.getOptionValue(CONF_CLIENT_KEY_STORE_PASSWORD)));
            } else {
                options.setKeyStoreOptions(new JksOptions()
                        .setPath(cmd.getOptionValue(CONF_CLIENT_KEY_STORE_PATH))
                        .setPassword(cmd.getOptionValue(CONF_CLIENT_KEY_STORE_PASSWORD)));
            }

            options.setTrustAll(cmd.getOptionValue(CONF_CLIENT_TRUST_STORE_TYPE) == null);

            if (cmd.getOptionValue(CONF_CLIENT_TRUST_STORE_TYPE) != null) {
                if (cmd.getOptionValue(CONF_CLIENT_TRUST_STORE_TYPE).equalsIgnoreCase("pkcs12")) {
                    options.setPfxKeyCertOptions(new PfxOptions()
                            .setPath(cmd.getOptionValue(CONF_CLIENT_TRUST_STORE_PATH))
                            .setPassword(cmd.getOptionValue(CONF_CLIENT_TRUST_STORE_PASSWORD)));
                } else {
                    options.setKeyStoreOptions(new JksOptions()
                            .setPath(cmd.getOptionValue(CONF_CLIENT_TRUST_STORE_PATH))
                            .setPassword(cmd.getOptionValue(CONF_CLIENT_TRUST_STORE_PASSWORD)));
                }
            }
        }

        return options;
    }

    private static CommandLine parseArgs(String[] args) throws ParseException {
        Options options = new Options();
        Option host = new Option(CONF_HOST, true, "binding interface");
        host.setRequired(false);
        options.addOption(host);

        Option port = new Option(CONF_PORT, true, "listening port");
        port.setRequired(false);
        options.addOption(port);

        Option bearer = new Option(CONF_BEARER, true, "Bearer used to authenticate the AM instance");
        bearer.setRequired(false);
        options.addOption(bearer);

        Option secure = new Option(CONF_SECURE, true, "Use secured connection");
        secure.setRequired(false);
        options.addOption(secure);

        Option certHeader = new Option(CONF_CERT_HEADER, true, "Header With Peer Certificate");
        certHeader.setRequired(false);
        options.addOption(certHeader);

        final Option truststore_path = new Option(CONF_TRUST_STORE_PATH, true, "truststore path");
        truststore_path.setRequired(false);
        options.addOption(truststore_path);

        final Option truststore_type = new Option(CONF_TRUST_STORE_TYPE, true, "truststore type");
        truststore_type.setRequired(false);
        options.addOption(truststore_type);

        final Option truststore_password = new Option(CONF_TRUST_STORE_PASSWORD, true, "truststore password");
        truststore_password.setRequired(false);
        options.addOption(truststore_password);

        final Option keystore_path = new Option(CONF_KEY_STORE_PATH, true, "keystore path");
        keystore_path.setRequired(false);
        options.addOption(keystore_path);

        final Option keystore_type = new Option(CONF_KEY_STORE_TYPE, true, "keystore type");
        keystore_type.setRequired(false);
        options.addOption(keystore_type);

        final Option keystore_password = new Option(CONF_KEY_STORE_PASSWORD, true, "keystore password");
        keystore_password.setRequired(false);
        options.addOption(keystore_password);

        final Option client_keystore_path = new Option(CONF_CLIENT_KEY_STORE_PATH, true, "Client keystore path");
        client_keystore_path.setRequired(false);
        options.addOption(client_keystore_path);

        final Option client_keystore_type = new Option(CONF_CLIENT_KEY_STORE_TYPE, true, "Client keystore type");
        client_keystore_type.setRequired(false);
        options.addOption(client_keystore_type);

        final Option client_keystore_password = new Option(CONF_CLIENT_KEY_STORE_PASSWORD, true, "Client keystore password");
        client_keystore_password.setRequired(false);
        options.addOption(client_keystore_password);

        final Option client_truststore_path = new Option(CONF_CLIENT_TRUST_STORE_PATH, true, "Client truststore path");
        client_truststore_path.setRequired(false);
        options.addOption(client_truststore_path);

        final Option client_truststore_type = new Option(CONF_CLIENT_TRUST_STORE_TYPE, true, "Client truststore type");
        client_truststore_type.setRequired(false);
        options.addOption(client_truststore_type);

        final Option client_truststore_password = new Option(CONF_CLIENT_TRUST_STORE_PASSWORD, true, "Client truststore password");
        client_truststore_password.setRequired(false);
        options.addOption(client_truststore_password);

        CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }
}
