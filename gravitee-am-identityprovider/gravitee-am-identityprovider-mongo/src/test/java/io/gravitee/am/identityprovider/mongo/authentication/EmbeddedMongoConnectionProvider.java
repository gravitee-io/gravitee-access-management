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
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderConfiguration;
import io.gravitee.am.repository.mongodb.provider.MongoConnectionConfiguration;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmbeddedMongoConnectionProvider implements ConnectionProvider<MongoClient, MongoConnectionConfiguration> {

    private final ClientWrapper wrapper;
    
    public EmbeddedMongoConnectionProvider(MongoIdentityProviderConfiguration configuration) {
        MongoClient mongoClient = MongoClients.create(configuration.getUri());
        this.wrapper = new ClientWrapper() {
            @Override
            public Object getClient() {
                return mongoClient;
            }

            @Override
            public void releaseClient() {
                mongoClient.close();
            }
        };
    }

    @Override
    public ClientWrapper getClientWrapper() {
        return this.wrapper;
    }

    @Override
    public ClientWrapper getClientWrapper(String name) {
        return this.wrapper;
    }

    @Override
    public ClientWrapper getClientFromConfiguration(MongoConnectionConfiguration configuration) {
        return this.wrapper;
    }

    @Override
    public ConnectionProvider stop() throws Exception {
        return this;
    }

    @Override
    public boolean canHandle(String backendType) {
        return BACKEND_TYPE_MONGO.equals(backendType);
    }
}
