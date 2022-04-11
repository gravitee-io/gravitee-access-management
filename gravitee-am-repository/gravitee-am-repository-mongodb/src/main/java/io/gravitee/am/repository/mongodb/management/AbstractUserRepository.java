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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.model.scim.Certificate;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.CommonUserRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.repository.mongodb.common.FilterCriteriaParser;
import io.gravitee.am.repository.mongodb.management.internal.model.UserMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.AddressMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.AttributeMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.CertificateMongo;
import io.reactivex.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractUserRepository<T extends UserMongo> extends AbstractManagementMongoRepository implements CommonUserRepository {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    protected static final String FIELD_USERNAME = "username";
    protected static final String FIELD_DISPLAY_NAME = "displayName";
    protected static final String FIELD_FIRST_NAME = "firstName";
    protected static final String FIELD_LAST_NAME = "lastName";
    protected static final String FIELD_SOURCE = "source";
    protected static final String FIELD_EMAIL = "email";
    protected static final String FIELD_EMAIL_CLAIM = "additionalInformation.email";
    protected static final String FIELD_EXTERNAL_ID = "externalId";

    protected MongoCollection<T> usersCollection;

    protected abstract Class<T> getMongoClass();

    protected void initCollection(String collectionName) {
        usersCollection = mongoOperations.getCollection(collectionName, getMongoClass());
        super.init(usersCollection);
        super.createIndex(usersCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1));
        super.createIndex(usersCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_EMAIL, 1));
        super.createIndex(usersCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_EMAIL_CLAIM, 1));
        super.createIndex(usersCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_USERNAME, 1));
        super.createIndex(usersCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_DISPLAY_NAME, 1));
        super.createIndex(usersCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_FIRST_NAME, 1));
        super.createIndex(usersCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_LAST_NAME, 1));
        super.createIndex(usersCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_EXTERNAL_ID, 1));
        super.createIndex(usersCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_USERNAME, 1).append(FIELD_SOURCE, 1));
        super.createIndex(usersCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_EXTERNAL_ID, 1).append(FIELD_SOURCE, 1));
    }


    @Override
    public Flowable<User> findAll(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(usersCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId)))).map(this::convert);
    }

    @Override
    public Single<Page<User>> findAll(ReferenceType referenceType, String referenceId, int page, int size) {
        Single<Long> countOperation = Observable.fromPublisher(usersCollection.countDocuments(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId)))).first(0l);
        Single<Set<User>> usersOperation = Observable.fromPublisher(usersCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId))).sort(new BasicDBObject(FIELD_USERNAME, 1)).skip(size * page).limit(size)).map(this::convert).collect(LinkedHashSet::new, Set::add);
        return Single.zip(countOperation, usersOperation, (count, users) -> new Page<>(users, page, count));
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, String query, int page, int size) {
        Bson searchQuery = or(
                new BasicDBObject(FIELD_USERNAME, query),
                new BasicDBObject(FIELD_EMAIL, query),
                new BasicDBObject(FIELD_EMAIL_CLAIM, query),
                new BasicDBObject(FIELD_DISPLAY_NAME, query),
                new BasicDBObject(FIELD_FIRST_NAME, query),
                new BasicDBObject(FIELD_LAST_NAME, query));

        // if query contains wildcard, use the regex query
        if (query.contains("*")) {
            String compactQuery = query.replaceAll("\\*+", ".*");
            String regex = "^" + compactQuery;
            Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            searchQuery = or(
                    new BasicDBObject(FIELD_USERNAME, pattern),
                    new BasicDBObject(FIELD_EMAIL, pattern),
                    new BasicDBObject(FIELD_EMAIL_CLAIM, pattern),
                    new BasicDBObject(FIELD_DISPLAY_NAME, pattern),
                    new BasicDBObject(FIELD_FIRST_NAME, pattern),
                    new BasicDBObject(FIELD_LAST_NAME, pattern));
        }

        Bson mongoQuery = and(
                eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                eq(FIELD_REFERENCE_ID, referenceId),
                searchQuery);

        Single<Long> countOperation = Observable.fromPublisher(usersCollection.countDocuments(mongoQuery)).first(0l);
        Single<Set<User>> usersOperation = Observable.fromPublisher(usersCollection.find(mongoQuery).sort(new BasicDBObject(FIELD_USERNAME, 1)).skip(size * page).limit(size)).map(this::convert).collect(LinkedHashSet::new, Set::add);
        return Single.zip(countOperation, usersOperation, (count, users) -> new Page<>(users, 0, count));
    }

    @Override
    public Single<Page<User>> search(ReferenceType referenceType, String referenceId, FilterCriteria criteria, int page, int size) {
        try {
            BasicDBObject searchQuery = BasicDBObject.parse(FilterCriteriaParser.parse(criteria));

            Bson mongoQuery = and(
                    eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                    eq(FIELD_REFERENCE_ID, referenceId),
                    searchQuery);

            Single<Long> countOperation = Observable.fromPublisher(usersCollection.countDocuments(mongoQuery)).first(0l);
            Single<Set<User>> usersOperation = Observable.fromPublisher(usersCollection.find(mongoQuery).sort(new BasicDBObject(FIELD_USERNAME, 1)).skip(size * page).limit(size)).map(this::convert).collect(LinkedHashSet::new, Set::add);
            return Single.zip(countOperation, usersOperation, (count, users) -> new Page<>(users, 0, count));
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException) {
                return Single.error(ex);
            }
            logger.error("An error has occurred while searching users with criteria {}", criteria, ex);
            return Single.error(new TechnicalException("An error has occurred while searching users with filter criteria", ex));
        }

    }

    @Override
    public Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source) {
        return Observable.fromPublisher(
                        usersCollection
                                .find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_USERNAME, username), eq(FIELD_SOURCE, source)))
                                .limit(1)
                                .first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Maybe<User> findByExternalIdAndSource(ReferenceType referenceType, String referenceId, String externalId, String source) {
        return Observable.fromPublisher(
                        usersCollection
                                .find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_EXTERNAL_ID, externalId), eq(FIELD_SOURCE, source)))
                                .limit(1)
                                .first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Flowable<User> findByIdIn(List<String> ids) {
        return Flowable.fromPublisher(usersCollection.find(in(FIELD_ID, ids))).map(this::convert);
    }

    @Override
    public Maybe<User> findById(ReferenceType referenceType, String referenceId, String userId) {
        return Observable.fromPublisher(usersCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_ID, userId))).first()).firstElement().map(this::convert);
    }

    @Override
    public Maybe<User> findById(String userId) {
        return Observable.fromPublisher(usersCollection.find(eq(FIELD_ID, userId)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<User> create(User item) {
        UserMongo user = convert(item);
        user.setId(user.getId() == null ? RandomString.generate() : user.getId());
        return Single.fromPublisher(usersCollection.insertOne((T) user))
                .flatMap(success -> {
                    item.setId(user.getId());
                    return Single.just(item);
                });
    }

    @Override
    public Single<User> update(User item) {
        UserMongo user = convert(item);
        return Single.fromPublisher(usersCollection.replaceOne(eq(FIELD_ID, user.getId()), (T) user))
                .flatMap(updateResult -> Single.just(item));
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(usersCollection.deleteOne(eq(FIELD_ID, id)));
    }

    @Override
    public Completable deleteByReference(ReferenceType referenceType, String referenceId) {
        return Completable.fromPublisher(usersCollection.deleteMany(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId))));
    }

    protected User convert(T userMongo) {
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
        user.setNewsletter(userMongo.isNewsletter());
        user.setRegistrationUserUri(userMongo.getRegistrationUserUri());
        user.setRegistrationAccessToken(userMongo.getRegistrationAccessToken());
        user.setReferenceType(ReferenceType.valueOf(userMongo.getReferenceType()));
        user.setReferenceId(userMongo.getReferenceId());
        user.setSource(userMongo.getSource());
        user.setClient(userMongo.getClient());
        user.setLoginsCount(userMongo.getLoginsCount());
        user.setLoggedAt(userMongo.getLoggedAt());
        user.setLastPasswordReset(userMongo.getLastPasswordReset());
        user.setLastLogoutAt(userMongo.getLastLogoutAt());
        user.setRoles(userMongo.getRoles());
        user.setDynamicRoles(userMongo.getDynamicRoles());
        user.setEmails(toModelAttributes(userMongo.getEmails()));
        user.setPhoneNumbers(toModelAttributes(userMongo.getPhoneNumbers()));
        user.setIms(toModelAttributes(userMongo.getIms()));
        user.setPhotos(toModelAttributes(userMongo.getPhotos()));
        user.setEntitlements(userMongo.getEntitlements());
        user.setAddresses(toModelAddresses(userMongo.getAddresses()));
        user.setX509Certificates(toModelCertificates(userMongo.getX509Certificates()));
        user.setFactors(userMongo.getFactors());
        user.setType(userMongo.getType());
        user.setTitle(userMongo.getTitle());
        user.setPreferredLanguage(userMongo.getPreferredLanguage());
        user.setAdditionalInformation(userMongo.getAdditionalInformation());
        user.setCreatedAt(userMongo.getCreatedAt());
        user.setUpdatedAt(userMongo.getUpdatedAt());
        return user;
    }

    protected abstract T convert(User user);

    protected T convert(User user, T userMongo) {
        if (user == null) {
            return null;
        }

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
        userMongo.setNewsletter(user.isNewsletter());
        userMongo.setRegistrationUserUri(user.getRegistrationUserUri());
        userMongo.setRegistrationAccessToken(user.getRegistrationAccessToken());
        userMongo.setReferenceType(user.getReferenceType().name());
        userMongo.setReferenceId(user.getReferenceId());
        userMongo.setSource(user.getSource());
        userMongo.setClient(user.getClient());
        userMongo.setLoginsCount(user.getLoginsCount());
        userMongo.setLoggedAt(user.getLoggedAt());
        userMongo.setLastPasswordReset(user.getLastPasswordReset());
        userMongo.setLastLogoutAt(user.getLastLogoutAt());
        userMongo.setRoles(user.getRoles());
        userMongo.setDynamicRoles(user.getDynamicRoles());
        userMongo.setEmails(toMongoAttributes(user.getEmails()));
        userMongo.setPhoneNumbers(toMongoAttributes(user.getPhoneNumbers()));
        userMongo.setIms(toMongoAttributes(user.getIms()));
        userMongo.setPhotos(toMongoAttributes(user.getPhotos()));
        userMongo.setEntitlements(user.getEntitlements());
        userMongo.setAddresses(toMongoAddresses(user.getAddresses()));
        userMongo.setX509Certificates(toMongoCertificates(user.getX509Certificates()));
        userMongo.setFactors(user.getFactors());
        userMongo.setType(user.getType());
        userMongo.setTitle(user.getTitle());
        userMongo.setPreferredLanguage(user.getPreferredLanguage());
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
