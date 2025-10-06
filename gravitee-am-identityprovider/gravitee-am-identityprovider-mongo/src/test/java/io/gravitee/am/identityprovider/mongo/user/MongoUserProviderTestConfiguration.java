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
package io.gravitee.am.identityprovider.mongo.user;

import java.util.UUID;

import static org.mockito.Mockito.mock;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.mongo.authentication.EmbeddedClient;
import io.gravitee.am.identityprovider.mongo.authentication.EmbeddedMongoConnectionProvider;
import io.gravitee.am.identityprovider.mongo.utils.PasswordEncoder;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.plugins.dataplane.core.DataPlaneLoader;
import io.gravitee.am.plugins.dataplane.core.DataPlanePluginManager;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistryImpl;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.gravitee.am.service.spring.datasource.DataSourcesConfiguration;
import io.reactivex.rxjava3.core.Observable;
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
public class MongoUserProviderTestConfiguration implements InitializingBean {

    @Autowired
    private MongoDatabase mongoDatabase;

    @Override
    public void afterPropertiesSet() throws Exception {
        MongoCollection<Document> collection = mongoDatabase.getCollection("users");
        Document doc = new Document("username", "bob").append("password", "bobspassword").append("_id", UUID.randomUUID().toString());
        Observable.fromPublisher(collection.insertOne(doc)).blockingFirst();
        Document doc2 = new Document("username", "user01").append("email", "user01@acme.com").append("password", "user01").append("_id", UUID.randomUUID().toString());
        Observable.fromPublisher(collection.insertOne(doc2)).blockingFirst();
        Document doc3 = new Document("username", "user02").append("email", "user02@acme.com").append("alternative_email", "user02-alt@acme.com").append("password", "user02").append("_id", UUID.randomUUID().toString());
        Observable.fromPublisher(collection.insertOne(doc3)).blockingFirst();
        Document doc4 = new Document("username", "changeme").append("password", "changepass").append("_id", UUID.randomUUID().toString());
        Observable.fromPublisher(collection.insertOne(doc4)).blockingFirst();
        Document doc5 = new Document("username", "b o b").append("password", "changepass").append("_id", UUID.randomUUID().toString());
        Observable.fromPublisher(collection.insertOne(doc5)).blockingFirst();
        Document doc6 = new Document("username", "UserWithCase").append("email", "user02@acme.com").append("alternative_email", "user02-alt@acme.com").append("password", "user02").append("_id", UUID.randomUUID().toString());
        Observable.fromPublisher(collection.insertOne(doc6)).blockingFirst();

    }

    @Bean
    public DataPlaneRegistry dataPlaneRegistry() {
        return new DataPlaneRegistryImpl(mock(DataPlaneLoader.class), mock(DataPlanePluginManager.class));
    }

    @Bean
    public DataSourcesConfiguration dataSourcesConfiguration() {
        return new DataSourcesConfiguration(null);
    }

    @Bean
    public MongoIdentityProviderConfiguration mongoIdentityProviderConfiguration() {
        MongoIdentityProviderConfiguration configuration = new MongoIdentityProviderConfiguration();

        String host = embeddedClient().getMongoClient().getClusterDescription().getClusterSettings().getHosts().get(0).toString();
        configuration.setUri("mongodb://" + host);
        configuration.setDatabase("test-idp-mongo");
        configuration.setUsersCollection("users");
        configuration.setFindUserByUsernameQuery("{username: ?}");
        configuration.setFindUserByMultipleFieldsQuery("{ $or : [{username: ?}, {email: ?}]}");
        configuration.setFindUserByEmailQuery("{ $or : [{email: ?}, {alternative_email: ?}]}");
        configuration.setPasswordField("password");
        configuration.setPasswordEncoder(PasswordEncoder.NONE);

        return configuration;
    }

    @Bean
    public UserProvider userProvider() {
        return new MongoUserProvider();
    }

    @Bean
    public IdentityProviderMapper mapper() {
        return new DefaultIdentityProviderMapper();
    }

    @Bean
    public IdentityProviderRoleMapper roleMapper() {
        return new DefaultIdentityProviderRoleMapper();
    }

    @Bean
    public EmbeddedClient embeddedClient() {
        return new EmbeddedClient("test-idp-mongo");
    }

    @Bean
    public MongoDatabase mongoDatabase() {
        return embeddedClient().mongoDatabase();
    }

    @Bean
    public ConnectionProvider mongoConnectionProvider(MongoIdentityProviderConfiguration config) {
        return new EmbeddedMongoConnectionProvider(config);
    }

    @Bean
    public IdentityProvider identityProviderEntity() {
        final IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setSystem(true);
        return identityProvider;
    }
}
