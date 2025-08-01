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
package io.gravitee.am.dataplane.jdbc.spring;

import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.dataplane.jdbc.dialect.DatabaseDialectHelper;
import io.gravitee.am.dataplane.jdbc.exceptions.RepositoryInitializationException;
import io.gravitee.am.repository.jdbc.provider.impl.R2DBCPoolWrapper;
import io.gravitee.am.repository.jdbc.provider.utils.TlsOptionsHelper;
import io.r2dbc.spi.ConnectionFactory;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractRepositoryConfiguration extends AbstractR2dbcConfiguration implements InitializingBean {
    public static final String POSTGRESQL_DRIVER = "postgresql";
    public static final String MYSQL_DRIVER = "mysql";
    public static final String MARIADB_DRIVER = "mariadb";
    public static final String SQLSERVER_DRIVER = "sqlserver";

    public static final String DIALECT_HELPER_POSTGRESQL = "PostgresqlHelper";
    public static final String DIALECT_HELPER_MYSQL = "MySqlHelper";
    public static final String DIALECT_HELPER_MARIADB = "MariadbHelper";
    public static final String DIALECT_HELPER_SQLSERVER = "MsSqlHelper";

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    protected RepositoriesEnvironment environment;

    protected abstract Optional<Converter> jsonConverter();

    @Bean
    public abstract DatabaseDialectHelper databaseDialectHelper(R2dbcDialect dialect);

    protected abstract String getDriver();

    @Bean
    public R2dbcDialect dialectDatabase(ConnectionFactory factory) {
        return super.getDialect(factory);
    }

    @Override
    protected List<Object> getCustomConverters() {

        List<Object> converterList = new ArrayList<>();
        converterList.add(new Converter<BitSet, Boolean>() {
            @Override
            public Boolean convert(BitSet bitSet) {
                Boolean result = null;
                if (bitSet != null) {
                    result = !bitSet.isEmpty() && bitSet.get(0);
                }
                return result;
            }
        });

        if (jsonConverter().isPresent()) {
            converterList.add(jsonConverter().get());
        }

        return converterList;
    }

    @Bean
    ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    protected final void initializeDatabaseSchema(R2DBCPoolWrapper poolWrapper, RepositoriesEnvironment environment, String prefix) throws Exception {
        Boolean enabled = environment.getProperty("liquibase.enabled", Boolean.class, true);
        if (enabled) {
            final String jdbcPort = poolWrapper.getJdbcPort();
            final String jdbcUrl = new StringBuilder("jdbc:")
                    .append(poolWrapper.getJdbcDriver())
                    .append("://")
                    .append(poolWrapper.getJdbcHostname())
                    .append(jdbcPort != null ? ":": "")
                    .append(jdbcPort != null ? jdbcPort : "")
                    .append(SQLSERVER_DRIVER.equals(getDriver()) ? ";databaseName=" : "/")
                    .append(poolWrapper.getJdbcDatabase())
                    .toString();

            try (Connection connection = DriverManager.getConnection(TlsOptionsHelper.setSSLOptions(jdbcUrl, environment, prefix, poolWrapper.getJdbcDriver()),
                    poolWrapper.getJdbcUsername(),
                    poolWrapper.getJdbcPassword())) {
                LOGGER.debug("Running Liquibase on {}", jdbcUrl);
                runLiquibase(connection);
            }
        }
    }

    protected final void runLiquibase(Connection connection) {
        System.setProperty("liquibase.databaseChangeLogTableName", "dp_databasechangelog");
        System.setProperty("liquibase.databaseChangeLogLockTableName", "dp_databasechangeloglock");

        LOGGER.info("Start Liquibase execution...");
        try (ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor(this.getClass().getClassLoader())) {
            final Liquibase liquibase = new Liquibase("liquibase/dp-master.yml", resourceAccessor, new JdbcConnection(connection));
            liquibase.update((Contexts) null);
        } catch (Exception ex) {
            LOGGER.error("Failed to set up database: ", ex);
        }
    }

    public static Optional<Converter> instantiatePostgresJsonConverter() {
        try {
            Class converter = Class.forName("io.gravitee.am.dataplane.jdbc.common.PostgresJsonConverter");
            return Optional.ofNullable((Converter) converter.getConstructor().newInstance());
        } catch (Exception e) {
            throw new RepositoryInitializationException("Unable to instantiate the Converter for the Postgresql Json type", e);
        }
    }

    public static DatabaseDialectHelper instantiateDialectHelper(String name, R2dbcDialect dialect, String collation) {
        try {
            Class converter = Class.forName("io.gravitee.am.dataplane.jdbc.dialect."+name);
            return (DatabaseDialectHelper) converter.getConstructor(R2dbcDialect.class, String.class).newInstance(dialect, collation);
        } catch (Exception e) {
            throw new RepositoryInitializationException("Unable to instantiate the DatabaseDialectHelper", e);
        }
    }
}
