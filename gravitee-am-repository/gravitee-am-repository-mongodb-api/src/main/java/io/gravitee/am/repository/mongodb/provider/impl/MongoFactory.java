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
package io.gravitee.am.repository.mongodb.provider.impl;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.ServerSettings;
import com.mongodb.connection.SocketSettings;
import com.mongodb.connection.SslSettings;
import com.mongodb.connection.TransportSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.repository.mongodb.provider.MongoConnectionConfiguration;
import io.gravitee.am.repository.mongodb.provider.metrics.MongoMetricsConnectionPoolListener;
import io.gravitee.node.monitoring.metrics.Metrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.List.of;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Component("mongoFactory")
public class MongoFactory implements FactoryBean<MongoClient> {
    private static final String PASSWORD = "password";
    private static final String SERVERS = "servers[";
    private static final String DEFAULT_TLS_PROTOCOL = "TLSv1.2";
    public static final NioEventLoopGroup COMMON_EVENT_LOOP_GROUP = new NioEventLoopGroup();

    @Autowired
    private RepositoriesEnvironment environment;

    @Setter
    private String propertyPrefix;

    public static MongoClient createClient(MongoConnectionConfiguration configuration) {
        MongoClient mongoClient;

        final MeterRegistry amRegistry = Metrics.getDefaultRegistry();
        final MongoMetricsConnectionPoolListener connectionPoolListener = new MongoMetricsConnectionPoolListener(amRegistry, "idp-mongo");

        if ((configuration.getUri() != null) && (!configuration.getUri().isEmpty())) {
            MongoClientSettings.Builder builder = MongoClientSettings.builder();
            MongoClientSettings settings = builder
                    .applyToConnectionPoolSettings(builder1 -> builder1.addConnectionPoolListener(connectionPoolListener))
                    .applyConnectionString(new ConnectionString(configuration.getUri()))
                    .transportSettings(TransportSettings.nettyBuilder().eventLoopGroup(COMMON_EVENT_LOOP_GROUP).build())
                    .build();
            mongoClient = MongoClients.create(settings);
        } else {
            ServerAddress serverAddress = new ServerAddress(configuration.getHost(), configuration.getPort());
            ConnectionPoolSettings.Builder connectionPoolBuilder = ConnectionPoolSettings.builder().addConnectionPoolListener(connectionPoolListener);
            ClusterSettings clusterSettings = ClusterSettings.builder().hosts(of(serverAddress)).build();
            MongoClientSettings.Builder settings = MongoClientSettings.builder()
                    .applyToConnectionPoolSettings(builder1 -> builder1.applySettings(connectionPoolBuilder.build()))
                    .applyToClusterSettings(clusterBuilder -> clusterBuilder.applySettings(clusterSettings))
                    .transportSettings(TransportSettings.nettyBuilder().eventLoopGroup(COMMON_EVENT_LOOP_GROUP).build());
            if (configuration.isEnableCredentials()) {
                MongoCredential credential = MongoCredential.createCredential(configuration
                        .getUsernameCredentials(), configuration
                        .getDatabaseCredentials(), configuration
                        .getPasswordCredentials().toCharArray());
                settings.credential(credential);
            }
            mongoClient = MongoClients.create(settings.build());
        }
        return mongoClient;
    }

    @Override
    public MongoClient getObject() {
        if (propertyPrefix == null) {
            throw new IllegalStateException("Property prefix is not initialized");
        }

        // Client settings
        MongoClientSettings.Builder builder = MongoClientSettings.builder();
        builder.writeConcern(WriteConcern.ACKNOWLEDGED);

        CodecRegistry defaultCodecRegistry = MongoClients.getDefaultCodecRegistry();
        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder()
                .automatic(true)
                .build());

        builder.codecRegistry(fromRegistries(defaultCodecRegistry, pojoCodecRegistry));

        final MeterRegistry amRegistry = Metrics.getDefaultRegistry();
        final MongoMetricsConnectionPoolListener connectionPoolListener = new MongoMetricsConnectionPoolListener(amRegistry, "common-pool");

        final SslSettings sslSettings = buildSslSettings();
        final ServerSettings serverSettings = buildServerSettings();

        // Trying to get the MongoClientURI if uri property is defined
        String uri = readPropertyValue(propertyPrefix + "uri");
        if (uri != null && !uri.isEmpty()) {
            // The builder can be configured with default options, which may be overridden by options specified in
            // the URI string.
            MongoClientSettings settings = builder
                    .applyToConnectionPoolSettings(builder1 -> builder1.addConnectionPoolListener(connectionPoolListener))
                    .applyToServerSettings(builder1 -> builder1.applySettings(serverSettings))
                    .applyToSslSettings(builder1 -> builder1.applySettings(sslSettings))
                    .applyConnectionString(new ConnectionString(uri))
                    .transportSettings(TransportSettings.nettyBuilder().eventLoopGroup(COMMON_EVENT_LOOP_GROUP).build())
                    .build();

            return MongoClients.create(settings);
        } else {
            // Advanced configuration
            SocketSettings.Builder socketBuilder = SocketSettings.builder();
            ClusterSettings.Builder clusterBuilder = ClusterSettings.builder();

            ConnectionPoolSettings.Builder connectionPoolBuilder = ConnectionPoolSettings.builder().addConnectionPoolListener(connectionPoolListener);


            Integer connectTimeout = readPropertyValue(propertyPrefix + "connectTimeout", Integer.class, 1000);
            Integer maxWaitTime = readPropertyValue(propertyPrefix + "maxWaitTime", Integer.class);
            Integer socketTimeout = readPropertyValue(propertyPrefix + "socketTimeout", Integer.class, 1000);
            Integer maxConnectionLifeTime = readPropertyValue(propertyPrefix + "maxConnectionLifeTime", Integer.class);
            Integer maxConnectionIdleTime = readPropertyValue(propertyPrefix + "maxConnectionIdleTime", Integer.class);
            Integer maxSize = readPropertyValue(propertyPrefix + "maxSize", Integer.class);
            Integer minSize = readPropertyValue(propertyPrefix + "minSize", Integer.class);

            // We do not want to wait for a server
            Integer serverSelectionTimeout = readPropertyValue(propertyPrefix + "serverSelectionTimeout", Integer.class, 1000);
            String description = readPropertyValue(propertyPrefix + "description", String.class, "gravitee.io");
            if (maxSize != null) {
                connectionPoolBuilder.maxSize(maxSize);
            }
            if (minSize != null) {
                connectionPoolBuilder.minSize(minSize);
            }
            if (maxWaitTime != null)
                connectionPoolBuilder.maxWaitTime(maxWaitTime, TimeUnit.MILLISECONDS);
            if (connectTimeout != null)
                socketBuilder.connectTimeout(connectTimeout, TimeUnit.MILLISECONDS);
            if (socketTimeout != null)
                socketBuilder.readTimeout(socketTimeout, TimeUnit.MILLISECONDS);
            if (maxConnectionLifeTime != null)
                connectionPoolBuilder.maxConnectionLifeTime(maxConnectionLifeTime, TimeUnit.MILLISECONDS);
            if (maxConnectionIdleTime != null)
                connectionPoolBuilder.maxConnectionIdleTime(maxConnectionIdleTime, TimeUnit.MILLISECONDS);
            if (description != null) {
                builder.applicationName(description);
            }
            if (serverSelectionTimeout != null)
                clusterBuilder.serverSelectionTimeout(serverSelectionTimeout, TimeUnit.MILLISECONDS);

            // credentials option
            String username = readPropertyValue(propertyPrefix + "username");
            String password = readPropertyValue(propertyPrefix + PASSWORD);
            MongoCredential credentials = null;
            if (username != null || password != null) {
                String authSource = readPropertyValue(propertyPrefix + "authSource", String.class, "gravitee-am");
                credentials = MongoCredential.createCredential(username, authSource, password.toCharArray());
                builder.credential(credentials);
            }

            Boolean retryWritesPreference = readPropertyValue(propertyPrefix + "retryWrites", Boolean.class, true);
            builder.retryWrites(retryWritesPreference);

            // clustering option
            List<ServerAddress> seeds;
            int serversCount = getServersCount();
            if (serversCount == 0) {
                String host = readPropertyValue(propertyPrefix + "host", String.class, "localhost");
                int port = readPropertyValue(propertyPrefix + "port", int.class, 27017);
                seeds = Collections.singletonList(new ServerAddress(host, port));
            } else {
                seeds = new ArrayList<>(serversCount);
                for (int i = 0; i < serversCount; i++) {
                    seeds.add(buildServerAddress(i));
                }
            }
            clusterBuilder.hosts(seeds);

            SocketSettings socketSettings = socketBuilder.build();
            ClusterSettings clusterSettings = clusterBuilder.build();
            ConnectionPoolSettings connectionPoolSettings = connectionPoolBuilder.build();

            MongoClientSettings settings = builder
                    .applyToClusterSettings(builder1 -> builder1.applySettings(clusterSettings))
                    .applyToSocketSettings(builder1 -> builder1.applySettings(socketSettings))
                    .applyToConnectionPoolSettings(builder1 -> builder1.applySettings(connectionPoolSettings))
                    .applyToServerSettings(builder1 -> builder1.applySettings(serverSettings))
                    .applyToSslSettings(builder1 -> builder1.applySettings(sslSettings))
                    .transportSettings(TransportSettings.nettyBuilder().eventLoopGroup(COMMON_EVENT_LOOP_GROUP).build())
                    .build();

            return MongoClients.create(settings);
        }
    }

    private SslSettings buildSslSettings() {
        final SslSettings.Builder sslBuilder = SslSettings.builder();
        final boolean sslEnabled = readPropertyValue(propertyPrefix + "sslEnabled", Boolean.class, false);
        sslBuilder.enabled(sslEnabled);
        if (sslEnabled) {
            try {
                String tlsProtocol = readPropertyValue(propertyPrefix + "tlsProtocol", String.class, DEFAULT_TLS_PROTOCOL);
                SSLContext sslContext = SSLContext.getInstance(tlsProtocol);
                sslContext.init(getKeyManagers(), getTrustManagers(), null);
                sslBuilder.context(sslContext);

                Boolean sslInvalidHostNameAllowed = readPropertyValue(propertyPrefix + "sslInvalidHostNameAllowed", Boolean.class, false);
                sslBuilder.invalidHostNameAllowed(sslInvalidHostNameAllowed);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new IllegalStateException("Error creating the SSLContext for mongodb", e);
            }
        }
        return sslBuilder.build();
    }

    private ServerSettings buildServerSettings() {
        ServerSettings.Builder serverBuilder = ServerSettings.builder();
        Integer minHeartbeatFrequency = readPropertyValue(propertyPrefix + "minHeartbeatFrequency", Integer.class);
        Integer heartbeatFrequency = readPropertyValue(propertyPrefix + "heartbeatFrequency", Integer.class);
        if (minHeartbeatFrequency != null)
            serverBuilder.minHeartbeatFrequency(minHeartbeatFrequency, TimeUnit.MILLISECONDS);
        if (heartbeatFrequency != null) {
            serverBuilder.heartbeatFrequency(heartbeatFrequency, TimeUnit.MILLISECONDS);
        }
        return serverBuilder.build();
    }

    private int getServersCount() {
        log.debug("Looking for MongoDB server configuration...");

        boolean found = true;
        int idx = 0;

        while (found) {
            String serverHost = environment.getProperty(propertyPrefix + SERVERS + (idx++) + "].host");
            found = (serverHost != null);
        }

        return --idx;
    }

    private ServerAddress buildServerAddress(int idx) {
        String host = environment.getProperty(propertyPrefix + SERVERS + idx + "].host");
        int port = readPropertyValue(propertyPrefix + SERVERS + idx + "].port", int.class, 27017);

        return new ServerAddress(host, port);
    }

    private String readPropertyValue(String propertyName) {
        return readPropertyValue(propertyName, String.class, null);
    }

    private <T> T readPropertyValue(String propertyName, Class<T> propertyType) {
        return readPropertyValue(propertyName, propertyType, null);
    }

    private <T> T readPropertyValue(String propertyName, Class<T> propertyType, T defaultValue) {
        T value = environment.getProperty(propertyName, propertyType, defaultValue);
        log.debug("Read property {}: {}", propertyName, value);
        return value;
    }

    @Override
    public Class<?> getObjectType() {
        return MongoClient.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    private KeyManager[] getKeyManagers() {
        String keystorePropertyPrefix = propertyPrefix + "keystore.";
        // TODO: Old properties are kept for backwards compatibility, new ones were added in 3.18.1.
        // So remove `keystore`, `keystorePassword` and `keyPassword` properties in 3.19.0+
        String keystore = readPropertyValue(
                keystorePropertyPrefix + "path",
                String.class,
                readPropertyValue(propertyPrefix + "keystore", String.class)
        );
        String keystorePassword = readPropertyValue(
                keystorePropertyPrefix + PASSWORD,
                String.class,
                readPropertyValue(propertyPrefix + "keystorePassword", String.class, "")
        );
        String keyPassword = readPropertyValue(
                keystorePropertyPrefix + "keyPassword",
                String.class,
                readPropertyValue(propertyPrefix + "keyPassword", String.class, "")
        );
        String keystoreType = readPropertyValue(keystorePropertyPrefix + "type", String.class);

        if (keystore == null) {
            return null;
        }

        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = KeyStore.getInstance(keystoreType != null ? keystoreType : KeyStore.getDefaultType());
            keyStore.load(new FileInputStream(keystore), keystorePassword.toCharArray());
            keyManagerFactory.init(keyStore, keyPassword.toCharArray());
            return keyManagerFactory.getKeyManagers();
        } catch (Exception e) {
            throw new IllegalStateException("Error creating the keystore for mongodb", e);
        }
    }

    private TrustManager[] getTrustManagers() {
        String truststorePropertyPrefix = propertyPrefix + "truststore.";
        String truststorePath = readPropertyValue(truststorePropertyPrefix + "path", String.class);
        String truststoreType = readPropertyValue(truststorePropertyPrefix + "type", String.class);
        String truststorePassword = readPropertyValue(truststorePropertyPrefix + PASSWORD, String.class, "");

        if (truststorePath == null) {
            return null;
        }

        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = KeyStore.getInstance(truststoreType != null ? truststoreType : KeyStore.getDefaultType());
            keyStore.load(new FileInputStream(truststorePath), truststorePassword.toCharArray());
            trustManagerFactory.init(keyStore);
            return trustManagerFactory.getTrustManagers();
        } catch (Exception e) {
            throw new IllegalStateException("Error creating the truststore for mongodb", e);
        }
    }
}
