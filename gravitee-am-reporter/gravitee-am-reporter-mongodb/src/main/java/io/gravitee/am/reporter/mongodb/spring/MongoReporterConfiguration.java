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
package io.gravitee.am.reporter.mongodb.spring;

import com.mongodb.ConnectionString;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static java.util.Arrays.asList;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class MongoReporterConfiguration {

    @Autowired
    private io.gravitee.am.reporter.mongodb.MongoReporterConfiguration configuration;

    @Bean
    public MongoClient mongoClient() {
        // Client settings
        com.mongodb.MongoClientSettings.Builder builder = com.mongodb.MongoClientSettings.builder();
        builder.writeConcern(WriteConcern.ACKNOWLEDGED);

        // codec configuration for pojo mapping
        CodecRegistry pojoCodecRegistry = fromRegistries(MongoClients.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        builder.codecRegistry(pojoCodecRegistry);

        if ((this.configuration.getUri() != null) && (!this.configuration.getUri().isEmpty())) {
            // The builder can be configured with default options, which may be overridden by options specified in
            // the URI string.
            com.mongodb.MongoClientSettings settings = builder
                    .codecRegistry(pojoCodecRegistry)
                    .applyConnectionString(new ConnectionString(this.configuration.getUri()))
                    .build();

            return MongoClients.create(settings);
        } else {
            // Manual configuration
            // Servers host
            ServerAddress serverAddress = new ServerAddress(this.configuration.getHost(), this.configuration.getPort());
            ClusterSettings clusterSettings = ClusterSettings.builder().hosts(asList(serverAddress)).build();

            // Mongo credentials
            if (this.configuration.isEnableCredentials()) {
                MongoCredential credential = MongoCredential.createCredential(this.configuration
                        .getUsernameCredentials(), this.configuration
                        .getDatabaseCredentials(), this.configuration
                        .getPasswordCredentials().toCharArray());
                builder.credential(credential);
            }

            com.mongodb.MongoClientSettings settings = builder
                    .applyToClusterSettings(builder1 -> builder1.applySettings(clusterSettings))
                    .build();
            return MongoClients.create(settings);
        }
    }
}
