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
package io.gravitee.am.repository.jdbc.common;

import io.gravitee.am.repository.jdbc.common.dialect.DatabaseDialectHelper;
import io.gravitee.am.repository.jdbc.exceptions.RepositoryInitializationException;
import io.gravitee.am.repository.jdbc.management.ManagementRepositoryConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractTestRepositoryConfiguration extends AbstractRepositoryConfiguration {

    @Autowired
    protected DatabaseUrlProvider provider;
    @Autowired
    protected R2dbcDatabaseContainer container;
    @Override
    public void afterPropertiesSet() throws Exception {
        initializeDatabaseSchema(provider.getDatabaseContainer().getOptions(), environment);
    }

    protected void initializeDatabaseSchema(ConnectionFactoryOptions options, Environment env) throws SQLException {
        Boolean enabled = env.getProperty("liquibase.enabled", Boolean.class, true);
        if (enabled) {
            final String jdbcUrl = container.getJdbcUrl();

            try (Connection connection = DriverManager.getConnection(jdbcUrl,
                    options.getValue(USER), options.getValue(PASSWORD).toString())) {
                LOGGER.debug("Running Liquibase on {}", jdbcUrl);
                runLiquibase(connection);
            }
        }
    }

    @Override
    protected Optional<Converter> jsonConverter() {
        if (provider.getDatabaseType().equals("postgresql")) {
            return ManagementRepositoryConfiguration.instantiatePostgresJsonConverter();
        }
        return Optional.empty();
    }

    @Override
    @Bean
    public DatabaseDialectHelper databaseDialectHelper(R2dbcDialect dialect) {
        if (provider.getDatabaseType().equals("postgresql")) {
            return instantiateDialectHelper("PostgresqlHelper", dialect, null);
        }
        if (provider.getDatabaseType().equals("mysql")) {
            return instantiateDialectHelper("MySqlHelper", dialect, "utf8mb4_bin");
        }
        if (provider.getDatabaseType().equals("mariadb")) {
            return instantiateDialectHelper("MariadbHelper", dialect, "utf8mb4_bin");
        }
        if (provider.getDatabaseType().equals("sqlserver")) {
            return instantiateDialectHelper("MsSqlHelper", dialect, "Latin1_General_100_CS_AS");
        }
        throw new RepositoryInitializationException("Unsupported driver " + provider.getDatabaseType());
    }

    @Override
    protected String getDriver() {
        return provider.getDatabaseType();
    }

    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
        R2dbcDatabaseContainer container = provider.getDatabaseContainer();
        ConnectionFactoryOptions options = container.getOptions();
        options = ConnectionFactoryOptions.builder()
                .from(options)
                .option(DRIVER, "pool")
                .option(PROTOCOL, options.getValue(DRIVER))
                .build();
        return ConnectionFactories.get(options);
    }

}
