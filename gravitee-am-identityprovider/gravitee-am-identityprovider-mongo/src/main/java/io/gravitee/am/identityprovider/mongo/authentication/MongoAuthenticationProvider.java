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
package io.gravitee.am.identityprovider.mongo.authentication;

import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.exception.authentication.InternalAuthenticationServiceException;
import io.gravitee.am.common.exception.authentication.UsernameNotFoundException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.IdentityProviderGroupMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderMapper;
import io.gravitee.am.identityprovider.api.IdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.UserCredentialEvaluation;
import io.gravitee.am.identityprovider.mongo.MongoAbstractProvider;
import io.gravitee.am.identityprovider.mongo.authentication.spring.MongoAuthenticationProviderConfiguration;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Optional.ofNullable;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import({MongoAuthenticationProviderConfiguration.class})
public class MongoAuthenticationProvider extends MongoAbstractProvider implements AuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoAuthenticationProvider.class);
    private static final String FIELD_ID = "_id";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    @Autowired
    private IdentityProviderMapper mapper;

    @Autowired
    private IdentityProviderRoleMapper roleMapper;

    @Autowired
    private IdentityProviderGroupMapper groupMapper;

    @Override
    public AuthenticationProvider stop() throws Exception {
        if (this.clientWrapper != null) {
            this.clientWrapper.releaseClient();
        }
        return this;
    }

    public Maybe<User> loadUserByUsername(Authentication authentication) {
        String username = (String) authentication.getPrincipal();
        return findUserByMultipleField(username)
                .toList()
                .flatMapPublisher(users -> {
                    if (users.isEmpty()) {
                        return Flowable.error(new UsernameNotFoundException(username));
                    }
                    return Flowable.fromIterable(users);
                })
                .observeOn(Schedulers.computation())// switch to the computation thread as Password Encoding is CPU bounded
                .map(user -> {
                    String password = user.getString(this.configuration.getPasswordField());
                    String presentedPassword = authentication.getCredentials().toString();

                    if (password == null) {
                        LOGGER.debug("Authentication failed: password is null");
                        return new UserCredentialEvaluation<>(false, user);
                    }

                    if (configuration.isUseDedicatedSalt()) {
                        String hash = user.getString(configuration.getPasswordSaltAttribute());
                        if (!passwordEncoder.matches(presentedPassword, password, hash)) {
                            LOGGER.debug("Authentication failed: password does not match stored value");
                            return new UserCredentialEvaluation<>(false, user);
                        }
                    } else {
                        if (!passwordEncoder.matches(presentedPassword, password)) {
                            LOGGER.debug("Authentication failed: password does not match stored value");
                            return new UserCredentialEvaluation<>(false, user);
                        }
                    }

                    return new UserCredentialEvaluation<>(true, user);
                })
                .toList()
                .flatMapMaybe(userEvaluations -> {
                    final var validUsers = userEvaluations.stream().filter(UserCredentialEvaluation::isPasswordValid).toList();
                    if (validUsers.size() > 1) {
                        LOGGER.debug("Authentication failed: multiple accounts with same credentials");
                        return Maybe.error(new BadCredentialsException("Bad credentials"));
                    }

                    var userEvaluation = !validUsers.isEmpty() ? validUsers.get(0) : userEvaluations.get(0);

                    var user = this.createUser(authentication.getContext(), userEvaluation.getUser());
                    ofNullable(authentication.getContext()).ifPresent(auth -> auth.set(ACTUAL_USERNAME, user.getUsername()));
                    return userEvaluation.isPasswordValid() ?
                            Maybe.just(user) :
                            Maybe.error(new BadCredentialsException("Bad credentials"));
                });
    }

    private Flowable<Document> findUserByMultipleField(String username) {
        MongoCollection<Document> usersCol = this.mongoClient.getDatabase(this.configuration.getDatabase()).getCollection(this.configuration.getUsersCollection());
        String findQuery = this.configuration.getFindUserByMultipleFieldsQuery() != null ? this.configuration.getFindUserByMultipleFieldsQuery() : this.configuration.getFindUserByUsernameQuery();
        Bson query = buildUsernameQuery(findQuery, username, configuration.isUsernameCaseSensitive());
        return Flowable.fromPublisher(usersCol.find(query));
    }

    public Maybe<User> loadUserByUsername(String username) {
        return findUserByUsername(username)
                .map(document -> createUser(new SimpleAuthenticationContext(), document))
                .observeOn(Schedulers.computation());
    }

    private Maybe<Document> findUserByUsername(String username) {
        MongoCollection<Document> usersCol = this.mongoClient.getDatabase(this.configuration.getDatabase())
            .getCollection(this.configuration.getUsersCollection());
        Bson query = buildUsernameQuery(this.configuration.getFindUserByUsernameQuery(), username, configuration.isUsernameCaseSensitive());
        return Observable.fromPublisher(usersCol.find(query).first()).firstElement();
    }

    private User createUser(AuthenticationContext authContext, Document document) {
        // get sub
        String sub = getClaim(document, FIELD_ID, null);
        // get username
        String username = getClaim(document, configuration.getUsernameField(), sub);

        // create the user
        DefaultUser user = new DefaultUser(username);
        // set technical id
        user.setId(sub);
        // set user roles
        user.setRoles(applyRoleMapping(authContext, document));
        user.setGroups(applyGroupMapping(authContext, document));
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put(StandardClaims.SUB, sub);
        additionalInformation.put(StandardClaims.PREFERRED_USERNAME, username);
        // apply user mapping
        Map<String, Object> mappedAttributes = applyUserMapping(authContext, document);
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
        additionalInformation.remove(FIELD_ID);
        additionalInformation.remove(configuration.getUsernameField());
        additionalInformation.remove(configuration.getPasswordField());
        additionalInformation.remove(FIELD_CREATED_AT);
        if (configuration.isUseDedicatedSalt()) {
            additionalInformation.remove(configuration.getPasswordSaltAttribute());
        }
        if (additionalInformation.containsKey(FIELD_UPDATED_AT)) {
            additionalInformation.put(StandardClaims.UPDATED_AT, document.get(FIELD_UPDATED_AT));
            additionalInformation.remove(FIELD_UPDATED_AT);
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

    private List<String> applyGroupMapping(AuthenticationContext authContext, Map<String, Object> attributes) {
        if (!groupMappingEnabled()) {
            return Collections.emptyList();
        }
        return groupMapper.apply(authContext, attributes);
    }

    private boolean mappingEnabled() {
        return this.mapper != null;
    }

    private boolean roleMappingEnabled() {
        return this.roleMapper != null;
    }

    private boolean groupMappingEnabled() {
        return this.groupMapper != null;
    }
}
