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
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderGroupMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderGroupMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.mongo.utils.PasswordEncoder;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.gravitee.am.service.spring.datasource.DataSourcesConfiguration;
import io.reactivex.rxjava3.core.Observable;
import org.bson.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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
        MongoCollection<Document> collection = mongoDatabase.getCollection("users");
        List<Document> users = List.of(
                new Document("username", "bob").append("password", "bobspassword"),
                new Document("username", "user01").append("email", "user01@acme.com").append("password", "user01"),
                new Document("username", "user02").append("email", "common@acme.com").append("password", "user02"),
                new Document("username", "user03").append("email", "common@acme.com").append("password", "user03"),
                new Document("username", "b o b").append("email", "b+o+b@acme.com").append("password", "b o bpassword"),
                new Document("username", "UserWithCase").append("email", "userwithcase@acme.com").append("password", "UserWithCase")
        );
        users.forEach(doc -> Observable.fromPublisher(collection.insertOne(doc)).blockingFirst());
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
        configuration.setPasswordField("password");
        configuration.setPasswordEncoder(PasswordEncoder.NONE);

        return configuration;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        return new MongoAuthenticationProvider();
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
    public IdentityProviderGroupMapper groupMapper() {
        return new DefaultIdentityProviderGroupMapper();
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
