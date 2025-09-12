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

import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.repository.jdbc.provider.R2DBCConnectionConfiguration;
import io.gravitee.am.repository.jdbc.provider.R2DBCSpringBeanAccessor;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.core.ReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;

import static io.gravitee.am.repository.Scope.GATEWAY;
import static io.gravitee.am.repository.Scope.MANAGEMENT;
import static io.gravitee.am.repository.Scope.OAUTH2;
import static io.gravitee.am.repository.Scope.RATE_LIMIT;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
// Need to name this component to end with Repository on in order to make it injectable
// as the Repository plugin only scan beanName ending with Repository or TransactionManager
@Component("ConnectionProviderFromRepository")
@org.springframework.context.annotation.Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class R2DBCConnectionProvider implements ConnectionProvider<ConnectionFactory, R2DBCConnectionConfiguration>, InitializingBean, R2DBCSpringBeanAccessor {

    @Autowired
    private RepositoriesEnvironment environment;

    private ClientWrapper<ConnectionFactory> commonConnectionFactory;

    private ClientWrapper<ConnectionFactory> oauthConnectionFactory;

    private ClientWrapper<ConnectionFactory> gatewayConnectionFactory;

    private ClientWrapper<ConnectionFactory> ratelimitConnectionFactory;

    @Autowired
    @Lazy
    private DatabaseClient dbClient;

    @Autowired
    @Lazy
    private R2dbcCustomConversions customConversions;

    @Autowired
    @Lazy
    private R2dbcDialect dialect;

    @Autowired
    @Lazy
    private R2dbcEntityTemplate entityTemplate;

    @Autowired
    @Lazy
    private R2dbcMappingContext mappingContext;

    @Autowired
    @Lazy
    private ReactiveDataAccessStrategy dataAccessStrategy;

    @Autowired
    @Lazy
    private MappingR2dbcConverter mappingConverter;

    @Autowired
    @Lazy
    private ReactiveTransactionManager transactionManager;

    private boolean notUseMngSettingsForOauth2;
    private boolean notUseGwSettingsForOauth2;
    private boolean notUseMngSettingsForGateway;

    private boolean notUseMngSettingsForRateLimit;
    private boolean notUseGwSettingsForRateLimit;

    @Override
    public DatabaseClient databaseClient() {
        return this.dbClient;
    }

    @Override
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return this.customConversions;
    }

    @Override
    public R2dbcDialect dialectDatabase() {
        return this.dialect;
    }

    @Override
    public R2dbcEntityTemplate r2dbcEntityTemplate() {
        return entityTemplate;
    }

    @Override
    public R2dbcMappingContext r2dbcMappingContext() {
        return this.mappingContext;
    }

    @Override
    public ReactiveDataAccessStrategy reactiveDataAccessStrategy() {
        return this.dataAccessStrategy;
    }

    @Override
    public MappingR2dbcConverter r2dbcConverter() {
        return this.mappingConverter;
    }

    @Override
    public ReactiveTransactionManager reactiveTransactionManager() {
        return this.transactionManager;
    }

    @Override
    public ClientWrapper<ConnectionFactory> getClientWrapper() {
        return getClientWrapper(MANAGEMENT.getName());
    }

    @Override
    public ClientWrapper getClientWrapper(String name) {
        if (OAUTH2.getName().equals(name) && notUseMngSettingsForOauth2) {
            return notUseGwSettingsForOauth2 ? oauthConnectionFactory : getClientWrapper(GATEWAY.getName());
        } else if (RATE_LIMIT.getName().equals(name) && notUseMngSettingsForRateLimit) {
            return notUseGwSettingsForRateLimit ? ratelimitConnectionFactory : getClientWrapper(GATEWAY.getName());
        } else if (GATEWAY.getName().equals(name) && notUseMngSettingsForGateway) {
            return gatewayConnectionFactory;
        } else {
            return commonConnectionFactory;
        }
    }

    @Override
    public ClientWrapper<ConnectionFactory> getClientFromConfiguration(R2DBCConnectionConfiguration configuration) {
        return new R2DBCPoolWrapper(configuration, ConnectionFactoryProvider.createClient(configuration));
    }

    @Override
    public ClientWrapper<ConnectionFactory> getClientWrapperFromPrefix(String prefix) {
        if (GATEWAY.getRepositoryPropertyKey().equals(prefix)) {
            if (notUseMngSettingsForGateway) {
                return gatewayConnectionFactory;
            } else {
                return commonConnectionFactory;
            }
        }
        // mAPI, create a connection pool for each DP
        return new R2DBCPoolWrapper(new ConnectionFactoryProvider(environment, prefix));
    }

    @Override
    public ConnectionProvider stop() throws Exception {
        if (commonConnectionFactory != null) {
            ((R2DBCPoolWrapper) commonConnectionFactory).shutdown();
        }
        if (oauthConnectionFactory != null) {
            ((R2DBCPoolWrapper) oauthConnectionFactory).shutdown();
        }
        if (gatewayConnectionFactory != null) {
            ((R2DBCPoolWrapper) gatewayConnectionFactory).shutdown();
        }
        if (ratelimitConnectionFactory != null) {
            ((R2DBCPoolWrapper) ratelimitConnectionFactory).shutdown();
        }
        return this;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        final var useMngSettingsForOauth2 = environment.getProperty(OAUTH2.getRepositoryPropertyKey() +".use-management-settings", Boolean.class, true);
        final var useGwSettingsForOauth2 = environment.getProperty(OAUTH2.getRepositoryPropertyKey() + ".use-gateway-settings", Boolean.class, false);
        final var useMngSettingsForGateway = environment.getProperty(GATEWAY.getRepositoryPropertyKey() +".use-management-settings", Boolean.class, true);

        final var useMngSettingsForRateLimit = environment.getProperty(RATE_LIMIT.getRepositoryPropertyKey() +".use-management-settings", Boolean.class, true);
        final var useGwSettingsForRateLimit = environment.getProperty(RATE_LIMIT.getRepositoryPropertyKey() + ".use-gateway-settings", Boolean.class, false);

        notUseGwSettingsForOauth2 = !useGwSettingsForOauth2;
        notUseMngSettingsForOauth2 = !useMngSettingsForOauth2;
        notUseMngSettingsForGateway = !useMngSettingsForGateway;

        notUseGwSettingsForRateLimit = !useGwSettingsForRateLimit;
        notUseMngSettingsForRateLimit = !useMngSettingsForRateLimit;

        // create the connection pool just after the bean Initialization to guaranty the uniqueness
        commonConnectionFactory = new R2DBCPoolWrapper(new ConnectionFactoryProvider(environment, MANAGEMENT.getRepositoryPropertyKey()));
        if (notUseMngSettingsForGateway) {
            gatewayConnectionFactory = new R2DBCPoolWrapper(new ConnectionFactoryProvider(environment, GATEWAY.getRepositoryPropertyKey()));
        }
        if (notUseMngSettingsForOauth2 && notUseGwSettingsForOauth2) {
            oauthConnectionFactory = new R2DBCPoolWrapper(new ConnectionFactoryProvider(environment, OAUTH2.getRepositoryPropertyKey()));
        }
        if (notUseMngSettingsForRateLimit && notUseGwSettingsForRateLimit) {
            ratelimitConnectionFactory = new R2DBCPoolWrapper(new ConnectionFactoryProvider(environment, RATE_LIMIT.getRepositoryPropertyKey()));
        }
    }

    @Override
    public boolean canHandle(String backendType) {
        return BACKEND_TYPE_RDBMS.equals(backendType);
    }
}
