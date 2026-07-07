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
package io.gravitee.am.reporter.mongodb.audit;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import io.gravitee.am.reporter.mongodb.MongoReporterConfiguration;
import io.gravitee.am.reporter.mongodb.tool.TestMongoConnectionProvider;
import io.gravitee.am.repository.mongodb.provider.impl.MongoClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.Collections;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class MongoReporterJUnitConfiguration {

    public static final String DATABASE = "gravitee-am-reporter-test";
    public static final String COLLECTION = "reporter_audits";
    private static final String DEFAULT_MONGO_IMAGE = "mongo:8.0";

    @Bean(destroyMethod = "stop")
    public MongoDBContainer mongoDBContainer() {
        // use sys.out to display in the junit logs which image is used
        System.out.println("Run Tests with " + DEFAULT_MONGO_IMAGE + " database container");
        MongoDBContainer container = new MongoDBContainer(DEFAULT_MONGO_IMAGE);
        container.withEnv("MONGO_INITDB_DATABASE", DATABASE);
        container.start();
        return container;
    }

    @Bean(destroyMethod = "close")
    public MongoClient mongoClient(MongoDBContainer container) {
        ClusterSettings clusterSettings = ClusterSettings.builder()
                .hosts(Collections.singletonList(new ServerAddress(container.getHost(), container.getFirstMappedPort())))
                .build();
        CodecRegistry pojoCodecRegistry = fromRegistries(
                MongoClients.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyToClusterSettings(builder -> builder.applySettings(clusterSettings))
                .codecRegistry(pojoCodecRegistry)
                .writeConcern(WriteConcern.ACKNOWLEDGED)
                .build();
        return MongoClients.create(settings);
    }

    @Bean
    public MongoReporterConfiguration reporterConfiguration() {
        MongoReporterConfiguration configuration = new MongoReporterConfiguration();
        configuration.setDatabase(DATABASE);
        configuration.setReportableCollection(COLLECTION);
        configuration.setBulkActions(1000);
        configuration.setFlushInterval(1L);
        return configuration;
    }

    @Bean
    public ConnectionProvider connectionProvider(MongoClient mongoClient) {
        return new TestMongoConnectionProvider(new MongoClientWrapper(mongoClient, DATABASE));
    }
}
