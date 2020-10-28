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
package io.gravitee.am.repository.jdbc.management;

import io.gravitee.am.repository.jdbc.common.AbstractRepositoryConfiguration;
import io.gravitee.am.repository.jdbc.common.ConnectionFactoryProvider;
import io.gravitee.am.repository.jdbc.common.dialect.DatabaseDialectHelper;
import io.gravitee.am.repository.jdbc.exceptions.RepositoryInitializationException;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@ComponentScan({
        "io.gravitee.am.repository.jdbc.management"
})
@EnableR2dbcRepositories
public class ManagementRepositoryConfiguration extends AbstractRepositoryConfiguration{

    @Autowired
    protected ConnectionFactory connectionFactory;

    @Autowired
    protected ConnectionFactoryProvider connectionFactoryProvider;

    @Autowired
    private DatabaseDialectHelper databaseDialectHelper;

    @Bean
    public ConnectionFactoryProvider connectionFactoryProvider() {
        return new ConnectionFactoryProvider(environment, "management");
    }

    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
        return connectionFactoryProvider().factory();
    }

    protected String getDriver() {
        return connectionFactoryProvider().getDatabaseType();
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
        if (getDriver().equals(POSTGRESQL_DRIVER)) {
            return instantiateDialectHelper(DIALECT_HELPER_POSTGRESQL, dialect);
        }
        if (getDriver().equals(MYSQL_DRIVER)) {
            return instantiateDialectHelper(DIALECT_HELPER_MYSQL, dialect);
        }
        if (getDriver().equals(MARIADB_DRIVER)) {
            return instantiateDialectHelper(DIALECT_HELPER_MARIADB, dialect);
        }
        if (getDriver().equals(MSSQL_DRIVER)) {
            return instantiateDialectHelper(DIALECT_HELPER_MSSQL, dialect);
        }
        throw new RepositoryInitializationException("Unsupported driver " + getDriver());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeDatabaseSchema(connectionFactoryProvider, environment);
    }
}