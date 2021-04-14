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

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.PoolingConnectionFactoryProvider;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ValidationDepth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ConnectionFactoryProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionFactoryProvider.class);

    private final Environment environment;
    private final String prefix;

    public ConnectionFactoryProvider(Environment environment, String prefix) {
        this.prefix = prefix + "." + "jdbc.";
        this.environment = environment;
    }

    public String getDatabaseType() {
        return environment.getProperty(prefix+"driver");
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
                    .option(PoolingConnectionFactoryProvider.ACQUIRE_RETRY, Integer.parseInt(environment.getProperty(prefix+"acquireRetry", "1")))
                    .option(PoolingConnectionFactoryProvider.INITIAL_SIZE, Integer.parseInt(environment.getProperty(prefix+"initialSize", "0")))
                    .option(PoolingConnectionFactoryProvider.MAX_SIZE, Integer.parseInt(environment.getProperty(prefix+"maxSize", "10")))
                    .option(PoolingConnectionFactoryProvider.MAX_IDLE_TIME, Duration.of(Long.parseLong(environment.getProperty(prefix+"maxIdleTime", "30000")), ChronoUnit.MILLIS))
                    .option(PoolingConnectionFactoryProvider.MAX_LIFE_TIME, Duration.of(Long.parseLong(environment.getProperty(prefix+"maxLifeTime", "30000")), ChronoUnit.MILLIS))
                    .option(PoolingConnectionFactoryProvider.MAX_ACQUIRE_TIME, Duration.of(Long.parseLong(environment.getProperty(prefix+"maxAcquireTime", "0")), ChronoUnit.MILLIS))
                    .option(PoolingConnectionFactoryProvider.MAX_CREATE_CONNECTION_TIME, Duration.of(Long.parseLong(environment.getProperty(prefix+"maxCreateConnectionTime", "0")), ChronoUnit.MILLIS));

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

            connectionPool = (ConnectionPool)ConnectionFactories.get(builder.build());
        }

        LOGGER.info("Connection pool created for {} database", prefix);
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
}
