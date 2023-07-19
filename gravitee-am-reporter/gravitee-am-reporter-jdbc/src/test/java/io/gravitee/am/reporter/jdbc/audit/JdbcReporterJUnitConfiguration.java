/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.reporter.jdbc.audit;

import io.gravitee.am.reporter.jdbc.JdbcReporterConfiguration;
import io.gravitee.am.reporter.jdbc.spring.JdbcReporterSpringConfiguration;
import io.gravitee.am.reporter.jdbc.tool.R2dbcDatabaseContainer;
import io.gravitee.am.repository.jdbc.provider.impl.R2DBCConnectionProvider;
import io.gravitee.am.repository.jdbc.provider.impl.R2DBCPoolWrapper;
import io.gravitee.am.repository.jdbc.provider.template.CustomR2dbcEntityTemplate;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.core.ReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;

import java.util.Optional;

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
@Configuration
public class JdbcReporterJUnitConfiguration extends JdbcReporterSpringConfiguration {

    @Autowired
    protected R2dbcDatabaseContainer dbContainer;

    private ConnectionFactory connectionFactory;

    @Override
    public ConnectionFactory connectionFactory() {
        if (connectionFactory == null) {
            ConnectionFactoryOptions options = dbContainer.getOptions();
            options = ConnectionFactoryOptions.builder()
                    .from(options)
                    .option(DRIVER, "pool")
                    .option(PROTOCOL, (String) options.getValue(DRIVER))
                    .build();
            this.connectionFactory = ConnectionFactories.get(options);
        }
        return this.connectionFactory;
    }

    @Bean
    public JdbcReporterConfiguration reporterConfiguration(R2dbcDatabaseContainer dbContainer) {
        ConnectionFactoryOptions options = dbContainer.getOptions();
        JdbcReporterConfiguration config = new JdbcReporterConfiguration();
        config.setDriver((String) options.getValue(DRIVER));
        config.setDatabase((String) options.getValue(DATABASE));
        config.setHost((String) options.getValue(HOST));
        config.setPort((Integer) options.getValue(PORT));
        config.setUsername((String) options.getValue(USER));
        CharSequence value = (CharSequence) options.getValue(PASSWORD);
        config.setPassword(String.join("", value));

        config.setTableSuffix("junit");
        config.setFlushInterval(1);
        return config;
    }

    @Component
    public class TestContainerR2DBCConnectionProvider extends R2DBCConnectionProvider {
        @Override
        public void afterPropertiesSet() throws Exception {
            // nothing to implement here for TestContainer tests
        }

        @Override
        public ClientWrapper getClientWrapper(String name) {
            return new R2DBCPoolWrapper(null, JdbcReporterJUnitConfiguration.this.connectionFactory());
        }
    }

    @Bean
    public R2dbcDialect dialectDatabase(ConnectionFactory factory) {
        return super.getDialect(factory);
    }

    @Bean
    ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    @Override
    public DatabaseClient databaseClient() {
        ConnectionFactory connectionFactory = connectionFactory();

        return DatabaseClient.builder() //
                .connectionFactory(connectionFactory) //
                .bindMarkers(getDialect(connectionFactory).getBindMarkersFactory()) //
                .build();
    }

    @Override
    public R2dbcEntityTemplate r2dbcEntityTemplate(DatabaseClient databaseClient, ReactiveDataAccessStrategy dataAccessStrategy) {
        return new CustomR2dbcEntityTemplate(databaseClient, dataAccessStrategy);
    }

    @Override
    public R2dbcMappingContext r2dbcMappingContext(Optional<NamingStrategy> namingStrategy, R2dbcCustomConversions r2dbcCustomConversions) {
        R2dbcMappingContext context = new R2dbcMappingContext(namingStrategy.orElse(NamingStrategy.INSTANCE));
        context.setSimpleTypeHolder(r2dbcCustomConversions.getSimpleTypeHolder());

        return context;
    }

    @Override
    public ReactiveDataAccessStrategy reactiveDataAccessStrategy(R2dbcConverter converter) {
        return new DefaultReactiveDataAccessStrategy(getDialect(connectionFactory()), converter);
    }

    @Override
    public MappingR2dbcConverter r2dbcConverter(R2dbcMappingContext mappingContext, R2dbcCustomConversions r2dbcCustomConversions) {
        return new MappingR2dbcConverter(mappingContext, r2dbcCustomConversions);
    }

    @Override
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return new R2dbcCustomConversions(getStoreConversions(), getCustomConverters());
    }
}
