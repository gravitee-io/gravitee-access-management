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
package io.gravitee.am.identityprovider.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.identityprovider.jdbc.configuration.JdbcIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.jdbc.utils.ObjectUtils;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.common.service.AbstractService;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.PoolingConnectionFactoryProvider;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import io.r2dbc.spi.ValidationDepth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JdbcAbstractProvider<T extends LifecycleComponent<T>> extends AbstractService<T>  {

    protected static final Logger LOGGER = LoggerFactory.getLogger(JdbcAbstractProvider.class);

    @Autowired
    protected JdbcIdentityProviderConfiguration configuration;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected ConnectionPool connectionPool;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    public void setConnectionPool(ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

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
                .option(PoolingConnectionFactoryProvider.ACQUIRE_RETRY, 1)
                .option(PoolingConnectionFactoryProvider.INITIAL_SIZE, 0)
                .option(PoolingConnectionFactoryProvider.MAX_SIZE, 10)
                .option(PoolingConnectionFactoryProvider.MAX_IDLE_TIME, Duration.of(30000l, ChronoUnit.MILLIS))
                .option(PoolingConnectionFactoryProvider.MAX_LIFE_TIME, Duration.of(30000l, ChronoUnit.MILLIS))
                .option(PoolingConnectionFactoryProvider.MAX_ACQUIRE_TIME, Duration.of(0, ChronoUnit.MILLIS))
                .option(PoolingConnectionFactoryProvider.MAX_CREATE_CONNECTION_TIME, Duration.of(0, ChronoUnit.MILLIS))
                .option(PoolingConnectionFactoryProvider.VALIDATION_DEPTH, ValidationDepth.LOCAL);

        List<Map<String, String>> options = configuration.getOptions();
        if (options != null && !options.isEmpty()) {
            options.forEach(claimMapper -> {
                String option = claimMapper.get("option");
                String value = claimMapper.get("value");
                builder.option(Option.valueOf(option), ObjectUtils.stringToValue(value));
            });
        }

        connectionPool = (ConnectionPool) ConnectionFactories.get(builder.build());
        LOGGER.info("Connection pool created for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        try {
            LOGGER.info("Disposing connection pool for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
            if (!connectionPool.isDisposed()) {
                connectionPool.disposeLater().subscribe();
                LOGGER.info("Connection pool disposed for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
            }
        } catch (Exception ex) {
            LOGGER.error("An error has occurred while disposing connection pool for database server {} on host {}", configuration.getProtocol(), configuration.getHost(), ex);
        }
    }

    protected void computeMetadata(Map<String, Object> claims) {
        Object metadata = claims.get(configuration.getMetadataAttribute());
        if (metadata == null) {
            return;
        }
        try {
            claims.putAll(objectMapper.readValue(claims.get(configuration.getMetadataAttribute()).toString(), Map.class));
        } catch (Exception e) {
        }
    }
}
