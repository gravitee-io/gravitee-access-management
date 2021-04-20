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

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import de.flapdoodle.embed.mongo.Command;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.config.IRuntimeConfig;
import de.flapdoodle.embed.process.config.io.ProcessOutput;
import java.util.Collections;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmbeddedClient implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedClient.class);

    static MongodProcess mongod;
    static MongodExecutable mongodExecutable;

    private String databaseName;
    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;

    public EmbeddedClient(String databaseName) {
        this.databaseName = databaseName;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        final IMongodConfig mongodConfig = new MongodConfigBuilder().version(Version.Main.PRODUCTION).build();

        IRuntimeConfig runtimeConfig = new RuntimeConfigBuilder()
            .defaultsWithLogger(Command.MongoD, logger)
            .processOutput(ProcessOutput.getDefaultInstanceSilent())
            .build();

        MongodStarter runtime = MongodStarter.getInstance(runtimeConfig);

        MongodExecutable mongodExecutable = runtime.prepare(mongodConfig);
        mongod = mongodExecutable.start();

        // cluster configuration
        ClusterSettings clusterSettings = ClusterSettings
            .builder()
            .hosts(
                Collections.singletonList(
                    new ServerAddress(mongodConfig.net().getServerAddress().getHostName(), mongodConfig.net().getPort())
                )
            )
            .build();
        // codec configuration
        CodecRegistry pojoCodecRegistry = fromRegistries(
            MongoClients.getDefaultCodecRegistry(),
            fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );

        MongoClientSettings settings = MongoClientSettings
            .builder()
            .clusterSettings(clusterSettings)
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

        if (mongod != null) {
            mongod.stop();
        }
        if (mongodExecutable != null) {
            mongodExecutable.stop();
        }
    }

    public MongoDatabase mongoDatabase() {
        return mongoDatabase;
    }
}
