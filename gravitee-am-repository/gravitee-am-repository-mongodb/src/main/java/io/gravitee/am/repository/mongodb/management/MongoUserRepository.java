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

import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.repository.mongodb.common.IdGenerator;
import io.gravitee.am.repository.mongodb.management.internal.model.UserMongo;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subscribers.DefaultSubscriber;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoUserRepository extends AbstractManagementMongoRepository implements UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(MongoUserRepository.class);
    private static final String FIELD_ID = "_id";
    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_USERNAME = "username";

    private MongoCollection<UserMongo> usersCollection;

    @Autowired
    private IdGenerator idGenerator;

    @PostConstruct
    public void init() {
        usersCollection = mongoOperations.getCollection("users", UserMongo.class);
        usersCollection.createIndex(new Document(FIELD_DOMAIN, 1)).subscribe(new IndexSubscriber());
        usersCollection.createIndex(new Document(FIELD_DOMAIN, 1).append(FIELD_USERNAME, 1)).subscribe(new IndexSubscriber());
    }

    @Override
    public Single<Set<User>> findByDomain(String domain) {
        return Observable.fromPublisher(usersCollection.find(eq(FIELD_DOMAIN, domain))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Page<User>> findByDomain(String domain, int page, int size) {
        Single<Long> countOperation = Observable.fromPublisher(usersCollection.count(eq(FIELD_DOMAIN, domain))).first(0l);
        Single<Set<User>> usersOperation = Observable.fromPublisher(usersCollection.find(eq(FIELD_DOMAIN, domain)).skip(size * (page - 1)).limit(size)).map(this::convert).collect(HashSet::new, Set::add);
        return Single.zip(countOperation, usersOperation, (count, users) -> new Page<>(users, page, count));
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
    public Single<Irrelevant> delete(String id) {
        return Single.fromPublisher(usersCollection.deleteOne(eq(FIELD_ID, id))).map(deleteResult -> Irrelevant.USER);
    }

    private User convert(UserMongo userMongo) {
        if (userMongo == null) {
            return null;
        }

        User user = new User();
        user.setId(userMongo.getId());
        user.setUsername(userMongo.getUsername());
        user.setPassword(userMongo.getPassword());
        user.setEmail(userMongo.getEmail());
        user.setFirstName(userMongo.getFirstName());
        user.setLastName(userMongo.getLastName());
        user.setAccountNonExpired(userMongo.isAccountNonExpired());
        user.setAccountNonLocked(userMongo.isAccountNonLocked());
        user.setCredentialsNonExpired(userMongo.isCredentialsNonExpired());
        user.setEnabled(userMongo.isEnabled());
        user.setDomain(userMongo.getDomain());
        user.setSource(userMongo.getSource());
        user.setClient(userMongo.getClient());
        user.setLoginsCount(userMongo.getLoginsCount());
        user.setLoggedAt(userMongo.getLoggedAt());
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
        userMongo.setUsername(user.getUsername());
        userMongo.setPassword(user.getPassword());
        userMongo.setEmail(user.getEmail());
        userMongo.setFirstName(user.getFirstName());
        userMongo.setLastName(user.getLastName());
        userMongo.setAccountNonExpired(user.isAccountNonExpired());
        userMongo.setAccountNonLocked(user.isAccountNonLocked());
        userMongo.setCredentialsNonExpired(user.isCredentialsNonExpired());
        userMongo.setEnabled(user.isEnabled());
        userMongo.setDomain(user.getDomain());
        userMongo.setSource(user.getSource());
        userMongo.setClient(user.getClient());
        userMongo.setLoginsCount(user.getLoginsCount());
        userMongo.setLoggedAt(user.getLoggedAt());
        userMongo.setAdditionalInformation(user.getAdditionalInformation() != null ? new Document(user.getAdditionalInformation()) : new Document());
        userMongo.setCreatedAt(user.getCreatedAt());
        userMongo.setUpdatedAt(user.getUpdatedAt());
        return userMongo;
    }

    private class IndexSubscriber extends DefaultSubscriber<String> {
        @Override
        public void onNext(String value) {
            logger.debug("Created an index named : " + value);
        }

        @Override
        public void onError(Throwable throwable) {
            logger.error("Error occurs during indexing", throwable);
        }

        @Override
        public void onComplete() {
            logger.debug("Index creation complete");
        }
    }
}
