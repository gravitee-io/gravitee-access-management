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
package io.gravitee.am.dataplane.mongodb.repository;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.analytics.Field;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.dataplane.mongodb.repository.model.UserMongo;
import io.gravitee.am.dataplane.mongodb.repository.model.scim.AddressMongo;
import io.gravitee.am.dataplane.mongodb.repository.model.scim.AttributeMongo;
import io.gravitee.am.dataplane.mongodb.repository.model.scim.CertificateMongo;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.model.scim.Certificate;
import io.gravitee.am.repository.common.UserIdFields;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.repository.mongodb.common.MongoUtils;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.MaybeSource;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleSource;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.or;
import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_CLIENT;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_TYPE;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.findOne;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.userIdMatches;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoUserRepository extends AbstractDataPlaneMongoRepository implements UserRepository {
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String FIELD_PRE_REGISTRATION = "preRegistration";
    private static final String TOTAL = "total";
    private static final String DOLLAR_COND = "$cond";
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_DISPLAY_NAME = "displayName";
    private static final String FIELD_FIRST_NAME = "firstName";
    private static final String FIELD_LAST_NAME = "lastName";
    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_ADDITIONAL_INFO_EMAIL = "additionalInformation.email";
    private static final String FIELD_EXTERNAL_ID = "externalId";
    private static final String FIELD_IDENTITIES_USERNAME = "identities.username";
    private static final String FIELD_IDENTITIES_PROVIDER_ID = "identities.providerId";
    private static final String INDEX_REFERENCE_TYPE_REFERENCE_ID_USERNAME_SOURCE_NAME_UNIQUE = "rt1ri1u1s1_unique";

    private final Set<String> UNUSED_INDEXES = Set.of(
            "referenceType_1_referenceId_1_username_1_source_1",
            "rt1ri1u1s1",
            "rt1ri1"
    );

    private static final UserIdFields USER_ID_FIELDS = new UserIdFields(FIELD_ID, FIELD_EXTERNAL_ID, FIELD_SOURCE);

    protected MongoCollection<UserMongo> usersCollection;

    @Autowired
    private Environment environment;

    @PostConstruct
    public void init() {
        usersCollection = mongoDatabase.getCollection("users", UserMongo.class);
        MongoUtils.init(usersCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_EMAIL, 1), new IndexOptions().name("rt1ri1e1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_ADDITIONAL_INFO_EMAIL, 1), new IndexOptions().name("rt1ri1ae1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_USERNAME, 1), new IndexOptions().name("rt1ri1u1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_DISPLAY_NAME, 1), new IndexOptions().name("rt1ri1d1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_FIRST_NAME, 1), new IndexOptions().name("rt1ri1f1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_LAST_NAME, 1), new IndexOptions().name("rt1ri1l1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_EXTERNAL_ID, 1), new IndexOptions().name("rt1ri1ext1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_EXTERNAL_ID, 1).append(FIELD_SOURCE, 1), new IndexOptions().name("rt1ri1ext1s1"));
        indexes.put(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_IDENTITIES_USERNAME, 1).append(FIELD_IDENTITIES_PROVIDER_ID, 1), new IndexOptions().name("rt1ri1iu1ip1"));
        super.createIndex(usersCollection, indexes);
        createOrUpdateIndex();
    }

    @Override
    public Flowable<User> findAll(Reference reference) {
        return Flowable.fromPublisher(withMaxTime(usersCollection.find(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id())))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> findAll(Reference reference, int page, int size) {
        Single<Long> countOperation = Observable.fromPublisher(usersCollection.countDocuments(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id())), countOptions())).first(0l);
        Single<Set<User>> usersOperation = Observable.fromPublisher(withMaxTime(usersCollection.find(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id())))).sort(new BasicDBObject(FIELD_USERNAME, 1)).skip(size * page).limit(size)).map(this::convert).collect(LinkedHashSet::new, Set::add);
        return Single.zip(countOperation, usersOperation, (count, users) -> new Page<>(users, page, count))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> search(Reference reference, String query, int page, int size) {
        Bson searchQuery = or(
                new BasicDBObject(FIELD_USERNAME, query),
                new BasicDBObject(FIELD_EMAIL, query),
                new BasicDBObject(FIELD_ADDITIONAL_INFO_EMAIL, query),
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
                    new BasicDBObject(FIELD_ADDITIONAL_INFO_EMAIL, pattern),
                    new BasicDBObject(FIELD_DISPLAY_NAME, pattern),
                    new BasicDBObject(FIELD_FIRST_NAME, pattern),
                    new BasicDBObject(FIELD_LAST_NAME, pattern));
        }

        Bson mongoQuery = and(
                eq(FIELD_REFERENCE_TYPE, reference.type().name()),
                eq(FIELD_REFERENCE_ID, reference.id()),
                searchQuery);

        Single<Long> countOperation = Observable.fromPublisher(usersCollection.countDocuments(mongoQuery, countOptions())).first(0l);
        Single<Set<User>> usersOperation = Observable.fromPublisher(withMaxTime(usersCollection.find(mongoQuery)).sort(new BasicDBObject(FIELD_USERNAME, 1)).skip(size * page).limit(size)).map(this::convert).collect(LinkedHashSet::new, Set::add);
        return Single.zip(countOperation, usersOperation, (count, users) -> new Page<>(users, 0, count))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> search(Reference reference, FilterCriteria criteria, int page, int size) {
        try {
            BasicDBObject searchQuery = BasicDBObject.parse(filterCriteriaParser.parse(criteria));

            Bson mongoQuery = and(
                    eq(FIELD_REFERENCE_TYPE, reference.type().name()),
                    eq(FIELD_REFERENCE_ID, reference.id()),
                    searchQuery);

            Single<Long> countOperation = Observable.fromPublisher(usersCollection.countDocuments(mongoQuery, countOptions())).first(0l);
            Single<Set<User>> usersOperation = Observable.fromPublisher(withMaxTime(usersCollection.find(mongoQuery)).sort(new BasicDBObject(FIELD_USERNAME, 1)).skip(size * page).limit(size)).map(this::convert).collect(LinkedHashSet::new, Set::add);
            return Single.zip(countOperation, usersOperation, (count, users) -> new Page<>(users, 0, count))
                    .observeOn(Schedulers.computation());
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException) {
                return Single.error(ex);
            }
            logger.error("An error has occurred while searching users with criteria {}", criteria, ex);
            return Single.error(new TechnicalException("An error has occurred while searching users with filter criteria", ex));
        }

    }

    @Override
    public Flowable<User> search(Reference reference, FilterCriteria criteria) {
        try {
            BasicDBObject searchQuery = BasicDBObject.parse(filterCriteriaParser.parse(criteria));

            Bson mongoQuery = and(
                    eq(FIELD_REFERENCE_TYPE, reference.type().name()),
                    eq(FIELD_REFERENCE_ID, reference.id()),
                    searchQuery);

            return Flowable.fromPublisher(withMaxTime(usersCollection.find(mongoQuery)).sort(new BasicDBObject(FIELD_USERNAME, 1))).map(this::convert)
                    .observeOn(Schedulers.computation());
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException) {
                return Flowable.error(ex);
            }
            logger.error("An error has occurred while searching users with criteria {}", criteria, ex);
            return Flowable.error(new TechnicalException("An error has occurred while searching users with filter criteria", ex));
        }

    }

    @Override
    public Maybe<User> findByUsernameAndSource(Reference reference, String username, String source) {
        return Observable.fromPublisher(withMaxTime(
                        usersCollection
                                .find(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id()), eq(FIELD_USERNAME, username), eq(FIELD_SOURCE, source))))
                        .limit(1)
                        .first())
                .firstElement()
                .map(this::convert)
                .onErrorResumeNext(this::mapExceptionAsMaybe)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findByExternalIdAndSource(Reference reference, String externalId, String source) {
        return Observable.fromPublisher(withMaxTime(
                        usersCollection
                                .find(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id()), eq(FIELD_EXTERNAL_ID, externalId), eq(FIELD_SOURCE, source))))
                        .limit(1)
                        .first())
                .firstElement()
                .map(this::convert)
                .onErrorResumeNext(this::mapExceptionAsMaybe)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<User> findByIdIn(Reference reference, List<String> ids) {
        return Flowable.fromPublisher(withMaxTime(usersCollection.find(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id()), in(FIELD_ID, ids))))).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findById(Reference reference, UserId userId) {
        return Observable.fromPublisher(usersCollection.find(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id()), userIdMatches(userId, USER_ID_FIELDS))).first())
                .firstElement()
                .map(this::convert)
                .onErrorResumeNext(this::mapExceptionAsMaybe)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findById(String userId) {
        return Observable.fromPublisher(usersCollection.find(eq(FIELD_ID, userId)).first())
                .firstElement()
                .map(this::convert)
                .onErrorResumeNext(this::mapExceptionAsMaybe)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<User> create(User item) {
        UserMongo user = convert(item);
        user.setId(user.getId() == null ? RandomString.generate() : user.getId());
        return Single.fromPublisher(usersCollection.insertOne(user))
                .flatMap(success -> {
                    item.setId(user.getId());
                    return Single.just(item);
                }).onErrorResumeNext(this::mapExceptionAsSingle)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<User> update(User item) {
        UserMongo user = convert(item);
        return Single.fromPublisher(usersCollection.replaceOne(eq(FIELD_ID, user.getId()), user, new ReplaceOptions().upsert(acceptUpsert())))
                .flatMap(checkUpdatedRows(item))
                .onErrorResumeNext(this::mapExceptionAsSingle)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<User> update(User item, UpdateActions actions) {
        ArrayList<Bson> updateFields = generateUserUpdates(item, actions);
        Bson updates = Updates.combine(updateFields);
        return Single.fromPublisher(usersCollection.updateOne(eq(FIELD_ID, item.getId()), updates, new UpdateOptions().upsert(acceptUpsert())))
                .flatMap(checkUpdatedRows(item))
                .onErrorResumeNext(this::mapExceptionAsSingle)
                .observeOn(Schedulers.computation());
    }

    private Function<UpdateResult, SingleSource<? extends User>> checkUpdatedRows(User item) {
        return updateResult -> {
            if (!acceptUpsert() && updateResult.getMatchedCount() == 0) {
                return Single.error(new TechnicalException("Update query on unknown user with id '" + item.getId() + "'"));
            }
            return Single.just(item);
        };
    }

    protected ArrayList<Bson> generateUserUpdates(User item, UpdateActions actions) {
        var updateFields = new ArrayList<Bson>();
        updateFields.add(Updates.set(FIELD_EXTERNAL_ID, item.getExternalId()));
        updateFields.add(Updates.set(FIELD_USERNAME, item.getUsername()));
        updateFields.add(Updates.set(FIELD_EMAIL, item.getEmail()));
        updateFields.add(Updates.set(FIELD_DISPLAY_NAME, item.getDisplayName()));
        updateFields.add(Updates.set("nickName", item.getNickName()));
        updateFields.add(Updates.set(FIELD_FIRST_NAME, item.getFirstName()));
        updateFields.add(Updates.set(FIELD_LAST_NAME, item.getLastName()));
        updateFields.add(Updates.set("accountNonExpired", item.isAccountNonExpired()));
        updateFields.add(Updates.set("accountLockedAt", item.getAccountLockedAt()));
        updateFields.add(Updates.set("accountLockedUntil", item.getAccountLockedUntil()));
        updateFields.add(Updates.set("accountNonLocked", item.isAccountNonLocked()));
        updateFields.add(Updates.set("credentialsNonExpired", item.isCredentialsNonExpired()));
        updateFields.add(Updates.set("enabled", item.isEnabled()));
        updateFields.add(Updates.set("internal", item.isInternal()));
        updateFields.add(Updates.set("preRegistration", item.isPreRegistration()));
        updateFields.add(Updates.set("newsletter", item.isNewsletter()));
        updateFields.add(Updates.set("registrationUserUri", item.getRegistrationUserUri()));
        updateFields.add(Updates.set("registrationAccessToken", item.getRegistrationAccessToken()));
        updateFields.add(Updates.set("registrationCompleted", item.isRegistrationCompleted()));
        updateFields.add(Updates.set("referenceType", item.getReferenceType().name()));
        updateFields.add(Updates.set("referenceId", item.getReferenceId()));
        updateFields.add(Updates.set(FIELD_SOURCE, item.getSource()));
        updateFields.add(Updates.set("lastIdentityUsed", item.getLastIdentityUsed()));
        updateFields.add(Updates.set("client", item.getClient()));
        updateFields.add(Updates.set("loginsCount", item.getLoginsCount()));
        updateFields.add(Updates.set("loggedAt", item.getLoggedAt()));
        updateFields.add(Updates.set("lastLoginWithCredentials", item.getLastLoginWithCredentials()));
        updateFields.add(Updates.set("mfaEnrollmentSkippedAt", item.getMfaEnrollmentSkippedAt()));
        updateFields.add(Updates.set("lastPasswordReset", item.getLastPasswordReset()));
        updateFields.add(Updates.set("lastLogoutAt", item.getLastLogoutAt()));
        updateFields.add(Updates.set("lastUsernameReset", item.getLastUsernameReset()));
        updateFields.add(Updates.set("lastUsernameReset", item.getLastUsernameReset()));
        updateFields.add(Updates.set("x509Certificates", toMongoCertificates(item.getX509Certificates())));
        updateFields.add(Updates.set("factors", item.getFactors()));
        updateFields.add(Updates.set("type", item.getType()));
        updateFields.add(Updates.set("title", item.getTitle()));
        updateFields.add(Updates.set("preferredLanguage", item.getPreferredLanguage()));
        updateFields.add(Updates.set("additionalInformation", item.getAdditionalInformation() != null ? new Document(item.getAdditionalInformation()) : new Document()));
        updateFields.add(Updates.set("createdAt", item.getCreatedAt()));
        updateFields.add(Updates.set("updatedAt", item.getUpdatedAt()));
        // TODO password for OrgUsers
        if (actions.updateRole()) {
            updateFields.add(Updates.set("roles", item.getRoles()));
        }
        if (actions.updateDynamicRole()) {
            updateFields.add(Updates.set("dynamicRoles", item.getDynamicRoles()));
        }
        if (actions.updateDynamicGroup()) {
            updateFields.add(Updates.set("dynamicGroups", item.getDynamicGroups()));
        }
        if (actions.updateAddresses()) {
            updateFields.add(Updates.set("addresses", toMongoAddresses(item.getAddresses())));
        }
        if (actions.updateAttributes()) {
            updateFields.add(Updates.set("emails", toMongoAttributes(item.getEmails())));
            updateFields.add(Updates.set("phoneNumbers", toMongoAttributes(item.getPhoneNumbers())));
            updateFields.add(Updates.set("ims", toMongoAttributes(item.getIms())));
            updateFields.add(Updates.set("photos", toMongoAttributes(item.getPhotos())));
        }
        if (actions.updateEntitlements()) {
            updateFields.add(Updates.set("entitlements", item.getEntitlements()));
        }
        // TODO manage update Actions
        updateFields.add(Updates.set("identities", item.getIdentities()));
        updateFields.add(Updates.set("forceResetPassword", item.getForceResetPassword()));
        updateFields.add(Updates.set("serviceAccount", item.getServiceAccount()));
        return updateFields;
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(usersCollection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByReference(Reference reference) {
        return Completable.fromPublisher(usersCollection.deleteMany(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id()))))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<User> findByDomainAndEmail(String domain, String email, boolean strict) {
        BasicDBObject emailQuery = new BasicDBObject(FIELD_EMAIL, (strict) ? email : Pattern.compile(email, Pattern.CASE_INSENSITIVE));
        BasicDBObject emailClaimQuery = new BasicDBObject(FIELD_ADDITIONAL_INFO_EMAIL, (strict) ? email : Pattern.compile(email, Pattern.CASE_INSENSITIVE));
        Bson mongoQuery = and(
                eq(FIELD_REFERENCE_TYPE, DOMAIN.name()),
                eq(FIELD_REFERENCE_ID, domain),
                or(emailQuery, emailClaimQuery));

        return Flowable.fromPublisher(usersCollection.find(mongoQuery)).map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findById(UserId id) {
        return findOne(usersCollection, userIdMatches(id, USER_ID_FIELDS), this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findByUsernameAndDomain(String domain, String username) {
        var query = and(eq(FIELD_REFERENCE_TYPE, DOMAIN.name()), eq(FIELD_REFERENCE_ID, domain), eq(FIELD_USERNAME, username));
        return findOne(usersCollection, query, this::convert)
                .observeOn(Schedulers.computation());

    }

    @Override
    public Single<Long> countByReference(Reference reference) {
        return Observable.fromPublisher(usersCollection.countDocuments(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id())), countOptions())).first(0L)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Long> countByApplication(String domain, String application) {
        return Observable.fromPublisher(usersCollection.countDocuments(and(eq(FIELD_REFERENCE_TYPE, DOMAIN.name()), eq(FIELD_REFERENCE_ID, domain), eq(FIELD_CLIENT, application)), countOptions())).first(0L)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Map<Object, Object>> statistics(AnalyticsQuery query) {
        return switch (query.getField()) {
            case Field.USER_STATUS -> usersStatusRepartition(query);
            case Field.USER_REGISTRATION -> registrationsStatusRepartition(query);
            default -> Single.just(Collections.emptyMap());
        };

    }

    @Override
    public Maybe<User> findByUsernameAndSource(Reference reference, String username, String source, boolean includeLinkedIdentities) {
        return findByUsernameAndSource(reference, username, source)
                .switchIfEmpty(includeLinkedIdentities ? findByIdentityUsernameAndProviderId(reference, username, source) : Maybe.empty())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> findAllScim(Reference reference, int startIndex, int count) {
        Single<Long> countOperation = Observable.fromPublisher(usersCollection.countDocuments(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id())), countOptions())).first(0l);
        Single<Set<User>> usersOperation = Observable.fromPublisher(withMaxTime(usersCollection.find(and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id())))).sort(new BasicDBObject(FIELD_USERNAME, 1)).skip(startIndex).limit(count)).map(this::convert).collect(LinkedHashSet::new, Set::add);
        return Single.zip(countOperation, usersOperation, (totalResults, users) -> new Page<>(users, count > 0 ? (startIndex / count) : 0, totalResults))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> searchScim(Reference reference, FilterCriteria criteria, int startIndex, int count) {
        try {
            BasicDBObject searchQuery = BasicDBObject.parse(filterCriteriaParser.parse(criteria));

            Bson mongoQuery = and(
                    eq(FIELD_REFERENCE_TYPE, reference.type().name()),
                    eq(FIELD_REFERENCE_ID, reference.id()),
                    searchQuery);

            Single<Long> countOperation = Observable.fromPublisher(usersCollection.countDocuments(mongoQuery, countOptions())).first(0l);
            Single<Set<User>> usersOperation = Observable.fromPublisher(withMaxTime(usersCollection.find(mongoQuery)).sort(new BasicDBObject(FIELD_USERNAME, 1)).skip(startIndex).limit(count)).map(this::convert).collect(LinkedHashSet::new, Set::add);
            return Single.zip(countOperation, usersOperation, (totalCount, users) -> new Page<>(users, count > 0 ? (startIndex / count) : 0, totalCount))
                    .observeOn(Schedulers.computation());
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException) {
                return Single.error(ex);
            }
            logger.error("An error has occurred while searching users with criteria {}", criteria, ex);
            return Single.error(new TechnicalException("An error has occurred while searching users with filter criteria", ex));
        }
    }

    private Maybe<User> findByIdentityUsernameAndProviderId(Reference reference, String username, String providerId) {
        Bson query = and(eq(FIELD_REFERENCE_TYPE, reference.type().name()), eq(FIELD_REFERENCE_ID, reference.id()), eq(FIELD_IDENTITIES_USERNAME, username), eq(FIELD_IDENTITIES_PROVIDER_ID, providerId));
        return Observable.fromPublisher(withMaxTime(usersCollection.find(query))
                        .limit(1)
                        .first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    private Single<Map<Object, Object>> usersStatusRepartition(AnalyticsQuery query) {
        List<Bson> filters = new ArrayList<>(Arrays.asList(eq(FIELD_REFERENCE_TYPE, DOMAIN.name()), eq(FIELD_REFERENCE_ID, query.getDomain())));
        if (query.getApplication() != null && !query.getApplication().isEmpty()) {
            filters.add(eq(FIELD_CLIENT, query.getApplication()));
        }

        return Observable.fromPublisher(withMaxTime(usersCollection.aggregate(
                        Arrays.asList(
                                Aggregates.match(and(filters)),
                                Aggregates.group(
                                        new BasicDBObject("_id", query.getField()),
                                        Accumulators.sum(TOTAL, 1),
                                        Accumulators.sum("disabled", new BasicDBObject(DOLLAR_COND, Arrays.asList(new BasicDBObject("$eq", Arrays.asList("$enabled", false)), 1, 0))),
                                        Accumulators.sum("locked", new BasicDBObject(DOLLAR_COND, Arrays.asList(new BasicDBObject("$and", Arrays.asList(new BasicDBObject("$eq", Arrays.asList("$accountNonLocked", false)), new BasicDBObject("$gte", Arrays.asList("$accountLockedUntil", new Date())))), 1, 0))),
                                        Accumulators.sum("inactive", new BasicDBObject(DOLLAR_COND, Arrays.asList(new BasicDBObject("$lte", Arrays.asList("$loggedAt", new Date(Instant.now().minus(90, ChronoUnit.DAYS).toEpochMilli()))), 1, 0)))
                                )
                        ), Document.class)))
                .map(doc -> {
                    long nonActiveUsers = ((Number) doc.get("disabled")).longValue() + ((Number) doc.get("locked")).longValue() + ((Number) doc.get("inactive")).longValue();
                    long activeUsers = ((Number) doc.get(TOTAL)).longValue() - nonActiveUsers;
                    Map<Object, Object> users = new HashMap<>();
                    users.put("active", activeUsers);
                    users.putAll(doc.entrySet()
                            .stream()
                            .filter(e -> !"_id".equals(e.getKey()) && !TOTAL.equals(e.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                    return users;
                })
                .first(Collections.emptyMap())
                .observeOn(Schedulers.computation());
    }

    private Single<Map<Object, Object>> registrationsStatusRepartition(AnalyticsQuery query) {
        return Observable.fromPublisher(withMaxTime(usersCollection.aggregate(
                        Arrays.asList(
                                Aggregates.match(and(eq(FIELD_REFERENCE_TYPE, DOMAIN.name()), eq(FIELD_REFERENCE_ID, query.getDomain()), eq(FIELD_PRE_REGISTRATION, true))),
                                Aggregates.group(new BasicDBObject("_id", query.getField()),
                                        Accumulators.sum(TOTAL, 1),
                                        Accumulators.sum("completed", new BasicDBObject(DOLLAR_COND, Arrays.asList(new BasicDBObject("$eq", Arrays.asList("$registrationCompleted", true)), 1, 0))))
                        ), Document.class)))
                .map(doc -> (Map<Object, Object>) new HashMap<Object, Object>(doc.entrySet()
                        .stream()
                        .filter(e -> !"_id".equals(e.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .first(Collections.emptyMap())
                .observeOn(Schedulers.computation());
    }

    protected boolean acceptUpsert() {
        return environment != null && environment.getProperty("resilience.enabled", Boolean.class, false);
    }

    protected User convert(UserMongo userMongo) {
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
        user.setNewsletter(userMongo.getNewsletter());
        user.setRegistrationUserUri(userMongo.getRegistrationUserUri());
        user.setRegistrationAccessToken(userMongo.getRegistrationAccessToken());
        user.setReferenceType(ReferenceType.valueOf(userMongo.getReferenceType()));
        user.setReferenceId(userMongo.getReferenceId());
        user.setSource(userMongo.getSource());
        user.setClient(userMongo.getClient());
        user.setLoginsCount(userMongo.getLoginsCount());
        user.setLoggedAt(userMongo.getLoggedAt());
        user.setLastLoginWithCredentials(userMongo.getLastLoginWithCredentials());
        user.setMfaEnrollmentSkippedAt(userMongo.getMfaEnrollmentSkippedAt());
        user.setLastPasswordReset(userMongo.getLastPasswordReset());
        user.setLastUsernameReset(userMongo.getLastUsernameReset());
        user.setLastLogoutAt(userMongo.getLastLogoutAt());
        user.setRoles(userMongo.getRoles());
        user.setDynamicRoles(userMongo.getDynamicRoles());
        user.setDynamicGroups(userMongo.getDynamicGroups());
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
        user.setIdentities(userMongo.getIdentities());
        user.setLastIdentityUsed(userMongo.getLastIdentityUsed());
        user.setAdditionalInformation(userMongo.getAdditionalInformation());
        user.setCreatedAt(userMongo.getCreatedAt());
        user.setUpdatedAt(userMongo.getUpdatedAt());
        user.setForceResetPassword(userMongo.getForceResetPassword());
        user.setServiceAccount(userMongo.getServiceAccount());
        return user;
    }

    protected UserMongo convert(User user) {
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
        userMongo.setNewsletter(user.isNewsletter());
        userMongo.setRegistrationUserUri(user.getRegistrationUserUri());
        userMongo.setRegistrationAccessToken(user.getRegistrationAccessToken());
        userMongo.setReferenceType(user.getReferenceType().name());
        userMongo.setReferenceId(user.getReferenceId());
        userMongo.setSource(user.getSource());
        userMongo.setClient(user.getClient());
        userMongo.setLoginsCount(user.getLoginsCount());
        userMongo.setLoggedAt(user.getLoggedAt());
        userMongo.setLastLoginWithCredentials(user.getLastLoginWithCredentials());
        userMongo.setMfaEnrollmentSkippedAt(user.getMfaEnrollmentSkippedAt());
        userMongo.setLastPasswordReset(user.getLastPasswordReset());
        userMongo.setLastLogoutAt(user.getLastLogoutAt());
        userMongo.setLastUsernameReset(user.getLastUsernameReset());
        userMongo.setRoles(user.getRoles());
        userMongo.setDynamicRoles(user.getDynamicRoles());
        userMongo.setDynamicGroups(user.getDynamicGroups());
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
        userMongo.setIdentities(user.getIdentities());
        userMongo.setLastIdentityUsed(user.getLastIdentityUsed());
        userMongo.setAdditionalInformation(user.getAdditionalInformation() != null ? new Document(user.getAdditionalInformation()) : new Document());
        userMongo.setCreatedAt(user.getCreatedAt());
        userMongo.setUpdatedAt(user.getUpdatedAt());
        userMongo.setForceResetPassword(user.getForceResetPassword());
        userMongo.setServiceAccount(user.getServiceAccount());
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

    private void createOrUpdateIndex() {
        if (getEnsureIndexOnStart()) {
            MongoUtils.dropIndexes(usersCollection, UNUSED_INDEXES::contains)
                    .doOnComplete(() -> {
                        try {
                            super.createIndex(usersCollection, Map.of(new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_USERNAME, 1).append(FIELD_SOURCE, 1),
                                    new IndexOptions().name(INDEX_REFERENCE_TYPE_REFERENCE_ID_USERNAME_SOURCE_NAME_UNIQUE).unique(true)));
                        } catch (Exception e) {
                            logger.error("An error has occurred while creating index {} with unique constraints", INDEX_REFERENCE_TYPE_REFERENCE_ID_USERNAME_SOURCE_NAME_UNIQUE, e);
                        }
                    })
                    .subscribe();
        }
    }
}
