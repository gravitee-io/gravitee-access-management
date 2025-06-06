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
package io.gravitee.am.dataplane.api.repository.test;

import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.dataplane.api.repository.test.testcontainers.DatabaseUrlProvider;
import io.gravitee.am.dataplane.api.repository.test.testcontainers.R2dbcDatabaseContainer;
import io.gravitee.am.dataplane.jdbc.dialect.DatabaseDialectHelper;
import io.gravitee.am.dataplane.jdbc.exceptions.RepositoryInitializationException;
import io.gravitee.am.dataplane.jdbc.spring.AbstractRepositoryConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractTestRepositoryConfiguration extends AbstractRepositoryConfiguration {
    private final Logger LOGGER = LoggerFactory.getLogger(AbstractTestRepositoryConfiguration.class);

    @Autowired
    protected DatabaseUrlProvider provider;

    @Autowired
    protected R2dbcDatabaseContainer container;

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeDatabaseSchema(provider.getDatabaseContainer().getOptions(), environment);
    }

    protected void initializeDatabaseSchema(ConnectionFactoryOptions options, RepositoriesEnvironment env) throws SQLException {
        Boolean enabled = env.getProperty("liquibase.enabled", Boolean.class, true);
        if (enabled) {
            final String jdbcUrl = container.getJdbcUrl();

            try (Connection connection = DriverManager.getConnection(jdbcUrl,
                    (String) options.getValue(USER), options.getValue(PASSWORD).toString())) {
                LOGGER.debug("Running Liquibase on {}", jdbcUrl);
                runLiquibase(connection);
                runLiquibase_addSpecificTestIndexes(connection);
            }
        }
    }

    protected final void runLiquibase_addSpecificTestIndexes(Connection connection) {
        System.setProperty("liquibase.databaseChangeLogTableName", "dp_databasechangelog");
        System.setProperty("liquibase.databaseChangeLogLockTableName", "dp_databasechangeloglock");

        try (ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor(this.getClass().getClassLoader())) {
            final Liquibase liquibase = new Liquibase("liquibase-test/master.yml", resourceAccessor, new JdbcConnection(connection));
            liquibase.update((Contexts) null);
        } catch (Exception ex) {
            LOGGER.error("Failed to set up database: ", ex);
        }
    }

    @Override
    protected Optional<Converter> jsonConverter() {
        if (provider.getDatabaseType().equals("postgresql")) {
            return instantiatePostgresJsonConverter();
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
            return instantiateDialectHelper("MsSqlHelper", dialect, "Latin1_General_BIN2");
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
        R2dbcDatabaseContainer dbContainer = provider.getDatabaseContainer();
        ConnectionFactoryOptions options = dbContainer.getOptions();
        options = ConnectionFactoryOptions.builder()
                .option(Option.valueOf("preferCursoredExecution"), false) // due to r2dbc-mssql 1.0.1+
                .from(options)
                .option(DRIVER, "pool")
                .option(PROTOCOL,(String) options.getValue(DRIVER))
                .build();
        return ConnectionFactories.get(options);
    }

    @Bean
    public RepositoriesEnvironment repositoriesEnvironment(Environment environment) {
        return new RepositoriesEnvironment(environment);
    }

}
