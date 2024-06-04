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
import io.gravitee.am.identityprovider.jdbc.utils.ParametersUtils;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.repository.jdbc.provider.impl.R2DBCConnectionProvider;
import io.gravitee.am.repository.provider.ConnectionProvider;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.common.service.AbstractService;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
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
            if (connectionPool instanceof ConnectionPool connection && !connection.isDisposed()) {
                LOGGER.info("Disposing connection pool for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
                connection.disposeLater().subscribe();
                LOGGER.info("Connection pool disposed for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
            } else if (connectionPool instanceof Closeable closeable) {
                LOGGER.info("Releasing Connection pool for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
                closeable.close();
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
            LOGGER.warn("Error on compute metadata", e);
        }
    }

    protected final Flowable<Result> query(String sql, Object... args) {
        return Single.fromPublisher(connectionPool.create())
                .toFlowable()
                .flatMap(connection ->
                        query(connection, sql, args)
                                .doFinally(() -> Completable.fromPublisher(connection.close()).subscribe()));
    }

    /**
     * !!WARNING!! This method shouldn't be used in subclass.
     *
     * This method execute the sql query with provided arguments on the connection present a first argument.
     * This method shouldn't be used by subclasses of JdbcAbstractProvider in favor of {@link #query(String, Object...)} that will
     * automatically close/release the connection.
     *
     * Currently, only the {@link io.gravitee.am.identityprovider.jdbc.user.JdbcUserProvider} class uses this method to initialize
     * the IDP Schema into the RDBMS.
     *
     * @param connection
     * @param sql
     * @param args
     * @return
     */
    protected final Flowable<Result> query(Connection connection, String sql, Object... args) {
        Statement statement = connection.createStatement(sql);
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            bind(statement, i, arg, arg != null ? arg.getClass() : String.class);
        }
        return Flowable.fromPublisher(statement.execute());
    }

    protected final void bind(Statement statement, int index, Object value, Class type) {
        if (value != null) {
            statement.bind(index, value);
        } else {
            statement.bindNull(index, type);
        }
    }

    protected final String getIndexParameter(int index, String field) {
        return ParametersUtils.getIndexParameter(configuration.getProtocol(), index, field);
    }

}
