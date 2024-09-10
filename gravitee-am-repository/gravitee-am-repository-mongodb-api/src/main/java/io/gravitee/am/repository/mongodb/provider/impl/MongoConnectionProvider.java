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
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static io.gravitee.am.repository.Scope.GATEWAY;
import static io.gravitee.am.repository.Scope.MANAGEMENT;
import static io.gravitee.am.repository.Scope.OAUTH2;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
// Need to name this component to end with Repository on in order to make it injectable
// as the Repository plugin only scan beanName ending with Repository or TransactionManager
@Component("ConnectionProviderFromRepository")
public class MongoConnectionProvider implements ConnectionProvider<MongoClient, MongoConnectionConfiguration>, InitializingBean {

    @Autowired
    private RepositoriesEnvironment environment;

    private ClientWrapper<MongoClient> commonMongoClient;
    private ClientWrapper<MongoClient> oauthMongoClient;
    private ClientWrapper<MongoClient> gatewayMongoClient;

    private boolean notUseMngSettingsForOauth2;
    private boolean notUseMngSettingsForGateway;

    @Override
    public void afterPropertiesSet() throws Exception {
        final var useMngSettingsForOauth2 = environment.getProperty(OAUTH2.getRepositoryPropertyKey() + ".use-management-settings", Boolean.class, true);
        final var notUseGatewaySettings = environment.getProperty(Scope.GATEWAY.getRepositoryPropertyKey() + ".use-management-settings", Boolean.class, true);
        notUseMngSettingsForOauth2 = !useMngSettingsForOauth2;
        notUseMngSettingsForGateway = !notUseGatewaySettings;
        // create the common client just after the bean Initialization to guaranty the uniqueness
        commonMongoClient = new MongoClientWrapper(new MongoFactory(environment, MANAGEMENT.getRepositoryPropertyKey()).getObject());
        if (notUseMngSettingsForOauth2) {
            oauthMongoClient = new MongoClientWrapper(new MongoFactory(environment, OAUTH2.getRepositoryPropertyKey()).getObject());
        }
        if (notUseMngSettingsForGateway) {
            gatewayMongoClient = new MongoClientWrapper(new MongoFactory(environment, GATEWAY.getRepositoryPropertyKey()).getObject());
        }
    }

    @Override
    public ClientWrapper<MongoClient> getClientWrapper() {
        return getClientWrapper(MANAGEMENT.getName());
    }

    @Override
    public ClientWrapper getClientWrapper(String name) {
        if (OAUTH2.getName().equals(name) && notUseMngSettingsForOauth2) {
            return oauthMongoClient;
        } else if (GATEWAY.getName().equals(name) && notUseMngSettingsForGateway) {
            return gatewayMongoClient;
        } else {
            return commonMongoClient;
        }
    }

    @Override
    public ClientWrapper<MongoClient> getClientFromConfiguration(MongoConnectionConfiguration configuration) {
        return new MongoClientWrapper(MongoFactory.createClient(configuration));
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
}
