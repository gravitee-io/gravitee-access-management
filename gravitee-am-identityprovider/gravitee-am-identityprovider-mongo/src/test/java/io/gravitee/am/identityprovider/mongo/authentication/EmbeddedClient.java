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
package io.gravitee.am.identityprovider.mongo.authentication;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.process.runtime.Network;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.net.InetAddress;

import static java.util.List.of;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;


/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmbeddedClient implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedClient.class);

    static TransitionWalker.ReachedState<RunningMongodProcess> mongodExecutable;

    private String databaseName;
    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;

    public EmbeddedClient(String databaseName) {
        this.databaseName = databaseName;
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        int port = Network.freeServerPort(InetAddress.getLocalHost());

        Version.Main version = Version.Main.V6_0;
        var mongod = Mongod.builder().net(Start.to(Net.class)
                .initializedWith(Net.builder().port(port).isIpv6(Network.localhostIsIPv6()).build()))
                .build();
        mongodExecutable = mongod.start(version);

        // cluster configuration
        final var serverAddress = mongodExecutable.current().getServerAddress();
        ClusterSettings clusterSettings = ClusterSettings.builder()
                .hosts(of(new ServerAddress(serverAddress.getHost(), serverAddress.getPort()))).build();
        // codec configuration
        var pojoCodecRegistry = CodecRegistries.fromRegistries(MongoClients.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));

        var settings = MongoClientSettings.builder()
                .applyToClusterSettings(clusterBuilder -> clusterBuilder.applySettings(clusterSettings))
                .codecRegistry(pojoCodecRegistry)
                .writeConcern(WriteConcern.ACKNOWLEDGED)
                .build();
        mongoClient = MongoClients.create(settings);
        mongoDatabase = mongoClient.getDatabase(databaseName);
    }

    public static com.mongodb.ServerAddress serverAddress(de.flapdoodle.embed.mongo.commands.ServerAddress serverAddress) {
        return new com.mongodb.ServerAddress(serverAddress.getHost(), serverAddress.getPort());
    }

    @Override
    public void destroy() throws Exception {
        if (mongoClient != null) {
            mongoClient.close();
        }

        if (mongodExecutable != null) {
            mongodExecutable.close();
        }
    }

    public MongoDatabase mongoDatabase() {
        return mongoDatabase;
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }
}
