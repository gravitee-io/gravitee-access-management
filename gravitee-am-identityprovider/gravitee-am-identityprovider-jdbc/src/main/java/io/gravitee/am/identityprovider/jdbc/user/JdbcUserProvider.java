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

import com.google.common.base.Strings;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.identityprovider.api.encoding.BinaryToTextEncoder;
import io.gravitee.am.identityprovider.jdbc.JdbcAbstractProvider;
import io.gravitee.am.identityprovider.jdbc.user.spring.JdbcUserProviderConfiguration;
import io.gravitee.am.identityprovider.jdbc.utils.ColumnMapRowMapper;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.r2dbc.spi.Result;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(JdbcUserProviderConfiguration.class)
public class JdbcUserProvider extends JdbcAbstractProvider<UserProvider> implements UserProvider {

    private final Pattern pattern = Pattern.compile("idp_users___");

    @Autowired
    private BinaryToTextEncoder binaryToTextEncoder;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (configuration.getAutoProvisioning()) {
            LOGGER.debug("Auto provisioning of identity provider table enabled");
            // for now simply get the file named <driver>.schema, more complex stuffs will be done if schema updates have to be done in the future
            final String sqlScript = "database/" + configuration.getProtocol() + ".schema";
            try (InputStream input = this.getClass().getClassLoader().getResourceAsStream(sqlScript);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {

                Single.fromPublisher(connectionPool.create())
                        .flatMapPublisher(connection -> {
                            final String tableExistsStatement = tableExists(configuration.getProtocol(), configuration.getUsersTable());
                            return query(connection, tableExistsStatement, new Object[0])
                                    .flatMap(Result::getRowsUpdated)
                                    .first(0l)
                                    .flatMapPublisher(total -> {
                                        if (total == 0) {
                                            LOGGER.debug("SQL datatable {} does not exist.", configuration.getUsersTable());

                                            final List<String> sqlStatements = reader.lines()
                                                    // remove empty line and comment
                                                    .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("--"))
                                                    .map(line -> {
                                                        // update table & index names
                                                        String finalLine = pattern.matcher(line).replaceAll(configuration.getUsersTable());
                                                        LOGGER.debug("Statement to execute: {}", finalLine);
                                                        return finalLine;
                                                    })
                                                    .distinct()
                                                    .collect(Collectors.toList());

                                            LOGGER.debug("Found {} statements to execute", sqlStatements.size());
                                            return Flowable.fromIterable(sqlStatements)
                                                    .flatMap(statement -> query(connection, statement, new Object[0]))
                                                    .flatMap(Result::getRowsUpdated);
                                        } else {
                                            return Flowable.empty();
                                        }
                                    })
                                    .doFinally(() -> Completable.fromPublisher(connection.close()).subscribe());
                        })
                        .ignoreElements()
                        .blockingAwait();
            } catch (Exception e) {
                LOGGER.error("Unable to initialize the identity provider schema", e);
            }
        }
    }

    @Override
    public Single<UserProvider> asyncStart() {
        try {
            super.doStart();
        } catch (Exception e) {
            return Single.error(e);
        }

        if (configuration.getAutoProvisioning()) {
            LOGGER.debug("Auto provisioning of identity provider table enabled");
            // for now simply get the file named <driver>.schema, more complex stuffs will be done if schema updates have to be done in the future
            final String sqlScript = "database/" + configuration.getProtocol() + ".schema";
            try (InputStream input = this.getClass().getClassLoader().getResourceAsStream(sqlScript);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {

                final List<String> sqlStatements = reader.lines()
                        // remove empty line and comment
                        .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("--"))
                        .map(line -> {
                            // update table & index names
                            String finalLine = pattern.matcher(line).replaceAll(configuration.getUsersTable());
                            LOGGER.debug("Statement to execute: {}", finalLine);
                            return finalLine;
                        })
                        .distinct()
                        .collect(Collectors.toList());

                LOGGER.debug("Found {} statements to execute if SQL schema doesn't exist", sqlStatements.size());

                return Single.fromPublisher(connectionPool.create())
                        .flatMapPublisher(connection -> {
                            final String tableExistsStatement = tableExists(configuration.getProtocol(), configuration.getUsersTable());
                            return query(connection, tableExistsStatement, new Object[0])
                                    .flatMap(Result::getRowsUpdated)
                                    .first(0l)
                                    .flatMapPublisher(total -> {
                                        if (total == 0) {
                                            LOGGER.debug("SQL datatable {} doest not exists, initialize it.", configuration.getUsersTable());

                                            return Flowable.fromIterable(sqlStatements)
                                                    .flatMap(statement -> query(connection, statement, new Object[0]))
                                                    .flatMap(Result::getRowsUpdated);
                                        } else {
                                            return Flowable.empty();
                                        }
                                    })
                                    .doFinally(() -> Completable.fromPublisher(connection.close()).subscribe());
                        }).toList()
                        .map(__ -> this);
            } catch (Exception e) {
                LOGGER.error("Unable to initialize the identity provider schema", e);
                return Single.error(e);
            }
        }
        return Single.just(this);
    }

    private String tableExists(String protocol, String table) {
        if ("sqlserver".equalsIgnoreCase(protocol)) {
            return "SELECT 1 FROM sysobjects WHERE name = '" + table + "' AND xtype = 'U'";
        } else {
            return "SELECT 1 FROM information_schema.tables WHERE table_name = '" + table + "'";
        }
    }

    @Override
    public Maybe<User> findByEmail(String email) {
        return selectUserByEmail(email)
                .map(result -> createUser(result));
    }

    private Maybe<Map<String, Object>> selectUserByEmail(String email) {
        final String sql = String.format(configuration.getSelectUserByEmailQuery(), getIndexParameter(1, configuration.getEmailAttribute()));
        return query(sql, email)
                .flatMap(result -> result.map(ColumnMapRowMapper::mapRow))
                .firstElement();
    }

    @Override
    public Maybe<User> findByUsername(String username) {
        return selectUserByUsername(username)
                .map(result -> createUser(result));
    }

    @Override
    public Single<User> create(User user) {
        // set technical id
        ((DefaultUser) user).setId(user.getId() != null ? user.getId() : RandomString.generate());
        return selectUserByUsername(user.getUsername())
                .isEmpty()
                .flatMap(isEmpty -> {
                    if (!isEmpty) {
                        return Single.error(new UserAlreadyExistsException(user.getUsername()));
                    } else {
                        String sql;
                        Object[] args;
                        if (configuration.isUseDedicatedSalt()) {
                            sql = String.format("INSERT INTO %s (%s, %s, %s, %s, %s, %s) VALUES (%s, %s, %s, %s, %s, %s)",
                                    configuration.getUsersTable(),
                                    configuration.getIdentifierAttribute(),
                                    configuration.getUsernameAttribute(),
                                    configuration.getPasswordAttribute(),
                                    configuration.getPasswordSaltAttribute(),
                                    configuration.getEmailAttribute(),
                                    configuration.getMetadataAttribute(),
                                    getIndexParameter(1, configuration.getIdentifierAttribute()),
                                    getIndexParameter(2, configuration.getUsernameAttribute()),
                                    getIndexParameter(3, configuration.getPasswordAttribute()),
                                    getIndexParameter(4, configuration.getPasswordSaltAttribute()),
                                    getIndexParameter(5, configuration.getEmailAttribute()),
                                    getIndexParameter(6, configuration.getMetadataAttribute()));

                            args = new Object[6];
                            byte[] salt = createSalt();
                            args[0] = user.getId();
                            args[1] = user.getUsername();
                            args[2] = user.getCredentials() != null ? passwordEncoder.encode(user.getCredentials(), salt) : null;
                            args[3] = user.getCredentials() != null ? binaryToTextEncoder.encode(salt) : null;
                            args[4] = user.getEmail();
                            args[5] = user.getAdditionalInformation() != null ? objectMapper.writeValueAsString(user.getAdditionalInformation()) : null;
                        } else {
                            sql = String.format("INSERT INTO %s (%s, %s, %s, %s, %s) VALUES (%s, %s, %s, %s, %s)",
                                    configuration.getUsersTable(),
                                    configuration.getIdentifierAttribute(),
                                    configuration.getUsernameAttribute(),
                                    configuration.getPasswordAttribute(),
                                    configuration.getEmailAttribute(),
                                    configuration.getMetadataAttribute(),
                                    getIndexParameter(1, configuration.getIdentifierAttribute()),
                                    getIndexParameter(2, configuration.getUsernameAttribute()),
                                    getIndexParameter(3, configuration.getPasswordAttribute()),
                                    getIndexParameter(4, configuration.getEmailAttribute()),
                                    getIndexParameter(5, configuration.getMetadataAttribute()));

                            args = new Object[5];
                            args[0] = user.getId();
                            args[1] = user.getUsername();
                            args[2] = user.getCredentials() != null ? passwordEncoder.encode(user.getCredentials()) : null;
                            args[3] = user.getEmail();
                            args[4] = user.getAdditionalInformation() != null ? objectMapper.writeValueAsString(user.getAdditionalInformation()) : null;
                        }

                        return query(sql, args)
                                .flatMap(Result::getRowsUpdated)
                                .first(0L)
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
            if (configuration.isUseDedicatedSalt()) {
                args = new Object[5];
                sql = String.format("UPDATE %s SET %s = %s, %s = %s, %s = %s , %s = %s WHERE %s = %s",
                        configuration.getUsersTable(),
                        configuration.getPasswordAttribute(),
                        getIndexParameter(1, configuration.getPasswordAttribute()),
                        configuration.getPasswordSaltAttribute(),
                        getIndexParameter(2, configuration.getPasswordSaltAttribute()),
                        configuration.getEmailAttribute(),
                        getIndexParameter(3, configuration.getEmailAttribute()),
                        configuration.getMetadataAttribute(),
                        getIndexParameter(4, configuration.getMetadataAttribute()),
                        configuration.getIdentifierAttribute(),
                        getIndexParameter(5, configuration.getIdentifierAttribute()));
                byte[] salt = createSalt();
                args[0] = passwordEncoder.encode(updateUser.getCredentials(), salt);
                args[1] = binaryToTextEncoder.encode(salt);
                args[2] = updateUser.getEmail();
                args[3] = metadata;
                args[4] = id;
            } else {
                args = new Object[4];
                sql = String.format("UPDATE %s SET %s = %s, %s = %s, %s = %s WHERE %s = %s",
                        configuration.getUsersTable(),
                        configuration.getPasswordAttribute(),
                        getIndexParameter(1, configuration.getPasswordAttribute()),
                        configuration.getMetadataAttribute(),
                        getIndexParameter(2, configuration.getMetadataAttribute()),
                        configuration.getEmailAttribute(),
                        getIndexParameter(3, configuration.getEmailAttribute()),
                        configuration.getIdentifierAttribute(),
                        getIndexParameter(4, configuration.getIdentifierAttribute()));
                args[0] = passwordEncoder.encode(updateUser.getCredentials());
                args[1] = metadata;
                args[2] = updateUser.getEmail();
                args[3] = id;
            }
        } else {
            args = new Object[3];
            sql = String.format("UPDATE %s SET %s = %s, %s = %s WHERE %s = %s",
                    configuration.getUsersTable(),
                    configuration.getMetadataAttribute(),
                    getIndexParameter(1, configuration.getMetadataAttribute()),
                    configuration.getEmailAttribute(),
                    getIndexParameter(2, configuration.getEmailAttribute()),
                    configuration.getIdentifierAttribute(),
                    getIndexParameter(3, configuration.getIdentifierAttribute()));
            args[0] = metadata;
            args[1] = updateUser.getEmail();
            args[2] = id;
        }

        return query(sql, args)
                .flatMap(Result::getRowsUpdated)
                .first(0l)
                .flatMap(rowsUpdated -> {
                    if (rowsUpdated == 0) {
                        return Single.error(new UserNotFoundException(id));
                    }
                    ((DefaultUser) updateUser).setId(id);
                    return Single.just(updateUser);
                });
    }

    @Override
    public Single<User> updateUsername(User user, String username) {
        if (Strings.isNullOrEmpty(username)) {
            return Single.error(new IllegalArgumentException("Username required for UserProvider.updateUsername"));
        }

        var sql = String.format("UPDATE %s SET %s = %s WHERE %s = %s",
                configuration.getUsersTable(),
                configuration.getUsernameAttribute(),
                getIndexParameter(1, configuration.getUsernameAttribute()),
                configuration.getIdentifierAttribute(),
                getIndexParameter(2, configuration.getIdentifierAttribute()));

        Object[] args = {username, user.getId()};

        return query(sql, args)
                .flatMap(Result::getRowsUpdated)
                .first(0l)
                .flatMap(rowsUpdated -> {
                    if (rowsUpdated == 0) {
                        return Single.error(new UserNotFoundException(user.getId()));
                    }
                    ((DefaultUser) user).setUsername(username);
                    return Single.just(user);
                });
    }

    @Override
    public Single<User> updatePassword(User user, String password) {
        final String sql;
        final Object[] args;

        if (Strings.isNullOrEmpty(password)) {
            return Single.error(new IllegalArgumentException("Password required for UserProvider.updatePassword"));
        }
        if (configuration.isUseDedicatedSalt()) {
            args = new Object[3];
            sql = String.format("UPDATE %s SET %s = %s, %s = %s WHERE %s = %s",
                    configuration.getUsersTable(),
                    configuration.getPasswordAttribute(),
                    getIndexParameter(1, configuration.getPasswordAttribute()),
                    configuration.getPasswordSaltAttribute(),
                    getIndexParameter(2, configuration.getPasswordSaltAttribute()),
                    configuration.getIdentifierAttribute(),
                    getIndexParameter(3, configuration.getIdentifierAttribute()));
            byte[] salt = createSalt();
            args[0] = passwordEncoder.encode(password, salt);
            args[1] = binaryToTextEncoder.encode(salt);
            args[2] = user.getId();
        } else {
            args = new Object[2];
            sql = String.format("UPDATE %s SET %s = %s WHERE %s = %s",
                    configuration.getUsersTable(),
                    configuration.getPasswordAttribute(),
                    getIndexParameter(1, configuration.getPasswordAttribute()),
                    configuration.getIdentifierAttribute(),
                    getIndexParameter(2, configuration.getIdentifierAttribute()));
            args[0] = passwordEncoder.encode(password);
            args[1] = user.getId();
        }

        return query(sql, args)
                .flatMap(Result::getRowsUpdated)
                .first(0l)
                .flatMap(rowsUpdated -> {
                    if (rowsUpdated == 0) {
                        return Single.error(new UserNotFoundException(user.getId()));
                    }
                    return Single.just(user);
                });
    }

    @Override
    public Completable delete(String id) {
        final String sql = String.format("DELETE FROM %s where %s = %s",
                configuration.getUsersTable(),
                configuration.getIdentifierAttribute(),
                getIndexParameter(1, configuration.getIdentifierAttribute()));

        return query(sql, id)
                .flatMap(Result::getRowsUpdated)
                .flatMapCompletable(rowsUpdated -> {
                    if (rowsUpdated == 0) {
                        return Completable.error(new UserNotFoundException(id));
                    }
                    return Completable.complete();
                });
    }

    private Maybe<Map<String, Object>> selectUserByUsername(String username) {
        final String sql = String.format(configuration.getSelectUserByUsernameQuery(), getIndexParameter(1, configuration.getUsernameAttribute()));
        return query(sql, username)
                .flatMap(result -> result.map(ColumnMapRowMapper::mapRow))
                .firstElement();
    }

    private User createUser(Map<String, Object> claims) {
        // get username
        String username = (String) claims.get(configuration.getUsernameAttribute());
        // get sub
        String id = (String) claims.get(configuration.getIdentifierAttribute());
        // get email
        String email = (String) claims.get(configuration.getEmailAttribute());

        // compute metadata
        computeMetadata(claims);

        // create the user
        DefaultUser user = new DefaultUser(username);
        user.setId(id);
        user.setEmail(email);

        // additional claims
        Map<String, Object> additionalInformation = new HashMap<>(claims);
        claims.put(StandardClaims.SUB, id);
        claims.put(StandardClaims.PREFERRED_USERNAME, username);

        // remove reserved claims
        additionalInformation.remove(configuration.getIdentifierAttribute());
        additionalInformation.remove(configuration.getUsernameAttribute());
        additionalInformation.remove(configuration.getPasswordAttribute());
        additionalInformation.remove(configuration.getMetadataAttribute());
        if (configuration.isUseDedicatedSalt()) {
            additionalInformation.remove(configuration.getPasswordSaltAttribute());
        }
        user.setAdditionalInformation(additionalInformation);

        return user;
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

    private byte[] createSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[configuration.getPasswordSaltLength()];
        random.nextBytes(salt);
        return salt;
    }
}
