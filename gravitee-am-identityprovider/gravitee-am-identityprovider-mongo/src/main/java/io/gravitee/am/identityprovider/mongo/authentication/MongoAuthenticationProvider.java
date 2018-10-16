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
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderMapper;
import io.gravitee.am.identityprovider.mongo.authentication.spring.MongoAuthenticationProviderConfiguration;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.am.service.exception.authentication.BadCredentialsException;
import io.gravitee.am.service.exception.authentication.UsernameNotFoundException;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import org.bson.BsonDocument;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import({MongoAuthenticationProviderConfiguration.class})
public class MongoAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoAuthenticationProvider.class);

    @Autowired
    private MongoIdentityProviderMapper mapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MongoIdentityProviderConfiguration configuration;

    @Autowired
    private MongoClient mongoClient;

    public Maybe<User> loadUserByUsername(Authentication authentication) {
        String username = (String)authentication.getPrincipal();
        return findUserByUsername(username)
                .switchIfEmpty(Maybe.error(new UsernameNotFoundException(username)))
                .map(user -> {
                    String password = user.getString(this.configuration.getPasswordField());
                    String presentedPassword = authentication.getCredentials().toString();
                    if (!passwordEncoder.matches(presentedPassword, password)) {
                        LOGGER.debug("Authentication failed: password does not match stored value");
                        throw new BadCredentialsException("Bad credentials");
                    }
                    return createUser(username, user);
                });
    }

    public Maybe<User> loadUserByUsername(String username) {
        return findUserByUsername(username)
                .map(document -> createUser(username, document));
    }

    private Maybe<Document> findUserByUsername(String username) {
        MongoCollection<Document> usersCol = this.mongoClient.getDatabase(this.configuration.getDatabase()).getCollection(this.configuration.getUsersCollection());
        String rawQuery = this.configuration.getFindUserByUsernameQuery().replaceAll("\\?", username);
        String jsonQuery = convertToJsonString(rawQuery);
        BsonDocument query = BsonDocument.parse(jsonQuery);
        return Observable.fromPublisher(usersCol.find(query).first()).firstElement();
    }

    private User createUser(String username, Document document) {
        DefaultUser user = new DefaultUser(username);
        Map<String, Object> claims = new HashMap<>();
        claims.put(StandardClaims.SUB, username);
        claims.put(StandardClaims.PREFERRED_USERNAME, username);
        if(this.mapper.getMappers() != null) {
            this.mapper.getMappers().forEach((k, v) -> claims.put(k, document.getString(v)));
        }

        user.setAdditonalInformation(claims);
        return user;
    }

    private String convertToJsonString(String rawString) {
        rawString = rawString.replaceAll("[^\\{\\}\\[\\],:]+", "\"$0\"").replaceAll("\\s+","");
        return rawString;
    }
}