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
package io.gravitee.am.repository.mongodb.provider.impl;

import com.mongodb.reactivestreams.client.MongoClient;
import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.mongodb.provider.MongoConnectionConfiguration;
import io.gravitee.am.repository.mongodb.provider.MongoFactory;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;

import static io.gravitee.am.repository.Scope.GATEWAY;
import static io.gravitee.am.repository.Scope.MANAGEMENT;
import static io.gravitee.am.repository.Scope.OAUTH2;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
// Need to name this component to end with Repository on in order to make it injectable
// as the Repository plugin only scan beanName ending with Repository or TransactionManager
@Slf4j
@Component("ConnectionProviderFromRepository")
public class MongoConnectionProvider implements ConnectionProvider<MongoClient, MongoConnectionConfiguration>, InitializingBean {

    @Autowired
    private RepositoriesEnvironment environment;

    @Autowired
    private MongoFactory mongoFactory;

    private ClientWrapper<MongoClient> commonMongoClient;
    private ClientWrapper<MongoClient> oauthMongoClient;
    private ClientWrapper<MongoClient> gatewayMongoClient;

    private boolean notUseMngSettingsForOauth2;
    private boolean notUseGwSettingsForOauth2;
    private boolean notUseMngSettingsForGateway;

    private final Map<String, ClientWrapper<MongoClient>> dsClientWrappers = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        final var useMngSettingsForOauth2 = environment.getProperty(OAUTH2.getRepositoryPropertyKey() + ".use-management-settings", Boolean.class, true);
        final var useGwSettingsForOauth2 = environment.getProperty(OAUTH2.getRepositoryPropertyKey() + ".use-gateway-settings", Boolean.class, false);
        final var useMngSettingsForGateway = environment.getProperty(Scope.GATEWAY.getRepositoryPropertyKey() + ".use-management-settings", Boolean.class, true);
        notUseGwSettingsForOauth2 = !useGwSettingsForOauth2;
        notUseMngSettingsForOauth2 = !useMngSettingsForOauth2;
        notUseMngSettingsForGateway = !useMngSettingsForGateway;
        // create the common client just after the bean Initialization to guaranty the uniqueness
<<<<<<< HEAD
        commonMongoClient =
                new MongoClientWrapper(new MongoFactory(environment, MANAGEMENT.getRepositoryPropertyKey()).getObject(), getDatabaseName(MANAGEMENT));
        if (notUseMngSettingsForGateway) {
            gatewayMongoClient = new MongoClientWrapper(new MongoFactory(environment, GATEWAY.getRepositoryPropertyKey()).getObject(), getDatabaseName(GATEWAY));
        }
        if (notUseMngSettingsForOauth2 && notUseGwSettingsForOauth2) {
            oauthMongoClient = new MongoClientWrapper(new MongoFactory(environment, OAUTH2.getRepositoryPropertyKey()).getObject(), getDatabaseName(OAUTH2));
=======
        commonMongoClient = buildClientWrapper(MANAGEMENT);
        if (notUseMngSettingsForGateway) {
            gatewayMongoClient = buildClientWrapper(GATEWAY);
        }
        if (notUseMngSettingsForOauth2 && notUseGwSettingsForOauth2) {
            oauthMongoClient = buildClientWrapper(OAUTH2);
>>>>>>> c6a36be30 (feat: Add datasource support for mongo clients (#6553))
        }
    }

    @Override
    public ClientWrapper<MongoClient> getClientWrapper() {
        return getClientWrapper(MANAGEMENT.getName());
    }

    @Override
    public ClientWrapper<MongoClient> getClientWrapper(String name) {
        if (OAUTH2.getName().equals(name) && notUseMngSettingsForOauth2) {
            return notUseGwSettingsForOauth2 ? oauthMongoClient : getClientWrapper(GATEWAY.getName());
        } else if (GATEWAY.getName().equals(name) && notUseMngSettingsForGateway) {
            return gatewayMongoClient;
        } else {
            return commonMongoClient;
        }
    }

    @Override
    public ClientWrapper<MongoClient> getClientFromConfiguration(MongoConnectionConfiguration configuration) {
<<<<<<< HEAD
        return new MongoClientWrapper(MongoFactory.createClient(configuration), configuration.getDatabase());
    }

    @Override
    public ClientWrapper<MongoClient> getClientWrapperFromPrefix(String prefix) {
        if(GATEWAY.getRepositoryPropertyKey().equalsIgnoreCase(prefix)){
            if (notUseMngSettingsForGateway) {
                return gatewayMongoClient;
            } else {
                return commonMongoClient;
            }
        } else {
            return new MongoClientWrapper(new MongoFactory(environment, prefix).getObject(), getDatabaseName(prefix));
        }
    }

    private String getDatabaseName(Scope scope) {
        boolean useManagementSettings = environment.getProperty(scope.getRepositoryPropertyKey() + ".use-management-settings", Boolean.class, true);
        String propertyPrefix = useManagementSettings ? Scope.MANAGEMENT.getRepositoryPropertyKey() : scope.getRepositoryPropertyKey();
        return getDatabaseName(propertyPrefix);
    }

    private String getDatabaseName(String prefix) {
        String uri = environment.getProperty(prefix + ".mongodb.uri", "");
        if (!uri.isEmpty()) {
            final String path = URI.create(uri).getPath();
            if (path != null && path.length() > 1) {
                return path.substring(1);
            }
        }
        return environment.getProperty(prefix + ".mongodb.dbname", "gravitee-am");
=======
        return new MongoClientWrapper(MongoFactoryImpl.createClient(configuration));
    }

    @Override
    public ClientWrapper<MongoClient> getClientWrapperFromDatasource(String datasourceId, String propertyPrefix) {
        log.debug("Using datasource {} with property prefix {}", datasourceId, propertyPrefix);
        return this.dsClientWrappers.computeIfAbsent(datasourceId, k ->
                new MongoClientWrapper(mongoFactory.getObject(propertyPrefix), () -> {
                    log.debug("Cleaning up datasource {}", datasourceId);
                    this.dsClientWrappers.remove(datasourceId);
                }));
>>>>>>> c6a36be30 (feat: Add datasource support for mongo clients (#6553))
    }

    @Override
    public ConnectionProvider stop() throws Exception {
        if (commonMongoClient != null) {
            ((MongoClientWrapper) commonMongoClient).shutdown();
        }
        if (oauthMongoClient != null) {
            ((MongoClientWrapper) oauthMongoClient).shutdown();
        }
        if (gatewayMongoClient != null) {
            ((MongoClientWrapper) gatewayMongoClient).shutdown();
        }
        return this;
    }

    @Override
    public boolean canHandle(String backendType) {
        return BACKEND_TYPE_MONGO.equals(backendType);
    }

    private MongoClientWrapper buildClientWrapper(Scope scope) {
        return new MongoClientWrapper(mongoFactory.getObject(scope.getRepositoryPropertyKey() + ".mongodb."));
    }
}
