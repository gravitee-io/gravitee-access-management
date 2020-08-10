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
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.model.scim.Certificate;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.UserMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.AddressMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.AttributeMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.CertificateMongo;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private static final String FIELD_EMAIL_CLAIM = "additionalInformation.email";
    private static final String FIELD_EXTERNAL_ID = "externalId";

    private MongoCollection<UserMongo> usersCollection;

    @PostConstruct
    public void init() {
        usersCollection = mongoOperations.getCollection("users", UserMongo.class);
        super.createIndex(usersCollection, new Document(FIELD_DOMAIN, 1));
        super.createIndex(usersCollection, new Document(FIELD_DOMAIN, 1).append(FIELD_EMAIL, 1));
        super.createIndex(usersCollection, new Document(FIELD_DOMAIN, 1).append(FIELD_EMAIL_CLAIM, 1));
        super.createIndex(usersCollection, new Document(FIELD_DOMAIN, 1).append(FIELD_USERNAME, 1));
        super.createIndex(usersCollection, new Document(FIELD_DOMAIN, 1).append(FIELD_EXTERNAL_ID, 1));
        super.createIndex(usersCollection, new Document(FIELD_DOMAIN, 1).append(FIELD_USERNAME, 1).append(FIELD_SOURCE, 1));
        super.createIndex(usersCollection, new Document(FIELD_DOMAIN, 1).append(FIELD_EXTERNAL_ID, 1).append(FIELD_SOURCE, 1));
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
    public Single<Page<User>> search(String domain, String query, int page, int size) {
        // currently search on username field
        Bson searchQuery = new BasicDBObject(FIELD_USERNAME, query);
        // if query contains wildcard, use the regex query
        if (query.contains("*")) {
            String compactQuery = query.replaceAll("\\*+", ".*");
            String regex = "^" + compactQuery;
            searchQuery = new BasicDBObject(FIELD_USERNAME, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
        }

        Bson mongoQuery = and(
                eq(FIELD_DOMAIN, domain),
                searchQuery);

        Single<Long> countOperation = Observable.fromPublisher(usersCollection.countDocuments(mongoQuery)).first(0l);
        Single<Set<User>> usersOperation = Observable.fromPublisher(usersCollection.find(mongoQuery).skip(size * page).limit(size)).map(this::convert).collect(LinkedHashSet::new, Set::add);
        return Single.zip(countOperation, usersOperation, (count, users) -> new Page<>(users, 0, count));
    }

    @Override
    public Single<List<User>> findByDomainAndEmail(String domain, String email, boolean strict) {
        BasicDBObject emailQuery = new BasicDBObject(FIELD_EMAIL, (strict) ? email : Pattern.compile(email, Pattern.CASE_INSENSITIVE));
        BasicDBObject emailClaimQuery = new BasicDBObject(FIELD_EMAIL_CLAIM, (strict) ? email : Pattern.compile(email, Pattern.CASE_INSENSITIVE));
        Bson mongoQuery = and(
                eq(FIELD_DOMAIN, domain),
                or(emailQuery, emailClaimQuery));

        return Observable.fromPublisher(usersCollection.find(mongoQuery)).map(this::convert).collect(ArrayList::new, List::add);
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
    public Maybe<User> findByDomainAndExternalIdAndSource(String domain, String externalId, String source) {
        return Observable.fromPublisher(
                usersCollection
                        .find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_EXTERNAL_ID, externalId), eq(FIELD_SOURCE, source)))
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
        user.setId(user.getId() == null ? RandomString.generate() : user.getId());
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
        user.setDisplayName(userMongo.getDisplayName());
        user.setNickName(userMongo.getNickName());
        user.setFirstName(userMongo.getFirstName());
        user.setLastName(userMongo.getLastName());
        user.setAccountNonExpired(userMongo.isAccountNonExpired());
        user.setAccountLockedAt(userMongo.getAccountLockedAt());
        user.setAccountLockedUntil(userMongo.getAccountLockedUntil());
        user.setAccountNonLocked(userMongo.isAccountNonLocked());
        user.setCredentialsNonExpired(userMongo.isCredentialsNonExpired());
        user.setEnabled(userMongo.isEnabled());
        user.setInternal(userMongo.isInternal());
        user.setPreRegistration(userMongo.isPreRegistration());
        user.setRegistrationCompleted(userMongo.isRegistrationCompleted());
        user.setRegistrationUserUri(userMongo.getRegistrationUserUri());
        user.setRegistrationAccessToken(userMongo.getRegistrationAccessToken());
        user.setDomain(userMongo.getDomain());
        user.setSource(userMongo.getSource());
        user.setClient(userMongo.getClient());
        user.setLoginsCount(userMongo.getLoginsCount());
        user.setLoggedAt(userMongo.getLoggedAt());
        user.setRoles(userMongo.getRoles());
        user.setEmails(toModelAttributes(userMongo.getEmails()));
        user.setPhoneNumbers(toModelAttributes(userMongo.getPhoneNumbers()));
        user.setIms(toModelAttributes(userMongo.getIms()));
        user.setPhotos(toModelAttributes(userMongo.getPhotos()));
        user.setEntitlements(userMongo.getEntitlements());
        user.setAddresses(toModelAddresses(userMongo.getAddresses()));
        user.setX509Certificates(toModelCertificates(userMongo.getX509Certificates()));
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
        userMongo.setDisplayName(user.getDisplayName());
        userMongo.setNickName(user.getNickName());
        userMongo.setFirstName(user.getFirstName());
        userMongo.setLastName(user.getLastName());
        userMongo.setAccountNonExpired(user.isAccountNonExpired());
        userMongo.setAccountLockedAt(user.getAccountLockedAt());
        userMongo.setAccountLockedUntil(user.getAccountLockedUntil());
        userMongo.setAccountNonLocked(user.isAccountNonLocked());
        userMongo.setCredentialsNonExpired(user.isCredentialsNonExpired());
        userMongo.setEnabled(user.isEnabled());
        userMongo.setInternal(user.isInternal());
        userMongo.setPreRegistration(user.isPreRegistration());
        userMongo.setRegistrationCompleted(user.isRegistrationCompleted());
        userMongo.setRegistrationUserUri(user.getRegistrationUserUri());
        userMongo.setRegistrationAccessToken(user.getRegistrationAccessToken());
        userMongo.setDomain(user.getDomain());
        userMongo.setSource(user.getSource());
        userMongo.setClient(user.getClient());
        userMongo.setLoginsCount(user.getLoginsCount());
        userMongo.setLoggedAt(user.getLoggedAt());
        userMongo.setRoles(user.getRoles());
        userMongo.setEmails(toMongoAttributes(user.getEmails()));
        userMongo.setPhoneNumbers(toMongoAttributes(user.getPhoneNumbers()));
        userMongo.setIms(toMongoAttributes(user.getIms()));
        userMongo.setPhotos(toMongoAttributes(user.getPhotos()));
        userMongo.setEntitlements(user.getEntitlements());
        userMongo.setAddresses(toMongoAddresses(user.getAddresses()));
        userMongo.setX509Certificates(toMongoCertificates(user.getX509Certificates()));
        userMongo.setAdditionalInformation(user.getAdditionalInformation() != null ? new Document(user.getAdditionalInformation()) : new Document());
        userMongo.setCreatedAt(user.getCreatedAt());
        userMongo.setUpdatedAt(user.getUpdatedAt());
        return userMongo;
    }

    private List<Attribute> toModelAttributes(List<AttributeMongo> mongoAttributes) {
        if (mongoAttributes == null) {
            return null;
        }
        return mongoAttributes
                .stream()
                .map(mongoAttribute -> {
                    Attribute modelAttribute = new Attribute();
                    modelAttribute.setPrimary(mongoAttribute.isPrimary());
                    modelAttribute.setValue(mongoAttribute.getValue());
                    modelAttribute.setType(mongoAttribute.getType());
                    return modelAttribute;
                }).collect(Collectors.toList());
    }

    private List<AttributeMongo> toMongoAttributes(List<Attribute> modelAttributes) {
        if (modelAttributes == null) {
            return null;
        }
        return modelAttributes
                .stream()
                .map(modelAttribute -> {
                    AttributeMongo mongoAttribute = new AttributeMongo();
                    mongoAttribute.setPrimary(modelAttribute.isPrimary());
                    mongoAttribute.setValue(modelAttribute.getValue());
                    mongoAttribute.setType(modelAttribute.getType());
                    return mongoAttribute;
                }).collect(Collectors.toList());
    }

    private List<Address> toModelAddresses(List<AddressMongo> mongoAddresses) {
        if (mongoAddresses == null) {
            return null;
        }
        return mongoAddresses
                .stream()
                .map(mongoAddress -> {
                    Address modelAddress = new Address();
                    modelAddress.setType(mongoAddress.getType());
                    modelAddress.setFormatted(mongoAddress.getFormatted());
                    modelAddress.setStreetAddress(mongoAddress.getStreetAddress());
                    modelAddress.setCountry(mongoAddress.getCountry());
                    modelAddress.setLocality(mongoAddress.getLocality());
                    modelAddress.setPostalCode(mongoAddress.getPostalCode());
                    modelAddress.setRegion(mongoAddress.getRegion());
                    modelAddress.setPrimary(mongoAddress.isPrimary());
                    return modelAddress;
                }).collect(Collectors.toList());
    }

    private List<AddressMongo> toMongoAddresses(List<Address> modelAddresses) {
        if (modelAddresses == null) {
            return null;
        }
        return modelAddresses
                .stream()
                .map(modelAddress -> {
                    AddressMongo mongoAddress = new AddressMongo();
                    mongoAddress.setType(modelAddress.getType());
                    mongoAddress.setFormatted(modelAddress.getFormatted());
                    mongoAddress.setStreetAddress(modelAddress.getStreetAddress());
                    mongoAddress.setCountry(modelAddress.getCountry());
                    mongoAddress.setLocality(modelAddress.getLocality());
                    mongoAddress.setPostalCode(modelAddress.getPostalCode());
                    mongoAddress.setRegion(modelAddress.getRegion());
                    mongoAddress.setPrimary(modelAddress.isPrimary());
                    return mongoAddress;
                }).collect(Collectors.toList());
    }

    private List<Certificate> toModelCertificates(List<CertificateMongo> mongoCertificates) {
        if (mongoCertificates == null) {
            return null;
        }
        return mongoCertificates
                .stream()
                .map(mongoCertificate -> {
                    Certificate modelCertificate = new Certificate();
                    modelCertificate.setValue(mongoCertificate.getValue());
                    return modelCertificate;
                }).collect(Collectors.toList());
    }

    private List<CertificateMongo> toMongoCertificates(List<Certificate> modelCertificates) {
        if (modelCertificates == null) {
            return null;
        }
        return modelCertificates
                .stream()
                .map(modelCertificate -> {
                    CertificateMongo mongoCertificate = new CertificateMongo();
                    mongoCertificate.setValue(modelCertificate.getValue());
                    return mongoCertificate;
                }).collect(Collectors.toList());
    }
}
