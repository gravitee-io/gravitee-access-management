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
import io.gravitee.am.reporter.jdbc.dialect.DialectHelper;
import io.gravitee.am.reporter.jdbc.dialect.MSSQLDialect;
import io.gravitee.am.reporter.jdbc.dialect.MariaDBDialect;
import io.gravitee.am.reporter.jdbc.dialect.MySQLDialect;
import io.gravitee.am.reporter.jdbc.dialect.PostgresqlDialect;
import io.gravitee.am.repository.jdbc.provider.R2DBCConnectionConfiguration;
import io.gravitee.am.repository.jdbc.provider.impl.R2DBCConnectionProvider;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.core.ReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;

import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class JdbcReporterSpringConfiguration extends AbstractR2dbcConfiguration {

    @Autowired
    private JdbcReporterConfiguration configuration;

    @Autowired
    public ConnectionProvider<ConnectionFactory, R2DBCConnectionConfiguration> connectionFactoryProvider;

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
        return connectionFactoryProvider.getClientWrapper().getClient();
    }

    /*
    Beans here after are provided by the Repository SpringContext through the io.gravitee.am.repository.jdbc.provider.impl.ConnectionFactoryProvider
    in order to reuse the beans otherwise there are LinkageError due to duplicate class definition.
    These LinkageErrors occur only with Java 17, everything is fine with Java 11.
     */

    @Bean
    public R2dbcDialect dialectDatabase(ConnectionFactory factory) {
        return ((R2DBCConnectionProvider)connectionFactoryProvider).dialectDatabase();
    }

    @Bean
    ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return ((R2DBCConnectionProvider)connectionFactoryProvider).reactiveTransactionManager();
    }

    @Override
    public DatabaseClient databaseClient() {
        return ((R2DBCConnectionProvider)connectionFactoryProvider).databaseClient();
    }

    @Override
    public R2dbcEntityTemplate r2dbcEntityTemplate(DatabaseClient databaseClient, ReactiveDataAccessStrategy dataAccessStrategy) {
        return ((R2DBCConnectionProvider)connectionFactoryProvider).r2dbcEntityTemplate();
    }

    @Override
    public R2dbcMappingContext r2dbcMappingContext(Optional<NamingStrategy> namingStrategy, R2dbcCustomConversions r2dbcCustomConversions) {
        return ((R2DBCConnectionProvider)connectionFactoryProvider).r2dbcMappingContext();
    }

    @Override
    public ReactiveDataAccessStrategy reactiveDataAccessStrategy(R2dbcConverter converter) {
        return ((R2DBCConnectionProvider)connectionFactoryProvider).reactiveDataAccessStrategy();
    }

    @Override
    public MappingR2dbcConverter r2dbcConverter(R2dbcMappingContext mappingContext, R2dbcCustomConversions r2dbcCustomConversions) {
        return ((R2DBCConnectionProvider)connectionFactoryProvider).r2dbcConverter();
    }

    @Override
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return ((R2DBCConnectionProvider)connectionFactoryProvider).r2dbcCustomConversions();
    }

}
