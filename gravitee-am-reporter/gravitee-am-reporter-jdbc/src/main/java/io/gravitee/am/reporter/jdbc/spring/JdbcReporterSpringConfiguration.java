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
package io.gravitee.am.reporter.jdbc.spring;

import io.gravitee.am.reporter.jdbc.JdbcReporterConfiguration;
import io.gravitee.am.reporter.jdbc.dialect.*;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.PoolingConnectionFactoryProvider;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ValidationDepth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.connectionfactory.R2dbcTransactionManager;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.transaction.ReactiveTransactionManager;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@ComponentScan("io.gravitee.am.reporter.jdbc")
public class JdbcReporterSpringConfiguration extends AbstractR2dbcConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcReporterSpringConfiguration.class);

    @Autowired
    private JdbcReporterConfiguration configuration;

    @Bean
    public R2dbcDialect dialectDatabase(ConnectionFactory factory) {
        return DialectResolver.getDialect(factory);
    }

    @Bean
    ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    @Bean
    public DialectHelper dialectHelper() {
        DialectHelper dialect = new PostgresqlDialect();
        if ("mysql".equalsIgnoreCase(configuration.getDriver())) {
            dialect = new MySQLDialect();
        }
        if ("mariadb".equalsIgnoreCase(configuration.getDriver())) {
            dialect = new MariaDBDialect();
        }
        if ("sqlserver".equalsIgnoreCase(configuration.getDriver())) {
            dialect = new MSSQLDialect();
        }
        return dialect;
    }

    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
        return buildConnectionFactory();
    }

    protected ConnectionFactory buildConnectionFactory() {
        LOGGER.info("Initializing connection pool for {} database", configuration.getDatabase());
        ConnectionFactory connectionPool;

        String driver = configuration.getDriver();
        String host = configuration.getHost();
        Integer port = configuration.getPort();
        String user = configuration.getUsername();
        String pwd = configuration.getPassword();
        String db = configuration.getDatabase();

        if (driver == null || host == null) {
            LOGGER.error("Missing one of connection parameters 'driver', 'host' ");
            throw new IllegalArgumentException("Missing connection property");
        }

        ConnectionFactoryOptions.Builder builder = ConnectionFactoryOptions.builder()
                .option(DRIVER, "pool") // force connection pool (https://github.com/r2dbc/r2dbc-pool#getting-started)
                .option(PROTOCOL, driver) // set driver as protocol  (https://github.com/r2dbc/r2dbc-pool#getting-started)
                .option(HOST, host)
                .option(USER, user)
                .option(PoolingConnectionFactoryProvider.ACQUIRE_RETRY, Optional.ofNullable(configuration.getAcquireRetry()).orElse(1))
                .option(PoolingConnectionFactoryProvider.INITIAL_SIZE, Optional.ofNullable(configuration.getInitialSize()).orElse(0))
                .option(PoolingConnectionFactoryProvider.MAX_SIZE, Optional.ofNullable(configuration.getMaxSize()).orElse(10))
                .option(PoolingConnectionFactoryProvider.MAX_IDLE_TIME, Duration.of(Optional.ofNullable(configuration.getMaxIdleTime()).orElse(30000), ChronoUnit.MILLIS))
                .option(PoolingConnectionFactoryProvider.MAX_LIFE_TIME, Duration.of(Optional.ofNullable(configuration.getMaxLifeTime()).orElse(30000), ChronoUnit.MILLIS))
                .option(PoolingConnectionFactoryProvider.MAX_ACQUIRE_TIME, Duration.of(Optional.ofNullable(configuration.getMaxAcquireTime()).orElse(0), ChronoUnit.MILLIS))
                .option(PoolingConnectionFactoryProvider.MAX_CREATE_CONNECTION_TIME, Duration.of(Optional.ofNullable(configuration.getMaxCreateConnectionTime()).orElse(0), ChronoUnit.MILLIS));

        final String validationQuery = configuration.getValidationQuery();
        if (validationQuery != null) {
            builder.option(PoolingConnectionFactoryProvider.VALIDATION_DEPTH, ValidationDepth.REMOTE)
                    .option(PoolingConnectionFactoryProvider.VALIDATION_QUERY, validationQuery);
        } else {
            builder.option(PoolingConnectionFactoryProvider.VALIDATION_DEPTH, ValidationDepth.LOCAL);
        }

        if (port != null) {
            builder.option(PORT, port);
        }

        if (pwd != null) {
            builder.option(PASSWORD, pwd);
        }

        if (db != null) {
            builder.option(DATABASE, db);
        }

        connectionPool = (ConnectionPool)ConnectionFactories.get(builder.build());

        LOGGER.info("Connection pool created for {} database", db);
        return connectionPool;
    }
}
