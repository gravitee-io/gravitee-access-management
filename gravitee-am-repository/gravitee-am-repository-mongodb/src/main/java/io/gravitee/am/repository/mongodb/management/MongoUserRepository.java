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
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import io.gravitee.am.common.analytics.Field;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.common.UserIdFields;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.repository.mongodb.management.internal.model.UserMongo;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.bson.conversions.Bson;
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
import static com.mongodb.client.model.Filters.or;
import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_CLIENT;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_ID;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_REFERENCE_TYPE;
/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoUserRepository extends AbstractUserRepository<UserMongo> implements UserRepository {
    // TODO [DP] class to remove
    private static final String FIELD_PRE_REGISTRATION = "preRegistration";
    private static final String TOTAL = "total";
    private static final String DOLLAR_COND = "$cond";

    private static final UserIdFields USER_ID_FIELDS = new UserIdFields(FIELD_ID, FIELD_EXTERNAL_ID, FIELD_SOURCE);

    @Autowired
    private Environment environment;

    @PostConstruct
    public void init() {
        super.initCollection("users");
    }

    @Override
    protected Class getMongoClass() {
        return UserMongo.class;
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
        return findOne(usersCollection, userIdMatches(id), this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<User> findByUsernameAndDomain(String domain, String username) {
        var query = and(eq(FIELD_REFERENCE_TYPE, DOMAIN.name()), eq(FIELD_REFERENCE_ID, domain), eq(FIELD_USERNAME, username));
        return findOne(usersCollection, query, this::convert)
                .observeOn(Schedulers.computation());

    }

    @Override
    public Single<Long> countByReference(ReferenceType referenceType, String referenceId) {
        return Observable.fromPublisher(usersCollection.countDocuments(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId)), countOptions())).first(0L)
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
    public Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source, boolean includeLinkedIdentities) {
        return findByUsernameAndSource(referenceType, referenceId, username, source)
                .switchIfEmpty(includeLinkedIdentities ? findByIdentityUsernameAndProviderId(referenceType, referenceId, username, source) : Maybe.empty())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> findAllScim(ReferenceType referenceType, String referenceId, int startIndex, int count) {
        Single<Long> countOperation = Observable.fromPublisher(usersCollection.countDocuments(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId)), countOptions())).first(0l);
        Single<Set<User>> usersOperation = Observable.fromPublisher(withMaxTime(usersCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId)))).sort(new BasicDBObject(FIELD_USERNAME, 1)).skip(startIndex).limit(count)).map(this::convert).collect(LinkedHashSet::new, Set::add);
        return Single.zip(countOperation, usersOperation, (totalResults, users) -> new Page<>(users, count > 0 ? (startIndex/count) : 0, totalResults))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<Page<User>> searchScim(ReferenceType referenceType, String referenceId, FilterCriteria criteria, int startIndex, int count) {
        try {
            BasicDBObject searchQuery = BasicDBObject.parse(filterCriteriaParser.parse(criteria));

            Bson mongoQuery = and(
                    eq(FIELD_REFERENCE_TYPE, referenceType.name()),
                    eq(FIELD_REFERENCE_ID, referenceId),
                    searchQuery);

            Single<Long> countOperation = Observable.fromPublisher(usersCollection.countDocuments(mongoQuery, countOptions())).first(0l);
            Single<Set<User>> usersOperation = Observable.fromPublisher(withMaxTime(usersCollection.find(mongoQuery)).sort(new BasicDBObject(FIELD_USERNAME, 1)).skip(startIndex).limit(count)).map(this::convert).collect(LinkedHashSet::new, Set::add);
            return Single.zip(countOperation, usersOperation, (totalCount, users) -> new Page<>(users, count > 0 ? (startIndex/count) : 0, totalCount))
                    .observeOn(Schedulers.computation());
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException) {
                return Single.error(ex);
            }
            logger.error("An error has occurred while searching users with criteria {}", criteria, ex);
            return Single.error(new TechnicalException("An error has occurred while searching users with filter criteria", ex));
        }

    }

    private Maybe<User> findByIdentityUsernameAndProviderId(ReferenceType referenceType, String referenceId, String username, String providerId){
        Bson query = and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_IDENTITIES_USERNAME, username), eq(FIELD_IDENTITIES_PROVIDER_ID, providerId));
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

    @Override
    protected UserMongo convert(User user) {
        return convert(user, new UserMongo());
    }

    @Override
    public Maybe<User> findById(String userId) {
        return super.findById(userId).onErrorResumeNext(this::mapExceptionAsMaybe);
    }

    @Override
    public Maybe<User> findById(Reference reference, UserId userId) {
        return super.findById(reference, userId).onErrorResumeNext(this::mapExceptionAsMaybe);
    }

    @Override
    public Maybe<User> findByExternalIdAndSource(ReferenceType referenceType, String referenceId, String externalId, String source) {
        return super.findByExternalIdAndSource(referenceType, referenceId, externalId, source).onErrorResumeNext(this::mapExceptionAsMaybe);
    }

    @Override
    public Maybe<User> findByUsernameAndSource(ReferenceType referenceType, String referenceId, String username, String source) {
        return super.findByUsernameAndSource(referenceType, referenceId, username, source).onErrorResumeNext(this::mapExceptionAsMaybe);
    }

    @Override
    public Single<User> create(User item) {
        return super.create(item).onErrorResumeNext(this::mapExceptionAsSingle);
    }

    @Override
    public Single<User> update(User item) {
        return super.update(item).onErrorResumeNext(this::mapExceptionAsSingle);
    }

    @Override
    public Single<User> update(User item, UpdateActions actions) {
        return super.update(item, actions).onErrorResumeNext(this::mapExceptionAsSingle);
    }


    @Override
    protected Bson userIdMatches(UserId user) {
        return super.userIdMatches(user, USER_ID_FIELDS);
    }

    @Override
    protected boolean acceptUpsert() {
        return environment != null && environment.getProperty("resilience.enabled", Boolean.class, false);
    }
}
