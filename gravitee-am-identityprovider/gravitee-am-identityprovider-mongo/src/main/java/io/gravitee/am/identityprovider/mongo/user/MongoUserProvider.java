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
package io.gravitee.am.identityprovider.mongo.user;

import com.google.common.base.Strings;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.identityprovider.api.encoding.BinaryToTextEncoder;
import io.gravitee.am.identityprovider.mongo.MongoAbstractProvider;
import io.gravitee.am.identityprovider.mongo.authentication.spring.MongoAuthenticationProviderConfiguration;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;

import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import({MongoAuthenticationProviderConfiguration.class})
public class MongoUserProvider extends MongoAbstractProvider implements UserProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoUserProvider.class);
    private static final String FIELD_ID = "_id";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String INDEX_USERNAME = "username_1";
    private static final String INDEX_USERNAME_UNIQUE = "u1_unique";

    @Autowired
    private BinaryToTextEncoder binaryToTextEncoder;

    private MongoCollection<Document> usersCollection;

    @Value("${repositories.management.mongodb.ensureIndexOnStart:${management.mongodb.ensureIndexOnStart:true}}")
    private boolean ensureIndexOnStart;

    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        // init users collection
        usersCollection = this.mongoClient.getDatabase(this.configuration.getDatabase()).getCollection(this.configuration.getUsersCollection());

        createOrUpdateIndex();
    }

    @Override
    public UserProvider stop() {
        if (this.clientWrapper != null) {
            this.clientWrapper.releaseClient();
        }
        return this;
    }

    @Override
    public Maybe<User> findByEmail(String email) {
        String rawQuery = this.configuration.getFindUserByEmailQuery();
        String jsonQuery = convertToJsonString(rawQuery).replace("?", email);
        BsonDocument query = BsonDocument.parse(jsonQuery);
        return Observable.fromPublisher(usersCollection.find(query).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findByUsername(String username) {
        final String encodedUsername = getSafeUsername(username);

        String rawQuery = this.configuration.getFindUserByUsernameQuery();
        String jsonQuery = convertToJsonString(rawQuery).replace("?", encodedUsername);
        BsonDocument query = BsonDocument.parse(jsonQuery);
        return Observable.fromPublisher(usersCollection.find(query).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<User> create(User user) {
        final String username = getSafeUsername(user.getUsername());

        return findByUsername(username)
                .isEmpty()
                .flatMap(isEmpty -> {
                    if (!isEmpty) {
                        return Single.error(new UserAlreadyExistsException(user.getUsername()));
                    } else {
                        Document document = new Document();
                        // set technical id
                        document.put(FIELD_ID, user.getId() != null ? user.getId() : RandomString.generate());
                        // set username
                        document.put(configuration.getUsernameField(), username);
                        // set password
                        if (user.getCredentials() != null) {
                            if (configuration.isUseDedicatedSalt()) {
                                byte[] salt = createSalt();
                                document.put(configuration.getPasswordField(), passwordEncoder.encode(user.getCredentials(), salt));
                                document.put(configuration.getPasswordSaltAttribute(), binaryToTextEncoder.encode(salt));
                            } else {
                                document.put(configuration.getPasswordField(), passwordEncoder.encode(user.getCredentials()));
                            }
                        }
                        // set additional information
                        if (user.getAdditionalInformation() != null) {
                            document.putAll(user.getAdditionalInformation());
                        }
                        // set date fields
                        document.put(FIELD_CREATED_AT, new Date());
                        document.put(FIELD_UPDATED_AT, document.get(FIELD_CREATED_AT));
                        return Single.fromPublisher(usersCollection.insertOne(document)).flatMap(success -> findById(document.getString(FIELD_ID)).toSingle());
                    }
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<User> update(String id, User updateUser) {
        return findById(id, false)
                .switchIfEmpty(Single.error(new UserNotFoundException(id)))
                .flatMap(oldUser -> {
                    Document document = new Document();
                    // set username (keep the original value)
                    document.put(configuration.getUsernameField(), oldUser.getUsername());
                    // set password
                    if (updateUser.getCredentials() != null) {
                        if (configuration.isUseDedicatedSalt()) {
                            byte[] salt = createSalt();
                            document.put(configuration.getPasswordField(), passwordEncoder.encode(updateUser.getCredentials(), salt));
                            document.put(configuration.getPasswordSaltAttribute(), binaryToTextEncoder.encode(salt));
                        } else {
                            document.put(configuration.getPasswordField(), passwordEncoder.encode(updateUser.getCredentials()));
                        }
                    } else {
                        document.put(configuration.getPasswordField(), oldUser.getCredentials());
                        if (configuration.isUseDedicatedSalt() && oldUser.getAdditionalInformation() != null) {
                            // be sure to include the current salt as we are using the MongoDB replaceOne method
                            document.put(configuration.getPasswordSaltAttribute(), oldUser.getAdditionalInformation().get(configuration.getPasswordSaltAttribute()));
                        }
                    }
                    // set additional information
                    if (updateUser.getAdditionalInformation() != null) {
                        document.putAll(updateUser.getAdditionalInformation());
                    }
                    // set date fields
                    document.put(FIELD_CREATED_AT, oldUser.getCreatedAt());
                    document.put(FIELD_UPDATED_AT, new Date());
                    return Single.fromPublisher(usersCollection.replaceOne(eq(FIELD_ID, oldUser.getId()), document)).flatMap(updateResult -> findById(oldUser.getId()).toSingle());
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<User> updateUsername(User user, String username) {
        if (Strings.isNullOrEmpty(username)) {
            return Single.error(new IllegalArgumentException("Username required for UserProvider.updateUsername"));
        }

        return findById(user.getId())
                .switchIfEmpty(Single.error(new UserNotFoundException(user.getId())))
                .flatMap(foundUser -> {
                    var updates = Updates.combine(
                            Updates.set(configuration.getUsernameField(), username.toLowerCase()),
                            Updates.set(FIELD_UPDATED_AT, new Date()));
                    return Single.fromPublisher(usersCollection.updateOne(eq(FIELD_ID, user.getId()), updates))
                            .flatMap(updateResult -> findById(user.getId()).toSingle());
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<User> updatePassword(User user, String password) {

        if (Strings.isNullOrEmpty(password)) {
            return Single.error(new IllegalArgumentException("Password required for UserProvider.updatePassword"));
        }

        return findById(user.getId(), false)
                .switchIfEmpty(Single.error(new UserNotFoundException(user.getId())))
                .flatMap(oldUser -> {
                    // set password
                    Bson passwordField = null;
                    Bson passwordSaltField = null;
                    if (configuration.isUseDedicatedSalt()) {
                        byte[] salt = createSalt();
                        passwordField = Updates.set(configuration.getPasswordField(), passwordEncoder.encode(password, salt));
                        passwordSaltField = Updates.set(configuration.getPasswordSaltAttribute(), binaryToTextEncoder.encode(salt));
                    } else {
                        passwordField = Updates.set(configuration.getPasswordField(), passwordEncoder.encode(password));
                    }

                    Bson updates = passwordSaltField == null ?
                            Updates.combine(
                                    passwordField,
                                    Updates.set(FIELD_UPDATED_AT, new Date())) :
                            Updates.combine(
                                    passwordField,
                                    passwordSaltField,
                                    Updates.set(FIELD_UPDATED_AT, new Date()));

                    return Single.fromPublisher(usersCollection.updateOne(eq(FIELD_ID, oldUser.getId()), updates))
                            .flatMap(updateResult -> findById(oldUser.getId()).toSingle());
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return findById(id)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(id)))
                .flatMapCompletable(idpUser -> Completable.fromPublisher(usersCollection.deleteOne(eq(FIELD_ID, id))))
                .observeOn(Schedulers.computation());
    }

    private Maybe<User> findById(String userId) {
        return this.findById(userId, true);
    }

    private Maybe<User> findById(String userId, boolean filterSalt) {
        return Observable.fromPublisher(usersCollection.find(eq(FIELD_ID, userId)).first()).firstElement().map(u -> convert(u, filterSalt))
                .observeOn(Schedulers.computation());
    }

    private User convert(Document document) {
        return this.convert(document, true);
    }

    private User convert(Document document, boolean filterSalt) {
        String username = document.getString(configuration.getUsernameField());
        DefaultUser user = new DefaultUser(username);
        user.setId(document.getString(FIELD_ID));
        user.setCredentials(document.getString(configuration.getPasswordField()));
        user.setCreatedAt(document.get(FIELD_CREATED_AT, Date.class));
        user.setUpdatedAt(document.get(FIELD_UPDATED_AT, Date.class));
        // additional claims
        Map<String, Object> claims = new HashMap<>();
        claims.put(StandardClaims.SUB, document.getString(FIELD_ID));
        claims.put(StandardClaims.PREFERRED_USERNAME, username);
        // remove reserved claims
        document.remove(FIELD_ID);
        document.remove(configuration.getUsernameField());
        document.remove(configuration.getPasswordField());
        claims.putAll(document);

        if (filterSalt && configuration.isUseDedicatedSalt()) {
            claims.remove(configuration.getPasswordSaltAttribute());
        }

        user.setAdditionalInformation(claims);
        return user;
    }

    private String convertToJsonString(String rawString) {
        return rawString
                .replaceAll("[^" + JSON_SPECIAL_CHARS + "\\s]+", "\"$0\"")
                .replaceAll("\\s+", "");
    }

    private byte[] createSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[configuration.getPasswordSaltLength()];
        random.nextBytes(salt);
        return salt;
    }

    /**
     * Create or update index on username field
     */
    private void createOrUpdateIndex() {
        if (ensureIndexOnStart) {
            getUserNameIndex()
                    .andThen(Completable.fromPublisher(usersCollection.createIndex(new Document(configuration.getUsernameField(), 1), new IndexOptions().name(INDEX_USERNAME_UNIQUE).unique(true))))
                    .subscribe(
                            () -> LOGGER.debug("Unique index on username created"),
                            err -> LOGGER.error("An error occurred while creating index {} with unique constraints", INDEX_USERNAME_UNIQUE, err));
        }
    }

    private Completable getUserNameIndex() {
        return Observable.fromPublisher(usersCollection.listIndexes())
                .map(document -> document.getString("name"))
                .flatMapCompletable(indexName -> {
                    if (indexName.equals(INDEX_USERNAME)) {
                        return Completable.fromPublisher(usersCollection.dropIndex(indexName));
                    } else {
                        return Completable.complete();
                    }
                });
    }
}
