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
package io.gravitee.am.identityprovider.mongo;

import com.mongodb.reactivestreams.client.MongoClient;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.repository.mongodb.provider.impl.MongoConnectionProvider;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class MongoAbstractProvider implements InitializingBean {

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    private ConnectionProvider commonConnectionProvider;

    @Autowired
    private IdentityProvider identityProviderEntity;

    @Autowired
    protected MongoIdentityProviderConfiguration configuration;

    protected ClientWrapper<MongoClient> clientWrapper;

    protected MongoClient mongoClient;

    /**
     * This provider is used to create MongoClient when the main backend is JDBC/R2DBC because in that case the commonConnectionProvider will provide R2DBC ConnectionPool.
     * This is useful if the user want to create a Mongo IDP when the main backend if a RDBMS.
     */
    private final MongoConnectionProvider mongoProvider = new MongoConnectionProvider();

    @Override
    public void afterPropertiesSet() {
        if (this.commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)) {
            this.clientWrapper = this.identityProviderEntity != null && this.identityProviderEntity.isSystem() ?
                    this.commonConnectionProvider.getClientWrapper() :
                    this.commonConnectionProvider.getClientFromConfiguration(this.configuration);
        } else {
            this.clientWrapper = mongoProvider.getClientFromConfiguration(this.configuration);
        }
        this.mongoClient = this.clientWrapper.getClient();
    }
}
