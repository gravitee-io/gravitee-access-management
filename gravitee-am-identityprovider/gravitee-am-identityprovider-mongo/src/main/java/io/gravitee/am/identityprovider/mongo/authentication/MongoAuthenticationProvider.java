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
import io.gravitee.am.common.exception.authentication.UsernameNotFoundException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderMapper;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.mongo.authentication.spring.MongoAuthenticationProviderConfiguration;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.*;

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
    private MongoIdentityProviderMapper mapper;

    @Autowired
    private MongoIdentityProviderRoleMapper roleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MongoIdentityProviderConfiguration configuration;

    @Autowired
    private MongoClient mongoClient;

    public Maybe<User> loadUserByUsername(Authentication authentication) {
        String username = ((String) authentication.getPrincipal()).toLowerCase();
        return findUserByMultipleField(username)
                .filter( user -> {
                    String password = user.getString(this.configuration.getPasswordField());
                    String presentedPassword = authentication.getCredentials().toString();

                    if (password == null) {
                        LOGGER.debug("Authentication failed: password is null");
                        return false;
                    }

                    if (!passwordEncoder.matches(presentedPassword, password)) {
                        LOGGER.debug("Authentication failed: password does not match stored value");
                        return false;
                    }

                    return true;
                })
                .map(this::createUser)
                .toList()
                .flatMapMaybe(users -> {
                    if (users.isEmpty()) {
                        return Maybe.error(new BadCredentialsException("Bad credentials"));
                    }
                    if (users.size() > 1) {
                        return Maybe.error(new BadCredentialsException("Bad credentials"));
                    }
                    return Maybe.just(users.get(0));
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
                .map(document -> createUser(document));
    }

    private Maybe<Document> findUserByUsername(String username) {
        MongoCollection<Document> usersCol = this.mongoClient.getDatabase(this.configuration.getDatabase()).getCollection(this.configuration.getUsersCollection());
        String rawQuery = this.configuration.getFindUserByUsernameQuery().replaceAll("\\?", username);
        String jsonQuery = convertToJsonString(rawQuery);
        BsonDocument query = BsonDocument.parse(jsonQuery);
        return Observable.fromPublisher(usersCol.find(query).first()).firstElement();
    }

    private User createUser(Document document) {
        String username = document.getString(FIELD_USERNAME);
        DefaultUser user = new DefaultUser(username);
        Map<String, Object> claims = new HashMap<>();

        String sub = document.containsKey(FIELD_ID) ?
                document.get(FIELD_ID) instanceof ObjectId ? ((ObjectId) document.get(FIELD_ID)).toString() : document.getString(FIELD_ID)
                : username;
        // set technical id
        user.setId(sub);

        // set user roles
        user.setRoles(getUserRoles(document));

        // set claims
        claims.put(StandardClaims.SUB, sub);
        claims.put(StandardClaims.PREFERRED_USERNAME, username);
        if (this.mapper.getMappers() != null && !this.mapper.getMappers().isEmpty()) {
            this.mapper.getMappers().forEach((k, v) -> claims.put(k, document.get(v)));
        } else {
            // default claims
            // remove reserved claims
            document.remove(FIELD_ID);
            document.remove(FIELD_USERNAME);
            document.remove(configuration.getPasswordField());
            document.remove(FIELD_CREATED_AT);
            if (document.containsKey(FIELD_UPDATED_AT)) {
                document.put(StandardClaims.UPDATED_AT, document.get(FIELD_UPDATED_AT));
                document.remove(FIELD_UPDATED_AT);
            }
            document.entrySet().forEach(entry -> claims.put(entry.getKey(), entry.getValue()));
        }
        user.setAdditionalInformation(claims);

        return user;
    }

    private String convertToJsonString(String rawString) {
        rawString = rawString.replaceAll("[^\\{\\}\\[\\],:\\s]+", "\"$0\"").replaceAll("\\s+","");
        return rawString;
    }

    private List<String> getUserRoles(Document document) {
        Set<String> roles = new HashSet();
        if (roleMapper != null && roleMapper.getRoles() != null) {
            roleMapper.getRoles().forEach((role, users) -> {
                Arrays.asList(users).forEach(u -> {
                    // user/group have the following syntax userAttribute=userValue
                    String[] attributes = u.split("=", 2);
                    String userAttribute = attributes[0];
                    String userValue = attributes[1];
                    if (document.containsKey(userAttribute) && document.getString(userAttribute).equals(userValue)) {
                        roles.add(role);
                    }
                });
            });
        }
        return new ArrayList<>(roles);
    }
}
