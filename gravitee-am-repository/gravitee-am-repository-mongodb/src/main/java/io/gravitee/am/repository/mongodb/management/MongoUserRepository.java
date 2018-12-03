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
package io.gravitee.am.repository.mongodb.management;

import com.mongodb.BasicDBObject;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.repository.mongodb.common.IdGenerator;
import io.gravitee.am.repository.mongodb.common.LoggableIndexSubscriber;
import io.gravitee.am.repository.mongodb.management.internal.model.UserMongo;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Filters.*;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoUserRepository extends AbstractManagementMongoRepository implements UserRepository {

    private static final String FIELD_ID = "_id";
    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_EMAIL = "email";

    private MongoCollection<UserMongo> usersCollection;

    @Autowired
    private IdGenerator idGenerator;

    @PostConstruct
    public void init() {
        usersCollection = mongoOperations.getCollection("users", UserMongo.class);
        usersCollection.createIndex(new Document(FIELD_DOMAIN, 1)).subscribe(new LoggableIndexSubscriber());
        usersCollection.createIndex(new Document(FIELD_DOMAIN, 1).append(FIELD_EMAIL, 1)).subscribe(new LoggableIndexSubscriber());
        usersCollection.createIndex(new Document(FIELD_DOMAIN, 1).append(FIELD_USERNAME, 1)).subscribe(new LoggableIndexSubscriber());
        usersCollection.createIndex(new Document(FIELD_DOMAIN, 1).append(FIELD_USERNAME, 1).append(FIELD_SOURCE, 1)).subscribe(new LoggableIndexSubscriber());
    }

    @Override
    public Single<Set<User>> findByDomain(String domain) {
        return Observable.fromPublisher(usersCollection.find(eq(FIELD_DOMAIN, domain))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Page<User>> findByDomain(String domain, int page, int size) {
        Single<Long> countOperation = Observable.fromPublisher(usersCollection.countDocuments(eq(FIELD_DOMAIN, domain))).first(0l);
        Single<Set<User>> usersOperation = Observable.fromPublisher(usersCollection.find(eq(FIELD_DOMAIN, domain)).sort(new BasicDBObject(FIELD_USERNAME, 1)).skip(size * page).limit(size)).map(this::convert).collect(LinkedHashSet::new, Set::add);
        return Single.zip(countOperation, usersOperation, (count, users) -> new Page<>(users, page, count));
    }

    @Override
    public Single<Page<User>> search(String domain, String query, int limit) {
        // currently search on username field
        Bson mongoQuery = and(
                eq(FIELD_DOMAIN, domain),
                regex(FIELD_USERNAME, "^(?)" + Pattern.quote(query), "i"));

        Single<Long> countOperation = Observable.fromPublisher(usersCollection.countDocuments(mongoQuery)).first(0l);
        Single<Set<User>> usersOperation = Observable.fromPublisher(usersCollection.find(mongoQuery).limit(limit)).map(this::convert).collect(LinkedHashSet::new, Set::add);
        return Single.zip(countOperation, usersOperation, (count, users) -> new Page<>(users, 0, count));
    }

    @Override
    public Single<List<User>> findByDomainAndEmail(String domain, String email) {
        return Observable.fromPublisher(usersCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_EMAIL, email)))).map(this::convert).collect(ArrayList::new, List::add);
    }

    @Override
    public Maybe<User> findByUsernameAndDomain(String domain, String username) {
        return Observable.fromPublisher(
                usersCollection
                        .find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_USERNAME, username)))
                        .limit(1)
                        .first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Maybe<User> findByDomainAndUsernameAndSource(String domain, String username, String source) {
        return Observable.fromPublisher(
                usersCollection
                        .find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_USERNAME, username), eq(FIELD_SOURCE, source)))
                        .limit(1)
                        .first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Single<List<User>> findByIdIn(List<String> ids) {
        return Observable.fromPublisher(usersCollection.find(in(FIELD_ID, ids))).map(this::convert).collect(ArrayList::new, List::add);
    }

    @Override
    public Maybe<User> findById(String userId) {
        return Observable.fromPublisher(usersCollection.find(eq(FIELD_ID, userId)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<User> create(User item) {
        UserMongo user = convert(item);
        user.setId(user.getId() == null ? (String) idGenerator.generate() : user.getId());
        return Single.fromPublisher(usersCollection.insertOne(user)).flatMap(success -> findById(user.getId()).toSingle());
    }

    @Override
    public Single<User> update(User item) {
        UserMongo user = convert(item);
        return Single.fromPublisher(usersCollection.replaceOne(eq(FIELD_ID, user.getId()), user)).flatMap(updateResult -> findById(user.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(usersCollection.deleteOne(eq(FIELD_ID, id)));
    }

    private User convert(UserMongo userMongo) {
        if (userMongo == null) {
            return null;
        }

        User user = new User();
        user.setId(userMongo.getId());
        user.setExternalId(userMongo.getExternalId());
        user.setUsername(userMongo.getUsername());
        user.setEmail(userMongo.getEmail());
        user.setFirstName(userMongo.getFirstName());
        user.setLastName(userMongo.getLastName());
        user.setAccountNonExpired(userMongo.isAccountNonExpired());
        user.setAccountNonLocked(userMongo.isAccountNonLocked());
        user.setCredentialsNonExpired(userMongo.isCredentialsNonExpired());
        user.setEnabled(userMongo.isEnabled());
        user.setInternal(userMongo.isInternal());
        user.setPreRegistration(userMongo.isPreRegistration());
        user.setRegistrationCompleted(userMongo.isRegistrationCompleted());
        user.setDomain(userMongo.getDomain());
        user.setSource(userMongo.getSource());
        user.setClient(userMongo.getClient());
        user.setLoginsCount(userMongo.getLoginsCount());
        user.setLoggedAt(userMongo.getLoggedAt());
        user.setRoles(userMongo.getRoles());
        user.setAdditionalInformation(userMongo.getAdditionalInformation());
        user.setCreatedAt(userMongo.getCreatedAt());
        user.setUpdatedAt(userMongo.getUpdatedAt());
        return user;
    }

    private UserMongo convert(User user) {
        if (user == null) {
            return null;
        }

        UserMongo userMongo = new UserMongo();
        userMongo.setId(user.getId());
        userMongo.setExternalId(user.getExternalId());
        userMongo.setUsername(user.getUsername());
        userMongo.setEmail(user.getEmail());
        userMongo.setFirstName(user.getFirstName());
        userMongo.setLastName(user.getLastName());
        userMongo.setAccountNonExpired(user.isAccountNonExpired());
        userMongo.setAccountNonLocked(user.isAccountNonLocked());
        userMongo.setCredentialsNonExpired(user.isCredentialsNonExpired());
        userMongo.setEnabled(user.isEnabled());
        userMongo.setInternal(user.isInternal());
        userMongo.setPreRegistration(user.isPreRegistration());
        userMongo.setRegistrationCompleted(user.isRegistrationCompleted());
        userMongo.setDomain(user.getDomain());
        userMongo.setSource(user.getSource());
        userMongo.setClient(user.getClient());
        userMongo.setLoginsCount(user.getLoginsCount());
        userMongo.setLoggedAt(user.getLoggedAt());
        userMongo.setRoles(user.getRoles());
        userMongo.setAdditionalInformation(user.getAdditionalInformation() != null ? new Document(user.getAdditionalInformation()) : new Document());
        userMongo.setCreatedAt(user.getCreatedAt());
        userMongo.setUpdatedAt(user.getUpdatedAt());
        return userMongo;
    }
}
