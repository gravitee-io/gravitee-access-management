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
package io.gravitee.am.identityprovider.jdbc.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.identityprovider.jdbc.configuration.JdbcIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.jdbc.user.spring.JdbcUserProviderConfiguration;
import io.gravitee.am.identityprovider.jdbc.utils.ColumnMapRowMapper;
import io.gravitee.am.identityprovider.jdbc.utils.ObjectUtils;
import io.gravitee.am.identityprovider.jdbc.utils.ParametersUtils;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.common.service.AbstractService;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.*;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(JdbcUserProviderConfiguration.class)
public class JdbcUserProvider extends AbstractService<UserProvider> implements UserProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcUserProvider.class);

    @Autowired
    private JdbcIdentityProviderConfiguration configuration;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ConnectionPool connectionPool;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOGGER.info("Initializing connection pool for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
        Builder builder = ConnectionFactoryOptions.builder()
                .option(DRIVER, "pool")
                .option(PROTOCOL, configuration.getProtocol())
                .option(HOST, configuration.getHost())
                .option(PORT, configuration.getPort())
                .option(USER, configuration.getUser())
                .option(DATABASE, configuration.getDatabase());

        if (configuration.getPassword() != null) {
            builder.option(PASSWORD, configuration.getPassword());
        }

        List<Map<String, String>> options = configuration.getOptions();
        if (options != null && !options.isEmpty()) {
            options.forEach(claimMapper -> {
                String option = claimMapper.get("option");
                String value = claimMapper.get("value");
                builder.option(Option.valueOf(option), ObjectUtils.stringToValue(value));
            });
        }

        connectionPool = (ConnectionPool) ConnectionFactories.get(builder.build());
        LOGGER.info("Connection pool created for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        try {
            LOGGER.info("Disposing connection pool for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
            if (!connectionPool.isDisposed()) {
                connectionPool.dispose();
                LOGGER.info("Connection pool disposed for database server {} on host {}", configuration.getProtocol(), configuration.getHost());
            }
        } catch (Exception ex) {
            LOGGER.error("An error has occurred while disposing connection pool for database server {} on host {}", configuration.getProtocol(), configuration.getHost(), ex);
        }
    }

    @Override
    public Maybe<User> findByUsername(String username) {
        return selectUserByUsername(username)
                .map(result -> createUser(result));
    }

    @Override
    public Single<User> create(User user) {
        // set technical id
        ((DefaultUser)user).setId(user.getId() != null ? user.getId() : RandomString.generate());

        return selectUserByUsername(user.getUsername())
                .isEmpty()
                .flatMap(isEmpty -> {
                    if (!isEmpty) {
                        return Single.error(new UserAlreadyExistsException(user.getUsername()));
                    } else {
                        final String sql = String.format("INSERT INTO %s (%s, %s, %s, %s, %s) VALUES (%s, %s, %s, %s, %s)",
                                configuration.getUsersTable(),
                                configuration.getIdentifierAttribute(),
                                configuration.getUsernameAttribute(),
                                configuration.getPasswordAttribute(),
                                configuration.getEmailAttribute(),
                                configuration.getMetadataAttribute(),
                                getIndexParameter(1, "id"),
                                getIndexParameter(2, "username"),
                                getIndexParameter(3, "password"),
                                getIndexParameter(4, "email"),
                                getIndexParameter(5, "metadata"));

                        Object[] args = new Object[5];
                        args[0] = user.getId();
                        args[1] = user.getUsername();
                        args[2] = user.getCredentials() != null ? passwordEncoder.encode(user.getCredentials()) : null;
                        args[3] = user.getEmail();
                        args[4] = user.getAdditionalInformation() != null ? objectMapper.writeValueAsString(user.getAdditionalInformation()) : null;

                        return query(sql, args)
                                .flatMap(Result::getRowsUpdated)
                                .first(0)
                                .map(result -> user);
                    }
                });
    }

    @Override
    public Single<User> update(String id, User updateUser) {
        final String sql;
        final Object[] args;
        final String metadata = convert(updateUser.getAdditionalInformation());

        if (updateUser.getCredentials() != null) {
            args = new Object[3];
            sql = String.format("UPDATE %s SET %s = %s, %s = %s WHERE id = %s",
                    configuration.getUsersTable(),
                    configuration.getPasswordAttribute(),
                    getIndexParameter(1, "password"),
                    configuration.getMetadataAttribute(),
                    getIndexParameter(2, "metadata"),
                    getIndexParameter(3, "id"));
            args[0] = passwordEncoder.encode(updateUser.getCredentials());
            args[1] = metadata;
            args[2] = id;
        } else {
            args = new Object[2];
            sql = String.format("UPDATE %s SET %s = %s WHERE id = %s",
                    configuration.getUsersTable(),
                    configuration.getMetadataAttribute(),
                    getIndexParameter(1, "metadata"),
                    getIndexParameter(2, "id"));
            args[0] = metadata;
            args[1] = id;
        }

        return query(sql, args)
                .flatMap(Result::getRowsUpdated)
                .first(0)
                .flatMap(rowsUpdated -> {
                    if (rowsUpdated == 0) {
                        return Single.error(new UserNotFoundException(id));
                    }
                    return Single.just(updateUser);
                });
    }

    @Override
    public Completable delete(String id) {
        final String sql = String.format("DELETE FROM %s where id = %s",
                configuration.getUsersTable(),
                getIndexParameter(1, "id"));

        return query(sql, id)
                .flatMap(Result::getRowsUpdated)
                .flatMapCompletable(rowsUpdated -> {
                    if (rowsUpdated == 0) {
                        return Completable.error(new UserNotFoundException(id));
                    }
                    return Completable.complete();
                });
    }

    public void setConnectionPool(ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    private Maybe<Map<String, Object>> selectUserByUsername(String username) {
        final String sql = String.format(configuration.getSelectUserByUsernameQuery(), getIndexParameter(1, "username"));
        return query(sql, username)
                .flatMap(result -> result.map(ColumnMapRowMapper::mapRow))
                .firstElement();
    }

    private Flowable<Result> query(String sql, Object... args) {
        return Single.fromPublisher(connectionPool.create())
                .toFlowable()
                .flatMap(connection -> {
                    Statement statement = connection.createStatement(sql);
                    for (int i = 0; i < args.length; i++) {
                        Object arg = args[i];
                        bind(statement, i, arg, arg != null ? arg.getClass() : String.class);
                    }
                    return Flowable.fromPublisher(statement.execute())
                            .doFinally(() -> Completable.fromPublisher(connection.close()).subscribe());
                });
    }

    private User createUser(Map<String, Object> claims) {
        // get username
        String username = claims.get(configuration.getUsernameAttribute()).toString();
        // get sub
        String id = claims.get(configuration.getIdentifierAttribute()).toString();
        // get encrypted password
        String password = claims.get(configuration.getPasswordAttribute()) != null ? claims.get(configuration.getPasswordAttribute()).toString() : null;
        // compute metadata
        computeMetadata(claims);

        // create the user
        DefaultUser user = new DefaultUser(username);
        user.setId(id);
        user.setCredentials(password);
        // additional claims
        Map<String, Object> additionalInformation = new HashMap<>(claims);
        claims.put(StandardClaims.SUB, id);
        claims.put(StandardClaims.PREFERRED_USERNAME, username);
        // remove reserved claims
        additionalInformation.remove(configuration.getIdentifierAttribute());
        additionalInformation.remove(configuration.getUsernameAttribute());
        additionalInformation.remove(configuration.getPasswordAttribute());
        additionalInformation.remove(configuration.getMetadataAttribute());
        user.setAdditionalInformation(additionalInformation);

        return user;
    }

    private void bind(Statement statement, int index, Object value, Class type) {
        if (value != null) {
            statement.bind(index, value);
        } else {
            statement.bindNull(index, type);
        }
    }

    private String getIndexParameter(int index, String field) {
        return ParametersUtils.getIndexParameter(configuration.getProtocol(), index, field);
    }

    private void computeMetadata(Map<String, Object> claims) {
        Object metadata = claims.get(configuration.getMetadataAttribute());
        if (metadata == null) {
            return;
        }
        try {
            claims.putAll(objectMapper.readValue(claims.get(configuration.getMetadataAttribute()).toString(), Map.class));
        } catch (Exception e) {
        }
    }

    private String convert(Map<String, Object> claims) {
        if (claims == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(claims);
        } catch (Exception e) {
            return null;
        }
    }
}
