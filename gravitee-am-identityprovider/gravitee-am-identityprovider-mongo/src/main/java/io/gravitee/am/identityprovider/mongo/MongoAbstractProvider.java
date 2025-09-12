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
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.mongodb.provider.impl.MongoConnectionProvider;
import io.gravitee.am.repository.provider.ClientWrapper;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import lombok.Getter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

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
    private DataPlaneRegistry dataPlaneRegistry;

    @Autowired
    private IdentityProvider identityProviderEntity;

    @Autowired
    protected MongoIdentityProviderConfiguration configuration;

    @Autowired
    protected Environment environment;

    protected ClientWrapper<MongoClient> clientWrapper;

    protected MongoClient mongoClient;

    protected static final String JSON_SPECIAL_CHARS = "\\{\\}\\[\\],:";
    protected static final String QUOTE = "\"";
    protected static final String SAFE_QUOTE_REPLACEMENT = "\\\\\\\\\\\\" + QUOTE;

    /**
     * This provider is used to create MongoClient when the main backend is JDBC/R2DBC because in that case the commonConnectionProvider will provide R2DBC ConnectionPool.
     * This is useful if the user want to create a Mongo IDP when the main backend if a RDBMS.
     */
    private final MongoConnectionProvider mongoProvider = new MongoConnectionProvider();

    @Override
    public void afterPropertiesSet() {
        final var systemScope = Scope.fromName(environment.getProperty("repositories.system-cluster", String.class, Scope.MANAGEMENT.getName()));
        if (!(systemScope == Scope.MANAGEMENT || systemScope == Scope.GATEWAY)) {
            throw new IllegalStateException("Unable to initialize Mongo Identity Provider, repositories.system-cluster only accept 'management' or 'gateway'");
        }

        // whatever are the values for the mongo connection settings or the useSystemCluster
        // if DataSource is present, it takes precedence on everything
        if (StringUtils.hasLength(this.configuration.getDatasource()) && this.commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)) {
            this.clientWrapper = this.commonConnectionProvider.getClientWrapperFromDatasource(this.configuration.getDatasource());
            this.mongoClient = this.clientWrapper.getClient();
            return;
        }

        // If the scope is Gateway, we have to use the DataPlane client
        if (StringUtils.hasText(this.identityProviderEntity.getDataPlaneId()) && systemScope == Scope.GATEWAY && configuration.isUseSystemCluster()) {
            final var provider = this.dataPlaneRegistry.getProviderById(this.identityProviderEntity.getDataPlaneId());
            // make sure the DataPlane plugin is a Mongo one
            if (provider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)) {
                this.clientWrapper = provider.getClientWrapper();
                this.mongoClient = this.clientWrapper.getClient();
                return;
            }
        }

        // the data plane client is not the one which has been configured if we land here.
        // use the commonConnectionProvider for system idp, or create dedicated client
        if (this.commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_MONGO)) {
            this.clientWrapper = (this.identityProviderEntity != null && this.identityProviderEntity.isSystem()) || configuration.isUseSystemCluster() ?
                    this.commonConnectionProvider.getClientWrapper() : this.commonConnectionProvider.getClientFromConfiguration(this.configuration);
        } else {
            this.clientWrapper = mongoProvider.getClientFromConfiguration(this.configuration);
        }

        this.mongoClient = this.clientWrapper.getClient();
    }

    protected String getSafeUsername(String username) {
        return getEncodedUsername(username).replace(QUOTE, SAFE_QUOTE_REPLACEMENT);
    }

    protected String getEncodedUsername(String username) {
        return this.configuration.isUsernameCaseSensitive() ? username : username.toLowerCase();
    }
}
