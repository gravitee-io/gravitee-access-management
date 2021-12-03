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
package io.gravitee.am.repository.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongodbProvider implements InitializingBean, DisposableBean {

    private MongoDBContainer mongoDBContainer = null;

    private String databaseName;
    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;

    public MongodbProvider(String databaseName) {
        this.databaseName = databaseName;
    }

    @Override
    public void afterPropertiesSet() {
        mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"));
        mongoDBContainer.withEnv("MONGO_INITDB_DATABASE", databaseName);
        mongoDBContainer.start();

        // cluster configuration
        ClusterSettings clusterSettings = ClusterSettings.builder().hosts(Collections.singletonList(new ServerAddress(mongoDBContainer.getHost(), mongoDBContainer.getFirstMappedPort()))).build();
        // codec configuration
        CodecRegistry pojoCodecRegistry = fromRegistries(
                MongoClients.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyToClusterSettings(builder1 -> builder1.applySettings(clusterSettings))
                .codecRegistry(pojoCodecRegistry)
                .writeConcern(WriteConcern.ACKNOWLEDGED)
                .build();

        mongoClient = MongoClients.create(settings);
        mongoDatabase = mongoClient.getDatabase(databaseName);
    }

    @Override
    public void destroy() throws Exception {
        if (mongoClient != null) {
            mongoClient.close();
        }

        if (mongoDBContainer != null) {
            mongoDBContainer.stop();
        }
    }

    public MongoDatabase mongoDatabase() {
        return mongoDatabase;
    }
}
