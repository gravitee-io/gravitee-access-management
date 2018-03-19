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
package io.gravitee.am.repository.mongodb.common;

import com.mongodb.ConnectionString;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.connection.*;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoFactory implements FactoryBean<MongoClient> {

    private final Logger logger = LoggerFactory.getLogger(MongoFactory.class);

    @Autowired
    private Environment environment;

    private final String propertyPrefix;

    public MongoFactory(String propertyPrefix) {
        this.propertyPrefix = propertyPrefix + ".mongodb.";
    }

    @Override
    public MongoClient getObject() throws Exception {
        // Client settings
        MongoClientSettings.Builder builder = MongoClientSettings.builder();
        builder.writeConcern(WriteConcern.ACKNOWLEDGED);

        // codec configuration for pojo mapping
        CodecRegistry pojoCodecRegistry = fromRegistries(MongoClients.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        builder.codecRegistry(pojoCodecRegistry);

        // Trying to get the MongoClientURI if uri property is defined
        String uri = readPropertyValue(propertyPrefix + "uri");
        if (uri != null && ! uri.isEmpty()) {
            // The builder can be configured with default options, which may be overridden by options specified in
            // the URI string.
            ServerSettings serverSettings = ServerSettings.builder().applyConnectionString(new ConnectionString(uri)).build();
            MongoClientSettings settings = builder.serverSettings(serverSettings).build();
            return MongoClients.create(settings);
        } else {
            // Advanced configuration
            SocketSettings.Builder socketBuilder = SocketSettings.builder();
            ClusterSettings.Builder clusterBuilder = ClusterSettings.builder();
            ConnectionPoolSettings.Builder connectionPoolBuilder = ConnectionPoolSettings.builder();
            ServerSettings.Builder serverBuilder = ServerSettings.builder();
            SslSettings.Builder sslBuilder = SslSettings.builder();

            Integer connectionsPerHost = readPropertyValue(propertyPrefix + "connectionsPerHost", Integer.class);
            Integer connectTimeout = readPropertyValue(propertyPrefix + "connectTimeout", Integer.class, 1000);
            Integer maxWaitTime = readPropertyValue(propertyPrefix + "maxWaitTime", Integer.class);
            Integer socketTimeout = readPropertyValue(propertyPrefix + "socketTimeout", Integer.class, 1000);
            Boolean socketKeepAlive = readPropertyValue(propertyPrefix + "socketKeepAlive", Boolean.class, true);
            Integer maxConnectionLifeTime = readPropertyValue(propertyPrefix + "maxConnectionLifeTime", Integer.class);
            Integer maxConnectionIdleTime = readPropertyValue(propertyPrefix + "maxConnectionIdleTime", Integer.class);

            // We do not want to wait for a server
            Integer serverSelectionTimeout = readPropertyValue(propertyPrefix + "serverSelectionTimeout", Integer.class, 1000);
            Integer minHeartbeatFrequency = readPropertyValue(propertyPrefix + "minHeartbeatFrequency", Integer.class);
            String description = readPropertyValue(propertyPrefix + "description", String.class, "gravitee.io");
            Integer heartbeatConnectTimeout = readPropertyValue(propertyPrefix + "heartbeatConnectTimeout", Integer.class, 1000);
            Integer heartbeatFrequency = readPropertyValue(propertyPrefix + "heartbeatFrequency", Integer.class);
            Integer heartbeatSocketTimeout = readPropertyValue(propertyPrefix + "heartbeatSocketTimeout", Integer.class);
            Integer localThreshold = readPropertyValue(propertyPrefix + "localThreshold", Integer.class);
            Integer minConnectionsPerHost = readPropertyValue(propertyPrefix + "minConnectionsPerHost", Integer.class);
            Boolean sslEnabled = readPropertyValue(propertyPrefix + "sslEnabled", Boolean.class);
            Integer threadsAllowedToBlockForConnectionMultiplier = readPropertyValue(propertyPrefix + "threadsAllowedToBlockForConnectionMultiplier", Integer.class);
            Boolean cursorFinalizerEnabled = readPropertyValue(propertyPrefix + "cursorFinalizerEnabled", Boolean.class);

            if (maxWaitTime != null)
                connectionPoolBuilder.maxWaitTime(maxWaitTime, TimeUnit.MILLISECONDS);
            if (connectTimeout != null)
                socketBuilder.connectTimeout(connectTimeout, TimeUnit.MILLISECONDS);
            if (socketTimeout != null)
                socketBuilder.readTimeout(socketTimeout, TimeUnit.MILLISECONDS);
            if (socketKeepAlive != null)
                socketBuilder.keepAlive(socketKeepAlive);
            if (maxConnectionLifeTime != null)
                connectionPoolBuilder.maxConnectionLifeTime(maxConnectionLifeTime, TimeUnit.MILLISECONDS);
            if (maxConnectionIdleTime != null)
                connectionPoolBuilder.maxConnectionIdleTime(maxConnectionIdleTime, TimeUnit.MILLISECONDS);
            if (minHeartbeatFrequency != null)
                serverBuilder.minHeartbeatFrequency(minHeartbeatFrequency, TimeUnit.MILLISECONDS);
            if (description != null)
                clusterBuilder.description(description);
            if (heartbeatFrequency != null)
                serverBuilder.heartbeatFrequency(heartbeatFrequency, TimeUnit.MILLISECONDS);
            if (sslEnabled != null)
                sslBuilder.enabled(sslEnabled);
            if (serverSelectionTimeout != null)
                clusterBuilder.serverSelectionTimeout(serverSelectionTimeout, TimeUnit.MILLISECONDS);

            // credentials option
            String username = readPropertyValue(propertyPrefix + "username");
            String password = readPropertyValue(propertyPrefix + "password");
            List<MongoCredential> credentials = null;
            if (username != null || password != null) {
                String authSource = readPropertyValue(propertyPrefix + "authSource", String.class, "gravitee-am");
                credentials = Collections.singletonList(MongoCredential.createCredential(
                        username, authSource, password.toCharArray()));
            }
            if (credentials != null) {
                builder.credentialList(credentials);
            }

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
            ServerSettings serverSettings = serverBuilder.build();
            SslSettings sslSettings = sslBuilder.build();
            MongoClientSettings settings = builder
                    .socketSettings(socketSettings)
                    .connectionPoolSettings(connectionPoolSettings)
                    .clusterSettings(clusterSettings)
                    .serverSettings(serverSettings)
                    .sslSettings(sslSettings)
                    .build();

            return MongoClients.create(settings);
        }
    }

    private int getServersCount() {
        logger.debug("Looking for MongoDB server configuration...");

        boolean found = true;
        int idx = 0;

        while (found) {
            String serverHost = environment.getProperty(propertyPrefix + "servers[" + (idx++) + "].host");
            found = (serverHost != null);
        }

        return --idx;
    }

    private ServerAddress buildServerAddress(int idx) {
        String host = environment.getProperty(propertyPrefix + "servers[" + idx + "].host");
        int port = readPropertyValue(propertyPrefix + "servers[" + idx + "].port", int.class, 27017);

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
        logger.debug("Read property {}: {}", propertyName, value);
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
}
