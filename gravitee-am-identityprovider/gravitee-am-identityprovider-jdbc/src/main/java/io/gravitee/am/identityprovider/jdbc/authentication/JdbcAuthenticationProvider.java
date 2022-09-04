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
package io.gravitee.am.identityprovider.jdbc.authentication;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.exception.authentication.InternalAuthenticationServiceException;
import io.gravitee.am.common.exception.authentication.UsernameNotFoundException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.jdbc.JdbcAbstractProvider;
import io.gravitee.am.identityprovider.jdbc.authentication.spring.JdbcAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.jdbc.utils.ColumnMapRowMapper;
import io.gravitee.am.identityprovider.jdbc.utils.ParametersUtils;
import io.r2dbc.spi.Statement;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(JdbcAuthenticationProviderConfiguration.class)
public class JdbcAuthenticationProvider extends JdbcAbstractProvider<AuthenticationProvider> implements AuthenticationProvider {

    @Autowired
    private IdentityProviderMapper mapper;

    @Autowired
    private IdentityProviderRoleMapper roleMapper;

    @Override
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        final String username = authentication.getPrincipal().toString();
        final String presentedPassword = authentication.getCredentials().toString();

        return selectUserByMultipleField(username)
                .toList()
                .flatMapPublisher(users -> {
                    if (users.isEmpty()) {
                        return Flowable.error(new UsernameNotFoundException(username));
                    }
                    return Flowable.fromIterable(users);
                })
                .map(result -> {
                    // check password
                    String password = String.valueOf(result.get(configuration.getPasswordAttribute()));
                    if (password == null) {
                        LOGGER.debug("Authentication failed: password is null");
                        return new UserCredentialEvaluation<>(false, result);
                    }

                    if (configuration.isUseDedicatedSalt()) {
                        String hash = String.valueOf(result.get(configuration.getPasswordSaltAttribute()));
                        if (!passwordEncoder.matches(presentedPassword, password, hash)) {
                            LOGGER.debug("Authentication failed: password does not match stored value");
                            return new UserCredentialEvaluation<>(false, result);
                        }
                    } else {
                        if (!passwordEncoder.matches(presentedPassword, password)) {
                            LOGGER.debug("Authentication failed: password does not match stored value");
                            return new UserCredentialEvaluation<>(false, result);
                        }
                    }

                    return new UserCredentialEvaluation<>(true, result);
                })
                .toList()
                .flatMapMaybe(userEvaluations -> {
                    if (userEvaluations.size() > 1) {
                        LOGGER.debug("Authentication failed: multiple accounts with same credentials");
                        return Maybe.error(new BadCredentialsException("Bad credentials"));
                    }
                    var userEvaluation = userEvaluations.get(0);
                    var user = this.createUser(authentication.getContext(), userEvaluation.getUser());

                    ofNullable(authentication.getContext()).ifPresent(auth -> auth.set(ACTUAL_USERNAME, user.getUsername()));

                    return userEvaluation.isPasswordValid() ?
                            Maybe.just(user) :
                            Maybe.error(new BadCredentialsException("Bad credentials"));
                });
    }

    private Flowable<Map<String, Object>> selectUserByMultipleField(String username) {
        String rawQuery = configuration.getSelectUserByMultipleFieldsQuery() != null ? configuration.getSelectUserByMultipleFieldsQuery() : configuration.getSelectUserByUsernameQuery();
        String[] args = prepareIndexParameters(rawQuery);
        final String sql = String.format(rawQuery, args);
        return Flowable.fromPublisher(connectionPool.create())
                .flatMap(connection -> {
                    Statement statement = connection.createStatement(sql);
                    for (int i = 0; i < args.length; ++i) {
                        statement = statement.bind(i, username);
                    }
                    return Flowable.fromPublisher(statement.execute())
                            .doFinally(() -> Completable.fromPublisher(connection.close()).subscribe());
                })
                .flatMap(result -> result.map(ColumnMapRowMapper::mapRow));
    }

    private String[] prepareIndexParameters(String rawQuery) {
        final int variables = StringUtils.countOccurrencesOf(rawQuery, "%s");
        String[] idxParameters = new String[variables];
        for (int i = 0; i < variables; ++i) {
            idxParameters[i] = getIndexParameter(configuration.getUsernameAttribute(), i);
        }
        return idxParameters;
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        return selectUserByUsername(username)
                .map(attributes -> createUser(new SimpleAuthenticationContext(), attributes));
    }

    private Maybe<Map<String, Object>> selectUserByUsername(String username) {
        final String sql = String.format(configuration.getSelectUserByUsernameQuery(), getIndexParameter(configuration.getUsernameAttribute()));
        return Flowable.fromPublisher(connectionPool.create())
                .flatMap(connection -> Flowable.fromPublisher(connection.createStatement(sql).bind(0, username).execute())
                        .doFinally(() -> Completable.fromPublisher(connection.close()).subscribe()))
                .flatMap(result -> result.map(ColumnMapRowMapper::mapRow))
                .firstElement();
    }

    private User createUser(AuthenticationContext authContext, Map<String, Object> claims) {
        // get sub
        String sub = getClaim(claims, configuration.getIdentifierAttribute(), null);
        // get username
        String username = getClaim(claims, configuration.getUsernameAttribute(), sub);
        // get email
        String email = getClaim(claims, configuration.getEmailAttribute(), null);
        // compute metadata
        computeMetadata(claims);

        // create the user
        DefaultUser user = new DefaultUser(username);
        // set technical id
        user.setId(sub);
        // set email
        user.setEmail(email);
        // set user roles
        user.setRoles(applyRoleMapping(authContext, claims));
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put(StandardClaims.SUB, sub);
        additionalInformation.put(StandardClaims.PREFERRED_USERNAME, username);

        // apply user mapping
        Map<String, Object> mappedAttributes = applyUserMapping(authContext, claims);
        additionalInformation.putAll(mappedAttributes);
        // update sub if user mapping has been changed
        if (additionalInformation.get(StandardClaims.SUB) != null) {
            user.setId(additionalInformation.get(StandardClaims.SUB).toString());
        }
        // update username if user mapping has been changed
        if (additionalInformation.get(StandardClaims.PREFERRED_USERNAME) != null) {
            user.setUsername(additionalInformation.get(StandardClaims.PREFERRED_USERNAME).toString());
        }
        // remove reserved claims
        additionalInformation.remove(configuration.getUsernameAttribute());
        additionalInformation.remove(configuration.getPasswordAttribute());
        additionalInformation.remove(configuration.getMetadataAttribute());
        if (!StandardClaims.SUB.equals(configuration.getIdentifierAttribute())) {
            // remove the entry matching the identifier attribute only if
            // the identifier attribute isn't named "sub"
            additionalInformation.remove(configuration.getIdentifierAttribute());
        }
        if (configuration.isUseDedicatedSalt()) {
            additionalInformation.remove(configuration.getPasswordSaltAttribute());
        }

        if (additionalInformation.isEmpty() || additionalInformation.get(StandardClaims.SUB) == null) {
            throw new InternalAuthenticationServiceException("The 'sub' claim for the user is required");
        }

        user.setAdditionalInformation(additionalInformation);
        return user;
    }

    private String getClaim(Map<String, Object> claims, String userAttribute, String defaultValue) {
        return claims.containsKey(userAttribute) ? claims.get(userAttribute).toString() : defaultValue;
    }

    private Map<String, Object> applyUserMapping(AuthenticationContext authContext, Map<String, Object> attributes) {
        if (!mappingEnabled()) {
            return attributes;
        }
        return this.mapper.apply(authContext, attributes);
    }

    private List<String> applyRoleMapping(AuthenticationContext authContext, Map<String, Object> attributes) {
        if (!roleMappingEnabled()) {
            return Collections.emptyList();
        }
        return roleMapper.apply(authContext, attributes);
    }

    private boolean mappingEnabled() {
        return this.mapper != null;
    }

    private boolean roleMappingEnabled() {
        return this.roleMapper != null;
    }

    private String getIndexParameter(String field) {
        return getIndexParameter(field, 0);
    }

    private String getIndexParameter(String field, int offset) {
        return ParametersUtils.getIndexParameter(configuration.getProtocol(), 1 + offset, field);
    }

}
