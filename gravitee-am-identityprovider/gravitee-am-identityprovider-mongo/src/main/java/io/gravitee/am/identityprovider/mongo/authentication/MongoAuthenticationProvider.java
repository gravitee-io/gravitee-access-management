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

import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderMapper;
import io.gravitee.am.identityprovider.mongo.authentication.spring.MongoAuthenticationProviderConfiguration;
import org.bson.BsonDocument;
import org.bson.Document;
import org.jongo.query.BsonQueryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
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

    public User loadUserByUsername(Authentication authentication) {
        try {
            String username = (String)authentication.getPrincipal();
            Document user = this.findUserByUsername(username);
            String password = user.getString(this.configuration.getPasswordField());
            String presentedPassword = authentication.getCredentials().toString();
            if(!this.passwordEncoder.matches(presentedPassword, password)) {
                LOGGER.debug("Authentication failed: password does not match stored value");
                throw new BadCredentialsException("Bad credentials");
            } else {
                return this.createUser(username, user);
            }
        } catch (Exception var6) {
            throw new InternalAuthenticationServiceException(var6.getMessage(), var6);
        }
    }

    public User loadUserByUsername(String username) {
        try {
            Document user = this.findUserByUsername(username);
            if(user == null) {
                throw new UsernameNotFoundException("User " + username + " can not be found.");
            } else {
                return this.createUser(username, user);
            }
        } catch (Exception var3) {
            throw new InternalAuthenticationServiceException(var3.getMessage(), var3);
        }
    }

    private Document findUserByUsername(String username) {
        try {
            MongoCollection<Document> usersCol = this.mongoClient.getDatabase(this.configuration.getDatabase()).getCollection(this.configuration.getUsersCollection());
            DBObject query = (new BsonQueryFactory(null, "?")).createQuery(this.configuration.getFindUserByUsernameQuery(), new Object[]{username}).toDBObject();
            return usersCol.find(BsonDocument.parse(query.toString())).first();
        } catch (Exception var4) {
            throw new InternalAuthenticationServiceException(var4.getMessage(), var4);
        }
    }

    private User createUser(String username, Document document) {
        DefaultUser user = new DefaultUser(username);
        Map<String, Object> claims = new HashMap<>();
        if(this.mapper.getMappers() != null) {
            this.mapper.getMappers().forEach((k, v) -> {
                claims.put(k, document.getString(v));
            });
        }

        user.setAdditonalInformation(claims);
        return user;
    }
}