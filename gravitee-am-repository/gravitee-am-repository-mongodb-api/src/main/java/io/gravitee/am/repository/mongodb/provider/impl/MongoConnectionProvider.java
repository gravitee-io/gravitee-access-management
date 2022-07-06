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
import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.mongodb.provider.MongoConnectionConfiguration;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
// Need to name this component to end with Repository on in order to make it injectable
// as the Repository plugin only scan beanName ending with Repository or TransactionManager
@Component("ConnectionProviderFromRepository")
public class MongoConnectionProvider implements ConnectionProvider<MongoClient, MongoConnectionConfiguration>, InitializingBean {

    @Value("${oauth2.use-management-settings:true}")
    private boolean useManagementSettings;

    @Autowired
    private Environment environment;

    private ClientWrapper<MongoClient> commonMongoClient;

    private ClientWrapper<MongoClient> oauthMongoClient;

    @Override
    public void afterPropertiesSet() throws Exception {
        // create the common client just after the bean Initialization to guaranty the uniqueness
        this.commonMongoClient = new MongoClientWrapper(new MongoFactory(this.environment, Scope.MANAGEMENT.getName()).getObject());
        if (!useManagementSettings) {
            this.oauthMongoClient = new MongoClientWrapper(new MongoFactory(this.environment, Scope.OAUTH2.getName()).getObject());
        }
    }

    @Override
    public ClientWrapper<MongoClient> getClientWrapper() {
        return getClientWrapper(Scope.MANAGEMENT.getName());
    }

    @Override
    public ClientWrapper getClientWrapper(String name) {
        return Scope.OAUTH2.getName().equals(name) && !this.useManagementSettings ? this.oauthMongoClient : this.commonMongoClient;
    }

    @Override
    public ClientWrapper<MongoClient> getClientFromConfiguration(MongoConnectionConfiguration configuration) {
        return new MongoClientWrapper(MongoFactory.createClient(configuration));
    }

    @Override
    public ConnectionProvider stop() throws Exception {
        if (this.commonMongoClient != null) {
            ((MongoClientWrapper)this.commonMongoClient).shutdown();
        }
        if (this.oauthMongoClient != null) {
            ((MongoClientWrapper)this.oauthMongoClient).shutdown();
        }
        return this;
    }

    @Override
    public boolean canHandle(String backendType) {
        return BACKEND_TYPE_MONGO.equals(backendType);
    }
}
