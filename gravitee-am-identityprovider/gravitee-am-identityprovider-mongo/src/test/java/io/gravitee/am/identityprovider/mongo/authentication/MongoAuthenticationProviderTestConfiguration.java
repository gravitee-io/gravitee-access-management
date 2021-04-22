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

import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderMapper;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.mongo.utils.PasswordEncoder;
import io.reactivex.Observable;
import org.bson.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class MongoAuthenticationProviderTestConfiguration implements InitializingBean {

    @Autowired
    private MongoDatabase mongoDatabase;

    @Override
    public void afterPropertiesSet() throws Exception {
        Observable.fromPublisher(mongoDatabase.createCollection("users")).blockingFirst();
        MongoCollection<Document> collection = mongoDatabase.getCollection("users");
        Document doc = new Document("username", "bob").append("password", "bobspassword");
        Observable.fromPublisher(collection.insertOne(doc)).blockingFirst();
        Document doc2 = new Document("username", "user01").append("email", "user01@acme.com").append("password", "user01");
        Observable.fromPublisher(collection.insertOne(doc2)).blockingFirst();
    }

    @Bean
    public MongoIdentityProviderConfiguration mongoIdentityProviderConfiguration() {
        MongoIdentityProviderConfiguration configuration = new MongoIdentityProviderConfiguration();

        String host = embeddedClient().getMongoClient().getSettings().getClusterSettings().getHosts().get(0).toString();
        configuration.setUri("mongodb://" + host);
        configuration.setDatabase("test-idp-mongo");
        configuration.setUsersCollection("users");
        configuration.setFindUserByUsernameQuery("{username: ?}");
        configuration.setFindUserByMultipleFieldsQuery("{ $or : [{username: ?}, {email: ?}]}");
        configuration.setPasswordField("password");
        configuration.setPasswordEncoder(PasswordEncoder.NONE);

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

    @Bean
    public EmbeddedClient embeddedClient() {
        return new EmbeddedClient("test-idp-mongo");
    }

    @Bean
    public MongoDatabase mongoDatabase() {
        return embeddedClient().mongoDatabase();
    }
}
