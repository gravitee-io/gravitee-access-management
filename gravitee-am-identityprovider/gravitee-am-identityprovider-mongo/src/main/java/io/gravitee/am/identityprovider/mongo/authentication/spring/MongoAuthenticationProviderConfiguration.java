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
package io.gravitee.am.identityprovider.mongo.authentication.spring;

import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderConfiguration;
import io.gravitee.am.service.authentication.crypto.password.NoOpPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static java.util.Arrays.asList;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class MongoAuthenticationProviderConfiguration {

    @Autowired
    private MongoIdentityProviderConfiguration configuration;

    @Bean
    public MongoClient mongoClient() {
        MongoClient mongoClient;
        if ((this.configuration.getUri() != null) && (!this.configuration.getUri().isEmpty())) {
            mongoClient = MongoClients.create(this.configuration.getUri());
        } else {
            ServerAddress serverAddress = new ServerAddress(this.configuration.getHost(), this.configuration.getPort());
            ClusterSettings clusterSettings = ClusterSettings.builder().hosts(asList(serverAddress)).build();
            MongoClientSettings.Builder settings = MongoClientSettings.builder().clusterSettings(clusterSettings);
            if (this.configuration.isEnableCredentials()) {
                MongoCredential credential = MongoCredential.createCredential(this.configuration
                        .getUsernameCredentials(), this.configuration
                        .getDatabaseCredentials(), this.configuration
                        .getPasswordCredentials().toCharArray());
                settings.credential(credential);
            }
            mongoClient = MongoClients.create(settings.build());
        }
        return mongoClient;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}
