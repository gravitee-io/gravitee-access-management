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

import com.google.common.base.Strings;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.repository.jdbc.provider.R2DBCConnectionConfiguration;
import io.gravitee.am.repository.jdbc.provider.metrics.R2DBCConnectionMetrics;
import io.gravitee.am.repository.jdbc.provider.utils.ObjectUtils;
import io.gravitee.am.repository.jdbc.provider.utils.SchemaSupport;
import io.gravitee.am.repository.jdbc.provider.utils.TlsOptionsHelper;
import io.gravitee.node.monitoring.metrics.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.PoolingConnectionFactoryProvider;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import io.r2dbc.spi.ValidationDepth;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ConnectionFactoryProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionFactoryProvider.class);
    public static final String TAG_SOURCE = "pool";
    public static final String TAG_PREFER_CURSORED_EXECUTION = "preferCursoredExecution";
    public static final String TAG_CURRENT_SCHEMA = "currentSchema";
    public static final String TAG_DRIVER = "r2dbc_driver";
    public static final String TAG_DATABASE = "r2dbc_db";
    public static final String TAG_SERVER = "r2dbc_server";
    public static final int DEFAULT_SETTINGS_ACQUIRE_RETRY = 1;
    public static final int DEFAULT_SETTINGS_INITIAL_SIZE = 1;
    public static final int DEFAULT_SETTINGS_MAX_SIZE = 50;
    public static final long DEFAULT_SETTINGS_MAX_IDLE_TIME = 30000;
    public static final long DEFAULT_SETTINGS_MAX_LIFE_TIME = -1;
    public static final long DEFAULT_SETTINGS_MAX_ACQUIRE_TIME = 3000;
    public static final long DEFAULT_SETTINGS_MAX_CREATE_CNX_TIME = 5000;
    public static final String DRIVER_SQLSERVER = "sqlserver";




    private final RepositoriesEnvironment environment;
    private final String prefix;

    public ConnectionFactoryProvider(RepositoriesEnvironment environment, String prefix) {
        this.prefix = prefix + ".jdbc.";
        this.environment = environment;
    }

    public static ConnectionFactory createClient(R2DBCConnectionConfiguration configuration) {
        LOGGER.info("Initializing connection pool for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
        ConnectionFactoryOptions.Builder builder = ConnectionFactoryOptions.builder()
                .option(DRIVER, "pool")
                .option(PROTOCOL, configuration.getProtocol())
                .option(HOST, configuration.getHost())
                .option(USER, configuration.getUser())
                .option(DATABASE, configuration.getDatabase());

        if (configuration.getPort() != null) {
            builder.option(PORT, configuration.getPort());
        }

        if (configuration.getPassword() != null) {
            builder.option(PASSWORD, configuration.getPassword());
        }

        // default values for connections
        // may be overridden by the configuration.getOptions
        builder
                .option(PoolingConnectionFactoryProvider.ACQUIRE_RETRY, DEFAULT_SETTINGS_ACQUIRE_RETRY)
                .option(PoolingConnectionFactoryProvider.INITIAL_SIZE, DEFAULT_SETTINGS_INITIAL_SIZE)
                .option(PoolingConnectionFactoryProvider.MAX_SIZE, DEFAULT_SETTINGS_MAX_SIZE)
                .option(PoolingConnectionFactoryProvider.MAX_IDLE_TIME, Duration.of(DEFAULT_SETTINGS_MAX_IDLE_TIME, ChronoUnit.MILLIS))
                .option(PoolingConnectionFactoryProvider.MAX_LIFE_TIME, Duration.of(DEFAULT_SETTINGS_MAX_LIFE_TIME, ChronoUnit.MILLIS))
                .option(PoolingConnectionFactoryProvider.MAX_ACQUIRE_TIME, Duration.of(DEFAULT_SETTINGS_MAX_ACQUIRE_TIME, ChronoUnit.MILLIS))
                .option(PoolingConnectionFactoryProvider.MAX_CREATE_CONNECTION_TIME, Duration.of(DEFAULT_SETTINGS_MAX_CREATE_CNX_TIME, ChronoUnit.MILLIS))
                .option(PoolingConnectionFactoryProvider.VALIDATION_DEPTH, ValidationDepth.LOCAL);

        var referCursorExecutionOptionNotFound = new AtomicBoolean(true);
        configuration.optionsStream().forEach(entry -> {
            String option = entry.getKey();
            String value = entry.getValue();
            builder.option(Option.valueOf(option), ObjectUtils.stringToValue(value));
            if (TAG_PREFER_CURSORED_EXECUTION.equals(option)) {
                referCursorExecutionOptionNotFound.set(false);
            }
        });

        if ( DRIVER_SQLSERVER.equalsIgnoreCase(configuration.getProtocol()) && referCursorExecutionOptionNotFound.get()) {
            // set default value for preferCurserExecution to false only for SQLServer if it is missing so
            // it will not override other driver default value if they introduce this option. This is a SQLServer workaround
            // due to https://github.com/r2dbc/r2dbc-mssql/issues/276
            builder.option(Option.valueOf(TAG_PREFER_CURSORED_EXECUTION), false);
        }

        ConnectionPool connectionPool = (ConnectionPool) ConnectionFactories.get(builder.build());
        LOGGER.info("Connection pool created for database server {} on host {}", configuration.getProtocol(), configuration.getHost());

        final Tags tags = Tags.of(
                Tag.of(TAG_SOURCE, "idp-r2dbc"),
                Tag.of(TAG_DRIVER, configuration.getProtocol()),
                Tag.of(TAG_DATABASE, configuration.getDatabase()),
                Tag.of(TAG_SERVER, configuration.getPort() == null ? configuration.getHost() : configuration.getHost() + ":" + configuration.getPort()));
        new R2DBCConnectionMetrics(Metrics.getDefaultRegistry(), tags)
                .register(connectionPool);

        return connectionPool;
    }

    public ConnectionFactory factory() {
        LOGGER.info("Initializing connection pool for {} database", prefix);
        ConnectionFactory connectionPool;
        String uri = environment.getProperty(prefix+"uri");
        if (uri != null) {
            connectionPool = ConnectionFactories.get(uri);
        } else {

            String driver = getJdbcDriver();
            String host = getJdbcHostname();
            String port = getJdbcPort();
            String user = getJdbcUsername();
            String pwd = getJdbcPassword();
            String db = getJdbcDatabase();
            var jdbcSchema = getJdbcSchema();

            if (driver == null || host == null) {
                LOGGER.error("Missing one of connection parameters 'driver', 'host' or 'port' for {} database", prefix);
                throw new IllegalArgumentException("Missing properties for '" + prefix + "' database");
            }

            ConnectionFactoryOptions.Builder builder = ConnectionFactoryOptions.builder()
                    .option(DRIVER, "pool") // force connection pool (https://github.com/r2dbc/r2dbc-pool#getting-started)
                    .option(PROTOCOL, driver) // set driver as protocol  (https://github.com/r2dbc/r2dbc-pool#getting-started)
                    .option(HOST, host)
                    .option(USER, user)
                    .option(DATABASE, db)
                    .option(PoolingConnectionFactoryProvider.ACQUIRE_RETRY, Integer.parseInt(environment.getProperty(prefix+"acquireRetry", ""+DEFAULT_SETTINGS_ACQUIRE_RETRY)))
                    .option(PoolingConnectionFactoryProvider.INITIAL_SIZE, Integer.parseInt(environment.getProperty(prefix+"initialSize", ""+DEFAULT_SETTINGS_INITIAL_SIZE)))
                    .option(PoolingConnectionFactoryProvider.MAX_SIZE, Integer.parseInt(environment.getProperty(prefix+"maxSize", ""+DEFAULT_SETTINGS_MAX_SIZE)))
                    .option(PoolingConnectionFactoryProvider.MAX_IDLE_TIME, Duration.of(Long.parseLong(environment.getProperty(prefix+"maxIdleTime", ""+DEFAULT_SETTINGS_MAX_IDLE_TIME)), ChronoUnit.MILLIS))
                    .option(PoolingConnectionFactoryProvider.MAX_LIFE_TIME, Duration.of(Long.parseLong(environment.getProperty(prefix+"maxLifeTime", ""+DEFAULT_SETTINGS_MAX_LIFE_TIME)), ChronoUnit.MILLIS))
                    .option(PoolingConnectionFactoryProvider.MAX_ACQUIRE_TIME, Duration.of(Long.parseLong(environment.getProperty(prefix+"maxAcquireTime", ""+DEFAULT_SETTINGS_MAX_ACQUIRE_TIME)), ChronoUnit.MILLIS))
                    .option(PoolingConnectionFactoryProvider.MAX_CREATE_CONNECTION_TIME, Duration.of(Long.parseLong(environment.getProperty(prefix+"maxCreateConnectionTime", ""+DEFAULT_SETTINGS_MAX_CREATE_CNX_TIME)), ChronoUnit.MILLIS));

            if (port != null) {
                builder.option(PORT, Integer.parseInt(port));
            }

            final String validationQuery = environment.getProperty(prefix + "validationQuery");
            if (validationQuery != null) {
                builder.option(PoolingConnectionFactoryProvider.VALIDATION_QUERY, validationQuery)
                        .option(PoolingConnectionFactoryProvider.VALIDATION_DEPTH, ValidationDepth.REMOTE);
            } else {
                builder.option(PoolingConnectionFactoryProvider.VALIDATION_DEPTH, ValidationDepth.LOCAL);
            }

            if (pwd != null) {
                builder.option(PASSWORD, pwd);
            }

            builder = TlsOptionsHelper.setSSLOptions(builder, environment, prefix, driver);

            // Add schema support for postgres
            if(jdbcSchema.isPresent()){
                String currentSchema = jdbcSchema.get();
                if(SchemaSupport.supportsSchema(driver)){
                    builder.option(Option.valueOf(TAG_CURRENT_SCHEMA), currentSchema);
                } else {
                    LOGGER.warn("Schema parameter '{}' detected for {} driver. Note: {} does not support schemas. This parameter will be ignored.", currentSchema, driver, driver);
                }
            }

            final String preferCursorExecution = environment.getProperty(prefix + TAG_PREFER_CURSORED_EXECUTION);
            if ( DRIVER_SQLSERVER.equalsIgnoreCase(driver) ) {
                // set default value for preferCurserExecution to false only for SQLServer if it is missing so
                // it will not override other driver default value if they introduce this option. This is a SQLServer workaround
                // due to https://github.com/r2dbc/r2dbc-mssql/issues/276
                builder.option(Option.valueOf(TAG_PREFER_CURSORED_EXECUTION), StringUtils.hasLength(preferCursorExecution) ? preferCursorExecution : "false");
            } else if (StringUtils.hasLength(preferCursorExecution)) {
                builder.option(Option.valueOf(TAG_PREFER_CURSORED_EXECUTION), preferCursorExecution);
            }

            connectionPool = ConnectionFactories.get(builder.build());
        }

        LOGGER.info("Connection pool created for {} database", prefix);

        if (connectionPool instanceof ConnectionPool connection) {
            final Tags tags = Tags.of(
                    Tag.of(TAG_SOURCE, "common-pool"),
                    Tag.of(TAG_DRIVER, getJdbcDriver()),
                    Tag.of(TAG_DATABASE, getJdbcDatabase()),
                    Tag.of(TAG_SERVER, Strings.isNullOrEmpty(getJdbcPort()) ? getJdbcHostname() : getJdbcHostname() + ":" + getJdbcPort()));
            new R2DBCConnectionMetrics(Metrics.getDefaultRegistry(), tags)
                    .register(connection);
        }
        return connectionPool;
    }

    public String getJdbcDriver() {
        return environment.getProperty(prefix+"driver");
    }

    public String getJdbcDatabase() {
        return environment.getProperty(prefix+"database");
    }

    public String getJdbcHostname() {
        return environment.getProperty(prefix+"host");
    }

    public String getJdbcPort() {
        return environment.getProperty(prefix+"port");
    }

    public String getJdbcUsername() {
        return environment.getProperty(prefix+"username");
    }

    public String getJdbcPassword() {
        return environment.getProperty(prefix+"password");
    }

    public Optional<String> getJdbcSchema() {
        return Optional.ofNullable(environment.getProperty(prefix+"schema"));
    }

}
