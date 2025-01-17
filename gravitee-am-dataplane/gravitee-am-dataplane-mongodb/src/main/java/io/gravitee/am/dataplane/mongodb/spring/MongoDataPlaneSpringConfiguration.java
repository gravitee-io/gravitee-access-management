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

package io.gravitee.am.dataplane.mongodb.spring;


import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.repository.mongodb.provider.MongoConnectionConfiguration;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.URI;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@ComponentScan(basePackages = {"io.gravitee.am.dataplane.mongodb.repository"})
public class MongoDataPlaneSpringConfiguration {

    @Autowired
    private Environment environment;

    @Autowired
    private ConnectionProvider<MongoClient, MongoConnectionConfiguration> connectionProvider;

    @Autowired
    private DataPlaneDescription description;

    @Bean(name = "mongoClientWrapper")
    public ClientWrapper<MongoClient> mongoClient() {
        return connectionProvider.getClientWrapperFromPrefix(description.propertiesBase());
    }

    @Bean(name = "dataPlaneMongoDatabase")
    public MongoDatabase mongoOperations(ClientWrapper<MongoClient> mongoClient) {
        return mongoClient.getClient().getDatabase(getDatabaseName(description.propertiesBase()));
    }

    private String getDatabaseName(String propertiesPrefix) {
        String uri = environment.getProperty(propertiesPrefix + ".mongodb.uri");
        if (uri != null && !uri.isEmpty()) {
            final String path = URI.create(uri).getPath();
            if (path != null && path.length() > 1) {
                return path.substring(1);
            }
        }

        return environment.getProperty(propertiesPrefix + ".mongodb.dbname", "gravitee-am");
    }
}
