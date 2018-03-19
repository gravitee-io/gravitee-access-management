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

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderMapper;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderRoleMapper;
import io.reactivex.Observable;
import org.bson.Document;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.IOException;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class MongoAuthenticationProviderTestConfiguration {

    private static final MongodStarter starter = MongodStarter.getDefaultInstance();
    private static MongodExecutable _mongodExe;
    private static MongodProcess _mongod;

    @PostConstruct
    public void init() throws IOException {
        _mongodExe = starter.prepare(new MongodConfigBuilder()
                .version(Version.Main.PRODUCTION)
                .net(new Net("localhost", 12347, Network.localhostIsIPv6()))
                .build());
        _mongod = _mongodExe.start();

        MongoClient mongo = MongoClients.create("mongodb://localhost:12347");
        MongoDatabase database = mongo.getDatabase("test-idp-mongo");
        Observable.fromPublisher(database.createCollection("users")).subscribe();
        MongoCollection<Document> collection = database.getCollection("users");
        Document doc = new Document("username", "bob").append("password", "bobspassword");
        Observable.fromPublisher(collection.insertOne(doc)).subscribe();
    }

    @Bean
    public MongoIdentityProviderConfiguration mongoIdentityProviderConfiguration() {
        MongoIdentityProviderConfiguration configuration = new MongoIdentityProviderConfiguration();

        configuration.setUri("mongodb://localhost:12347");
        configuration.setDatabase("test-idp-mongo");
        configuration.setUsersCollection("users");
        configuration.setFindUserByUsernameQuery("{username: ?}");
        configuration.setPasswordField("password");

        return configuration;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        return new MongoAuthenticationProvider();
    }

    @Bean
    public MongoIdentityProviderMapper mapper() {
        return new MongoIdentityProviderMapper();
    }

    @Bean
    public MongoIdentityProviderRoleMapper roleMapper() {
        return new MongoIdentityProviderRoleMapper();
    }
}
