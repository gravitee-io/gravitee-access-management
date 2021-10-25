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
package io.gravitee.sample.fapi.api;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import org.apache.commons.cli.*;

import java.util.Set;

import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 */
public class FapiApi {

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
        CommandLine cmd = parseArgs(args);

        CertUtils.certHeader = cmd.getOptionValue(CONF_CERT_HEADER);
        if (CertUtils.certHeader != null) {
            System.out.println("PeerCertificate read from '" + CertUtils.certHeader + "' header");
        }

        HttpServerOptions options = buildHttpOptions(cmd);

        Vertx vertx = Vertx.vertx();
        HttpServer server = vertx.createHttpServer(options);

        Router router = Router.router(vertx);
        router.route().handler(StaticHandler.create());

        router.route("/fapi/api")
                .method(GET)
                .produces("application/json")
                .handler(new FapiResourceApiHandler());

        router.route("/fapi/api/consent")
                .method(POST)
                .produces("application/json")
                .handler(new FapiConsentResourceApiHandler());

        server.requestHandler(router)

                .listen();
        System.out.println("Server listening on port " + cmd.getOptionValue(CONF_PORT, "9443"));
    }

    private static HttpServerOptions buildHttpOptions(CommandLine cmd) {
        HttpServerOptions options = new HttpServerOptions();
        options.setPort(Integer.parseInt(cmd.getOptionValue(CONF_PORT, "9443")));
        options.setHost(cmd.getOptionValue(CONF_HOST, "0.0.0.0"));
        options.setSsl(CertUtils.certHeader == null); // ssl must be enable if Herder is missing
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

    private static CommandLine parseArgs(String[] args) throws ParseException {
        Options options = new Options();
        Option host = new Option(CONF_HOST, true, "binding interface");
        host.setRequired(false);
        options.addOption(host);

        Option port = new Option(CONF_PORT, true, "listening port");
        port.setRequired(false);
        options.addOption(port);

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

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse( options, args);

        if (!cmd.hasOption(CONF_CERT_HEADER) && !(cmd.hasOption(CONF_KEY_STORE_PATH) && cmd.hasOption(CONF_KEY_STORE_PATH))) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "java io.gravitee.sample.fapi.api.FapiApi ", options );
            System.exit(1);
        }

        return cmd;
    }
}
