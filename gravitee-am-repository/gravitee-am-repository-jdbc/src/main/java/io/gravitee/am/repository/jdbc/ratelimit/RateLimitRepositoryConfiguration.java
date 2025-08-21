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

package io.gravitee.am.repository.jdbc.ratelimit;

import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.jdbc.common.AbstractRepositoryConfiguration;
import io.gravitee.am.repository.jdbc.common.dialect.DatabaseDialectHelper;
import io.gravitee.am.repository.jdbc.exceptions.RepositoryInitializationException;
import io.gravitee.am.repository.jdbc.provider.R2DBCConnectionConfiguration;
import io.gravitee.am.repository.jdbc.provider.impl.R2DBCPoolWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;

import java.util.Optional;

import static io.gravitee.am.repository.Scope.GATEWAY;

@Configuration
@ComponentScan(basePackages = {"io.gravitee.am.repository.jdbc.ratelimit"})
public class RateLimitRepositoryConfiguration extends AbstractRepositoryConfiguration {

    public static final String LIQUIBASE_FILE = "liquibase/ratelimit-master.yml";

    @Autowired
    public ConnectionProvider<ConnectionFactory, R2DBCConnectionConfiguration> connectionFactoryProvider;

    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
        return getRateLimitPool().getClient();
    }

    private R2DBCPoolWrapper getRateLimitPool() {
        return (R2DBCPoolWrapper) connectionFactoryProvider.getClientWrapper(Scope.RATE_LIMIT.getName());
    }

    protected String getDriver() {
        return getRateLimitPool().getJdbcDriver();
    }

    @Override
    protected Optional<Converter> jsonConverter() {
        if (getDriver().equals("postgresql")) {
            return instantiatePostgresJsonConverter();
        }
        return Optional.empty();
    }

    @Override
    @Bean
    public DatabaseDialectHelper databaseDialectHelper(R2dbcDialect dialect) {
        String collation = environment.getProperty(Scope.RATE_LIMIT.getRepositoryPropertyKey() + ".jdbc.collation");
        if (getDriver().equals(POSTGRESQL_DRIVER)) {
            return instantiateDialectHelper(DIALECT_HELPER_POSTGRESQL, dialect, collation);
        }
        if (getDriver().equals(MYSQL_DRIVER)) {
            return instantiateDialectHelper(DIALECT_HELPER_MYSQL, dialect, collation);
        }
        if (getDriver().equals(MARIADB_DRIVER)) {
            return instantiateDialectHelper(DIALECT_HELPER_MARIADB, dialect, collation);
        }
        if (getDriver().equals(SQLSERVER_DRIVER)) {
            return instantiateDialectHelper(DIALECT_HELPER_SQLSERVER, dialect, collation);
        }
        throw new RepositoryInitializationException("Unsupported driver " + getDriver());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeDatabaseSchema(getRateLimitPool(), environment, Scope.RATE_LIMIT.getRepositoryPropertyKey() + ".jdbc.", LIQUIBASE_FILE);
    }

}
