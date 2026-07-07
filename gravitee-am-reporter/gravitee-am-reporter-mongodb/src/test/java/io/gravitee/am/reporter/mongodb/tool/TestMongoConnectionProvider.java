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
package io.gravitee.am.reporter.mongodb.tool;

import com.mongodb.reactivestreams.client.MongoClient;
import io.gravitee.am.repository.mongodb.provider.MongoConnectionConfiguration;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;

/**
 * Minimal {@link ConnectionProvider} backed by a single testcontainer-managed {@link MongoClient}.
 * Only {@link #getClientWrapper()} is exercised by the {@code MongoAuditReporter}, the remaining
 * methods are not used within the tests.
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TestMongoConnectionProvider implements ConnectionProvider<MongoClient, MongoConnectionConfiguration> {

    private final ClientWrapper<MongoClient> clientWrapper;

    public TestMongoConnectionProvider(ClientWrapper<MongoClient> clientWrapper) {
        this.clientWrapper = clientWrapper;
    }

    @Override
    public ClientWrapper<MongoClient> getClientWrapper() {
        return clientWrapper;
    }

    @Override
    public ClientWrapper<MongoClient> getClientWrapper(String name) {
        return clientWrapper;
    }

    @Override
    public ClientWrapper<MongoClient> getClientFromConfiguration(MongoConnectionConfiguration configuration) {
        return clientWrapper;
    }

    @Override
    public ClientWrapper<MongoClient> getClientWrapperFromPrefix(String prefix) {
        return clientWrapper;
    }

    @Override
    public boolean canHandle(String backendType) {
        return BACKEND_TYPE_MONGO.equals(backendType);
    }

    @Override
    public ConnectionProvider stop() {
        return this;
    }
}
