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
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.common.service.AbstractService;
import org.davidmoten.rx.jdbc.Database;
import org.davidmoten.rx.jdbc.pool.DatabaseType;
import org.davidmoten.rx.jdbc.pool.NonBlockingConnectionPool;
import org.davidmoten.rx.jdbc.pool.Pools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class JdbcAbstractProvider<T extends LifecycleComponent> extends AbstractService<T> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(JdbcAbstractProvider.class);

    @Autowired
    protected JdbcIdentityProviderConfiguration configuration;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected Database db;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOGGER.info("Initializing connection pool for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
        // JDBC URL
        final String url = new StringBuilder("jdbc:")
                .append(configuration.getProtocol()).append("://")
                .append(configuration.getHost())
                .append(configuration.getPort() != null ? ":" + configuration.getPort() : "")
                .append("/")
                .append(configuration.getDatabase())
                .toString();

        // JDBC Properties
        Properties props = new Properties();
        props.setProperty("user", configuration.getUser());
        if (configuration.getPassword() != null) {
            props.setProperty("password", configuration.getPassword());
        }
        List<Map<String, String>> options = configuration.getOptions();
        if (options != null && !options.isEmpty()) {
            options.forEach(claimMapper -> {
                String option = claimMapper.get("option");
                String value = claimMapper.get("value");
                props.setProperty(option, value);
            });
        }

        // LOAD DRIVER
        Class.forName(getDriverClassName());

        // BUILD THE POOL
        NonBlockingConnectionPool pool = Pools
                .nonBlocking()
                // the jdbc url of the connections to be placed in the pool
                .url(url)
                // the jdbc properties
                .properties(props)
                // an unused connection will be closed after thirty minutes
                .maxIdleTime(30, TimeUnit.MINUTES)
                // connections are checked for healthiness on checkout if the connection
                // has been idle for at least 5 seconds
                .healthCheck(getDatabaseType())
                .idleTimeBeforeHealthCheck(5, TimeUnit.SECONDS)
                // the maximum number of connections in the pool
                .maxPoolSize(5)
                .build();

        // BUILD THE DB
        db = Database.from(pool, () -> pool.close());
        LOGGER.info("Connection pool created for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        try {
            LOGGER.info("Disposing connection pool for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
            db.close();
            LOGGER.info("Connection pool disposed for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
        } catch (Exception ex) {
            LOGGER.error("An error has occurred while disposing connection pool for database server {} on host {}", configuration.getProtocol(), configuration.getHost(), ex);
        }
    }

    public void setDb(Database db) {
        this.db = db;
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

    private String getDriverClassName() {
        switch(configuration.getProtocol()) {
            case "postgresql":
                return "org.postgresql.Driver";
            case "mysql":
                return "com.mysql.jdbc.Driver";
            case "mariadb":
                return "org.mariadb.jdbc.Driver";
            case "sqlserver":
                return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default:
                throw new IllegalStateException("No suitable driver name found for : " + configuration.getProtocol());
        }
    }

    private DatabaseType getDatabaseType() {
        switch(configuration.getProtocol()) {
            case "postgresql":
                return DatabaseType.POSTGRES;
            case "mysql":
            case "mariadb":
                return DatabaseType.MYSQL;
            case "sqlserver":
                return DatabaseType.SQL_SERVER;
            default:
                throw new IllegalStateException("No suitable driver name found for : " + configuration.getProtocol());
        }
    }
}
