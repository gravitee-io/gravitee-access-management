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

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.identityprovider.jdbc.JdbcAbstractProvider;
import io.gravitee.am.identityprovider.jdbc.user.spring.JdbcUserProviderConfiguration;
import io.gravitee.am.identityprovider.jdbc.utils.ColumnMapRowMapper;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.context.annotation.Import;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(JdbcUserProviderConfiguration.class)
public class JdbcUserProvider extends JdbcAbstractProvider<UserProvider> implements UserProvider {

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
                        final String sql = String.format("INSERT INTO %s (%s, %s, %s, %s, %s) VALUES (?, ?, ?, ?, ?)",
                                configuration.getUsersTable(),
                                configuration.getIdentifierAttribute(),
                                configuration.getUsernameAttribute(),
                                configuration.getPasswordAttribute(),
                                configuration.getEmailAttribute(),
                                configuration.getMetadataAttribute());

                        Object[] args = new Object[5];
                        args[0] = user.getId();
                        args[1] = user.getUsername();
                        args[2] = user.getCredentials() != null ? passwordEncoder.encode(user.getCredentials()) : null;
                        args[3] = user.getEmail();
                        args[4] = user.getAdditionalInformation() != null ? objectMapper.writeValueAsString(user.getAdditionalInformation()) : null;

                        return query(sql, args)
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
            sql = String.format("UPDATE %s SET %s = ?, %s = ? WHERE id = ?",
                    configuration.getUsersTable(),
                    configuration.getPasswordAttribute(),
                    configuration.getMetadataAttribute());
            args[0] = passwordEncoder.encode(updateUser.getCredentials());
            args[1] = metadata;
            args[2] = id;
        } else {
            args = new Object[2];
            sql = String.format("UPDATE %s SET %s = ? WHERE id = ?",
                    configuration.getUsersTable(),
                    configuration.getMetadataAttribute());
            args[0] = metadata;
            args[1] = id;
        }

        return query(sql, args)
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
        final String sql = String.format("DELETE FROM %s where id = ?", configuration.getUsersTable());

        return query(sql, id)
                .flatMapCompletable(rowsUpdated -> {
                    if (rowsUpdated == 0) {
                        return Completable.error(new UserNotFoundException(id));
                    }
                    return Completable.complete();
                });
    }

    private Maybe<Map<String, Object>> selectUserByUsername(String username) {
        final String sql = String.format(configuration.getSelectUserByUsernameQuery(), "?");

        return db.select(sql)
                .parameter(username)
                .get(ColumnMapRowMapper::mapRow)
                .firstElement();
    }

    private Flowable<Integer> query(String sql, Object... args) {
        return db.update(sql)
                .parameters(args)
                .counts();
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
