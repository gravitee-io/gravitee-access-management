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

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.exception.authentication.InternalAuthenticationServiceException;
import io.gravitee.am.common.exception.authentication.UsernameNotFoundException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.mongo.authentication.spring.MongoAuthenticationProviderConfiguration;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import org.bson.BsonDocument;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import({MongoAuthenticationProviderConfiguration.class})
public class MongoAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoAuthenticationProvider.class);
    private static final String FIELD_ID = "_id";
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    @Autowired
    private IdentityProviderMapper mapper;

    @Autowired
    private IdentityProviderRoleMapper roleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MongoIdentityProviderConfiguration configuration;

    @Autowired
    private MongoClient mongoClient;

    @Override
    public AuthenticationProvider stop() throws Exception {
        if (this.mongoClient != null) {
            try {
                this.mongoClient.close();
            } catch (Exception e) {
                LOGGER.debug("Unable to safely close MongoDB connection", e);
            }
        }
        return this;
    }

    public Maybe<User> loadUserByUsername(Authentication authentication) {
        String username = ((String) authentication.getPrincipal()).toLowerCase();
        return findUserByMultipleField(username)
                .toList()
                .flatMapPublisher(users -> {
                    if (users.isEmpty()) {
                        return Flowable.error(new UsernameNotFoundException(username));
                    }
                    return Flowable.fromIterable(users);
                })
                .filter(user -> {
                    String password = user.getString(this.configuration.getPasswordField());
                    String presentedPassword = authentication.getCredentials().toString();

                    if (password == null) {
                        LOGGER.debug("Authentication failed: password is null");
                        return false;
                    }

                    if (configuration.isUseDedicatedSalt()) {
                        String hash = user.getString(configuration.getPasswordSaltAttribute());
                        if (!passwordEncoder.matches(presentedPassword, password, hash)) {
                            LOGGER.debug("Authentication failed: password does not match stored value");
                            return false;
                        }
                    } else {
                        if (!passwordEncoder.matches(presentedPassword, password)) {
                            LOGGER.debug("Authentication failed: password does not match stored value");
                            return false;
                        }
                    }

                    return true;
                })
                .toList()
                .flatMapMaybe(users -> {
                    if (users.isEmpty()) {
                        return Maybe.error(new BadCredentialsException("Bad credentials"));
                    }
                    if (users.size() > 1) {
                        return Maybe.error(new BadCredentialsException("Bad credentials"));
                    }
                    return Maybe.just(this.createUser(authentication.getContext(), users.get(0)));
                });
    }

    private Flowable<Document> findUserByMultipleField(String value) {
        MongoCollection<Document> usersCol = this.mongoClient.getDatabase(this.configuration.getDatabase()).getCollection(this.configuration.getUsersCollection());
        String findQuery = this.configuration.getFindUserByMultipleFieldsQuery() != null ? this.configuration.getFindUserByMultipleFieldsQuery() : this.configuration.getFindUserByUsernameQuery();
        String rawQuery = findQuery.replaceAll("\\?", value);
        String jsonQuery = convertToJsonString(rawQuery);
        BsonDocument query = BsonDocument.parse(jsonQuery);
        return Flowable.fromPublisher(usersCol.find(query));
    }

    public Maybe<User> loadUserByUsername(String username) {
        final String encodedUsername = username.toLowerCase();
        return findUserByUsername(encodedUsername)
                .map(document -> createUser(new SimpleAuthenticationContext(), document));
    }

    private Maybe<Document> findUserByUsername(String username) {
        MongoCollection<Document> usersCol = this.mongoClient.getDatabase(this.configuration.getDatabase()).getCollection(this.configuration.getUsersCollection());
        String rawQuery = this.configuration.getFindUserByUsernameQuery().replaceAll("\\?", username);
        String jsonQuery = convertToJsonString(rawQuery);
        BsonDocument query = BsonDocument.parse(jsonQuery);
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
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put(StandardClaims.SUB, sub);
        additionalInformation.put(StandardClaims.PREFERRED_USERNAME, username);
        // apply user mapping
        Map<String, Object> mappedAttributes = applyUserMapping(authContext, document);
        additionalInformation.putAll(mappedAttributes);
        // update sub if user mapping has been changed
        if (additionalInformation.containsKey(StandardClaims.SUB)) {
            user.setId(additionalInformation.get(StandardClaims.SUB).toString());
        }
        // update username if user mapping has been changed
        if (additionalInformation.containsKey(StandardClaims.PREFERRED_USERNAME)) {
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

    private String convertToJsonString(String rawString) {
        rawString = rawString.replaceAll("[^\\{\\}\\[\\],:\\s]+", "\"$0\"").replaceAll("\\s+", "");
        return rawString;
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
}
