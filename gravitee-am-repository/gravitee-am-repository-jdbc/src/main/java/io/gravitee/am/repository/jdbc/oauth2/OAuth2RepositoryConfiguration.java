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
package io.gravitee.am.repository.jdbc.oauth2;

import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.jdbc.common.AbstractRepositoryConfiguration;
import io.gravitee.am.repository.jdbc.common.dialect.DatabaseDialectHelper;
import io.gravitee.am.repository.jdbc.exceptions.RepositoryInitializationException;
import io.gravitee.am.repository.jdbc.oauth2.api.JdbcAccessTokenRepository;
import io.gravitee.am.repository.jdbc.oauth2.api.JdbcRefreshTokenRepository;
import io.gravitee.am.repository.jdbc.oauth2.api.JdbcTokenRepository;
import io.gravitee.am.repository.jdbc.provider.R2DBCConnectionConfiguration;
import io.gravitee.am.repository.jdbc.provider.impl.R2DBCPoolWrapper;
import io.gravitee.am.repository.oauth2.api.BackwardCompatibleTokenRepository;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@ComponentScan({
        "io.gravitee.am.repository.jdbc.oauth2",
        "io.gravitee.am.repository.jdbc.oauth2.oidc"
})
@EnableR2dbcRepositories
public class OAuth2RepositoryConfiguration extends AbstractRepositoryConfiguration {

    public static final String LIQUIBASE_FILE = "liquibase/oauth-master.yml";

    @Autowired
    public ConnectionProvider<ConnectionFactory, R2DBCConnectionConfiguration> connectionFactoryProvider;

    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
        return getOauth2Pool().getClient();
    }

    private R2DBCPoolWrapper getOauth2Pool() {
        return (R2DBCPoolWrapper) connectionFactoryProvider.getClientWrapper(Scope.OAUTH2.getName());
    }

    protected String getDriver() {
        return getOauth2Pool().getJdbcDriver();
    }

    @Override
    protected Optional<Converter> jsonConverter() {
        if (getDriver().equals("postgresql")) {
            return instantiatePostgresJsonConverter();
        }
        return Optional.empty();
    }

    @Override
    @Bean
    public DatabaseDialectHelper databaseDialectHelper(R2dbcDialect dialect) {
        String collation = environment.getProperty(Scope.MANAGEMENT.getRepositoryPropertyKey() + ".jdbc.collation");
        if (getDriver().equals(POSTGRESQL_DRIVER)) {
            return instantiateDialectHelper(DIALECT_HELPER_POSTGRESQL, dialect, collation);
        }
        if (getDriver().equals(MYSQL_DRIVER)) {
            return instantiateDialectHelper(DIALECT_HELPER_MYSQL, dialect, collation);
        }
        if (getDriver().equals(MARIADB_DRIVER)) {
            return instantiateDialectHelper(DIALECT_HELPER_MARIADB, dialect, collation);
        }
        if (getDriver().equals(SQLSERVER_DRIVER)) {
            return instantiateDialectHelper(DIALECT_HELPER_SQLSERVER, dialect, collation);
        }
        throw new RepositoryInitializationException("Unsupported driver " + getDriver());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initializeDatabaseSchema(getOauth2Pool(), environment, Scope.OAUTH2.getRepositoryPropertyKey() + ".jdbc.", LIQUIBASE_FILE);
    }

    @Bean
    public TokenRepository tokenRepository(JdbcTokenRepository tokenRepository,
                                           JdbcAccessTokenRepository accessTokenRepository,
                                           JdbcRefreshTokenRepository refreshTokenRepository,
                                           @Value("${legacy.repositories.useLegacyTokenRepositories:true}") boolean useLegacyTokenRepositories) {
        return new BackwardCompatibleTokenRepository(
                tokenRepository,
                accessTokenRepository,
                refreshTokenRepository,
                useLegacyTokenRepositories);
    }



}
