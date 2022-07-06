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
package io.gravitee.am.identityprovider.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.identityprovider.jdbc.configuration.JdbcIdentityProviderConfiguration;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.repository.jdbc.provider.impl.R2DBCConnectionProvider;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.common.service.AbstractService;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Closeable;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JdbcAbstractProvider<T extends LifecycleComponent<T>> extends AbstractService<T>  {

    protected static final Logger LOGGER = LoggerFactory.getLogger(JdbcAbstractProvider.class);

    @Autowired
    protected JdbcIdentityProviderConfiguration configuration;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    public ConnectionProvider commonConnectionProvider;

    @Autowired
    private IdentityProvider identityProviderEntity;

    protected ConnectionFactory connectionPool;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * This provider is used to create ConnectionFactory when the main backend is MongoDB because in that case the commonConnectionProvider will provide MongoDB Client.
     * This is useful if the user want to create a JDBC IDP when the main backend if Mongo.
     */
    private final R2DBCConnectionProvider r2dbcProvider = new R2DBCConnectionProvider();

    public void setConnectionPool(ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (this.commonConnectionProvider.canHandle(ConnectionProvider.BACKEND_TYPE_RDBMS)) {
            this.connectionPool = (ConnectionFactory) (this.identityProviderEntity != null && this.identityProviderEntity.isSystem() ?
                    commonConnectionProvider.getClientWrapper().getClient() :
                    commonConnectionProvider.getClientFromConfiguration(this.configuration).getClient());
        } else {
            this.connectionPool = r2dbcProvider.getClientFromConfiguration(this.configuration).getClient();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        try {
            if (connectionPool instanceof ConnectionPool && !((ConnectionPool)connectionPool).isDisposed()) {
                LOGGER.info("Disposing connection pool for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
                ((ConnectionPool)connectionPool).disposeLater().subscribe();
                LOGGER.info("Connection pool disposed for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
            } else if (connectionPool instanceof Closeable) {
                LOGGER.info("Releasing Connection pool for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
                ((Closeable) connectionPool).close();
                LOGGER.info("Connection pool released for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
            }
        } catch (Exception ex) {
            LOGGER.error("An error has occurred while disposing connection pool for database server {} on host {}", configuration.getProtocol(), configuration.getHost(), ex);
        }
    }

    protected void computeMetadata(Map<String, Object> claims) {
        Object metadata = claims.get(configuration.getMetadataAttribute());
        if (metadata == null) {
            return;
        }
        try {
            claims.putAll(objectMapper.readValue(claims.get(configuration.getMetadataAttribute()).toString(), Map.class));
        } catch (Exception e) {
        }
    }
}
