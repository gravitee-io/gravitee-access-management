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
package io.gravitee.am.repository.jdbc.management;

import io.gravitee.am.repository.jdbc.common.AbstractRepositoryConfiguration;
import io.gravitee.am.repository.jdbc.common.DatabaseUrlProvider;
import io.gravitee.am.repository.jdbc.common.R2dbcDatabaseContainer;
import io.gravitee.am.repository.jdbc.common.dialect.DatabaseDialectHelper;
import io.gravitee.am.repository.jdbc.exceptions.RepositoryInitializationException;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.util.Optional;

import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@EnableR2dbcRepositories
@ComponentScan({"io.gravitee.am.repository.jdbc.management.api", "io.gravitee.am.repository.jdbc.common"})
public class ManagementRepositoryTestConfiguration extends AbstractRepositoryConfiguration {

    @Autowired
    private DatabaseUrlProvider provider;

    @Override
    public void afterPropertiesSet() throws Exception {

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
            return instantiateDialectHelper("PostgresqlHelper", dialect);
        }
        if (provider.getDatabaseType().equals("mysql")) {
            return instantiateDialectHelper("MySqlHelper", dialect);
        }
        if (provider.getDatabaseType().equals("mariadb")) {
            return instantiateDialectHelper("MariadbHelper", dialect);
        }
        if (provider.getDatabaseType().equals("mssql")) {
            return instantiateDialectHelper("MsSqlHelper", dialect);
        }
        throw new RepositoryInitializationException("Unsupported driver " + provider.getDatabaseType());
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
