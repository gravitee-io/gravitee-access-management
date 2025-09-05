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
package io.gravitee.am.repository.jdbc.provider.impl;

import io.gravitee.am.repository.jdbc.provider.R2DBCConnectionConfiguration;
import io.gravitee.am.repository.jdbc.provider.utils.SchemaSupport;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import org.reactivestreams.Publisher;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.gravitee.am.repository.jdbc.provider.impl.ConnectionFactoryProvider.TAG_CURRENT_SCHEMA;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class R2DBCPoolWrapper implements ClientWrapper<ConnectionFactory>, ConnectionFactory, Closeable {

    private final R2DBCConnectionConfiguration configuration;
    private final ConnectionFactoryProvider connectionFactoryProvider;
    private final ConnectionFactory connectionFactory;
    private final String dbName;

    private AtomicInteger reference = new AtomicInteger(0);

    public R2DBCPoolWrapper(ConnectionFactoryProvider connectionFactoryProvider) {
        this.connectionFactoryProvider = connectionFactoryProvider;
        this.connectionFactory = connectionFactoryProvider.factory();
        this.configuration = null;
        this.dbName = connectionFactoryProvider.getJdbcDatabase();
    }

    public R2DBCPoolWrapper(R2DBCConnectionConfiguration configuration, ConnectionFactory connectionFactory) {
        this.configuration = configuration;
        this.connectionFactory = connectionFactory;
        this.connectionFactoryProvider = null;
        this.dbName = configuration.getDatabase();
    }

    @Override
    public ConnectionFactory getClient() {
        this.reference.incrementAndGet();
        return this;
    }

    @Override
    public void releaseClient() {
        if (this.reference.decrementAndGet() <= 0) {
            this.shutdown();
        }
    }

    @Override
    public String getDatabaseName() {
        return dbName;
    }

    @Override
    public Publisher<? extends Connection> create() {
        return this.connectionFactory.create();
    }

    @Override
    public ConnectionFactoryMetadata getMetadata() {
        return this.connectionFactory.getMetadata();
    }

    @Override
    public void close() throws IOException {
        this.releaseClient();
    }

    void shutdown() {
        this.reference.set(0);
        if (this.connectionFactory != null && connectionFactory instanceof ConnectionPool pool && !pool.isDisposed()) {
                // dispose is a blocking call, use the non blocking one to avoid error
                pool.disposeLater().subscribe();
            }

    }

    public String getJdbcDriver() {
        return this.connectionFactoryProvider != null ? this.connectionFactoryProvider.getJdbcDriver() : this.configuration.getProtocol();
    }

    public String getJdbcDatabase() {
        return this.connectionFactoryProvider != null ? this.connectionFactoryProvider.getJdbcDatabase() : this.configuration.getDatabase();
    }

    public String getJdbcHostname() {
        return this.connectionFactoryProvider != null ? this.connectionFactoryProvider.getJdbcHostname() : this.configuration.getHost();
    }

    public String getJdbcPort() {
        return this.connectionFactoryProvider != null ? this.connectionFactoryProvider.getJdbcPort() : Integer.toString(this.configuration.getPort());
    }

    public String getJdbcUsername() {
        return this.connectionFactoryProvider != null ? this.connectionFactoryProvider.getJdbcUsername() : this.configuration.getUser();
    }

    public String getJdbcPassword() {
        return this.connectionFactoryProvider != null ? this.connectionFactoryProvider.getJdbcPassword() : this.configuration.getPassword();
    }

    public Optional<String> getJdbcSchema() {
        Optional<String> schema = this.connectionFactoryProvider != null ? this.connectionFactoryProvider.getJdbcSchema() : this.configuration.getOption(TAG_CURRENT_SCHEMA);
        return schema.map(String::trim).filter(s -> !s.isEmpty());
    }

    public boolean supportsSchema(){
        return SchemaSupport.supportsSchema(getJdbcDriver());
    }


}
