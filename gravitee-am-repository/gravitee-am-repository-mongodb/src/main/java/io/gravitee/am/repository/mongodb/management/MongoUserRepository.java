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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.scim.Address;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.model.scim.Certificate;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.UserMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.AddressMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.AttributeMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.CertificateMongo;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.*;
import static io.gravitee.am.model.ReferenceType.DOMAIN;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoUserRepository extends AbstractUserRepository<UserMongo> implements UserRepository {

    private static final String FIELD_PRE_REGISTRATION = "preRegistration";

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
        BasicDBObject emailClaimQuery = new BasicDBObject(FIELD_EMAIL_CLAIM, (strict) ? email : Pattern.compile(email, Pattern.CASE_INSENSITIVE));
        Bson mongoQuery = and(
                eq(FIELD_REFERENCE_TYPE, DOMAIN.name()),
                eq(FIELD_REFERENCE_ID, domain),
                or(emailQuery, emailClaimQuery));

        return Flowable.fromPublisher(usersCollection.find(mongoQuery)).map(this::convert);
    }

    @Override
    public Maybe<User> findByUsernameAndDomain(String domain, String username) {
        return Observable.fromPublisher(
                usersCollection
                        .find(and(eq(FIELD_REFERENCE_TYPE, DOMAIN.name()), eq(FIELD_REFERENCE_ID, domain), eq(FIELD_USERNAME, username)))
                        .limit(1)
                        .first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Single<Long> countByReference(ReferenceType referenceType, String referenceId) {
        return Observable.fromPublisher(usersCollection.countDocuments(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId)))).first(0l);
    }

    @Override
    public Single<Long> countByApplication(String domain, String application) {
        return Observable.fromPublisher(usersCollection.countDocuments(and(eq(FIELD_REFERENCE_TYPE, DOMAIN.name()), eq(FIELD_REFERENCE_ID, domain), eq(FIELD_CLIENT, application)))).first(0l);
    }

    @Override
    public Single<Map<Object, Object>> statistics(AnalyticsQuery query) {
        switch (query.getField()) {
            case Field.USER_STATUS:
                return usersStatusRepartition(query);
            case Field.USER_REGISTRATION:
                return registrationsStatusRepartition(query);
        }

        return Single.just(Collections.emptyMap());
    }

    private Single<Map<Object, Object>> usersStatusRepartition(AnalyticsQuery query) {
        List<Bson> filters = new ArrayList<>(Arrays.asList(eq(FIELD_REFERENCE_TYPE, DOMAIN.name()), eq(FIELD_REFERENCE_ID, query.getDomain())));
        if (query.getApplication() != null && !query.getApplication().isEmpty()) {
            filters.add(eq(FIELD_CLIENT, query.getApplication()));
        }

        return Observable.fromPublisher(usersCollection.aggregate(
                Arrays.asList(
                        Aggregates.match(and(filters)),
                        Aggregates.group(
                                new BasicDBObject("_id", query.getField()),
                                Accumulators.sum("total", 1),
                                Accumulators.sum("disabled", new BasicDBObject("$cond", Arrays.asList(new BasicDBObject("$eq", Arrays.asList("$enabled", false)), 1, 0))),
                                Accumulators.sum("locked", new BasicDBObject("$cond", Arrays.asList(new BasicDBObject("$and", Arrays.asList(new BasicDBObject("$eq", Arrays.asList("$accountNonLocked", false)), new BasicDBObject("$gte", Arrays.asList("$accountLockedUntil", new Date())))), 1, 0))),
                                Accumulators.sum("inactive", new BasicDBObject("$cond", Arrays.asList(new BasicDBObject("$lte", Arrays.asList("$loggedAt", new Date(Instant.now().minus(90, ChronoUnit.DAYS).toEpochMilli()))), 1, 0)))
                        )
                ), Document.class))
                .map(doc -> {
                    Long nonActiveUsers = ((Number) doc.get("disabled")).longValue() + ((Number) doc.get("locked")).longValue() + ((Number) doc.get("inactive")).longValue();
                    Long activeUsers = ((Number) doc.get("total")).longValue() - nonActiveUsers;
                    Map<Object, Object> users = new HashMap<>();
                    users.put("active", activeUsers);
                    users.putAll(doc.entrySet()
                            .stream()
                            .filter(e -> !"_id".equals(e.getKey()) && !"total".equals(e.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                    return users;
                })
                .first(Collections.emptyMap());
    }

    private Single<Map<Object, Object>> registrationsStatusRepartition(AnalyticsQuery query) {
        return Observable.fromPublisher(usersCollection.aggregate(
                Arrays.asList(
                        Aggregates.match(and(eq(FIELD_REFERENCE_TYPE, DOMAIN.name()), eq(FIELD_REFERENCE_ID, query.getDomain()), eq(FIELD_PRE_REGISTRATION, true))),
                        Aggregates.group(new BasicDBObject("_id", query.getField()),
                                Accumulators.sum("total", 1),
                                Accumulators.sum("completed", new BasicDBObject("$cond", Arrays.asList(new BasicDBObject("$eq", Arrays.asList("$registrationCompleted", true)), 1, 0))))
                ), Document.class))
                .map(doc -> {
                    Map<Object, Object> registrations = new HashMap<>();
                    registrations.putAll(doc.entrySet()
                            .stream()
                            .filter(e -> !"_id".equals(e.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                    return registrations;
                })
                .first(Collections.emptyMap());
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

    @Override
    protected UserMongo convert(User user) {
        return convert(user, new UserMongo());
    }
}
