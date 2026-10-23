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

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.repository.exceptions.UniqueConstraintViolationException;
import io.gravitee.am.repository.management.api.AuthorizationDetailTypeRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AuthorizationDetailTypeMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author GraviteeSource Team
 */
@Component
public class MongoAuthorizationDetailTypeRepository extends AbstractManagementMongoRepository implements AuthorizationDetailTypeRepository {

    private static final String COLLECTION_NAME = "authorization_detail_types";
    private static final String FIELD_TYPE = "type";

    private MongoCollection<AuthorizationDetailTypeMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection(COLLECTION_NAME, AuthorizationDetailTypeMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(new Document(FIELD_DOMAIN, 1).append(FIELD_TYPE, 1), indexOptionsWithCollation("d1t1_ci").unique(true)));
    }

    @Override
    public Maybe<AuthorizationDetailType> findById(String id) {
        return Observable.fromPublisher(collection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Flowable<AuthorizationDetailType> findByDomain(String domain) {
        return Flowable.fromPublisher(withMaxTime(collection.find(eq(FIELD_DOMAIN, domain))).collation(CASE_INSENSITIVE_COLLATION))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationDetailType> findByDomainAndId(String domain, String id) {
        return Observable.fromPublisher(collection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_ID, id))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationDetailType> findByDomainAndType(String domain, String type) {
        return Observable.fromPublisher(collection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_TYPE, type))).collation(CASE_INSENSITIVE_COLLATION).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationDetailType> create(AuthorizationDetailType item) {
        item.setId(item.getId() == null ? RandomString.generate() : item.getId());
        return Single.fromPublisher(collection.insertOne(convert(item)))
                .map(__ -> item)
                .onErrorResumeNext(ex -> Single.error(isDuplicateKey(ex)
                        ? new UniqueConstraintViolationException("Authorization detail type " + item.getType() + " already exists in domain " + item.getDomain(), ex)
                        : ex))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AuthorizationDetailType> update(AuthorizationDetailType item) {
        AuthorizationDetailTypeMongo entity = convert(item);
        return Single.fromPublisher(collection.replaceOne(eq(FIELD_ID, entity.getId()), entity))
                .flatMap(updateResult -> Single.just(item))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.deleteOne(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomain(String domain) {
        return Completable.fromPublisher(collection.deleteMany(eq(FIELD_DOMAIN, domain)))
                .observeOn(Schedulers.computation());
    }

    private AuthorizationDetailType convert(AuthorizationDetailTypeMongo mongo) {
        if (mongo == null) {
            return null;
        }
        return AuthorizationDetailType.builder()
                .id(mongo.getId())
                .domain(mongo.getDomain())
                .type(mongo.getType())
                .name(mongo.getName())
                .description(mongo.getDescription())
                .schema(mongo.getSchema())
                .discovery(mongo.isDiscovery())
                .expiresIn(mongo.getExpiresIn())
                .createdAt(mongo.getCreatedAt())
                .updatedAt(mongo.getUpdatedAt())
                .build();
    }

    private AuthorizationDetailTypeMongo convert(AuthorizationDetailType type) {
        if (type == null) {
            return null;
        }
        AuthorizationDetailTypeMongo mongo = new AuthorizationDetailTypeMongo();
        mongo.setId(type.getId());
        mongo.setDomain(type.getDomain());
        mongo.setType(type.getType());
        mongo.setName(type.getName());
        mongo.setDescription(type.getDescription());
        mongo.setSchema(type.getSchema());
        mongo.setDiscovery(type.isDiscovery());
        mongo.setExpiresIn(type.getExpiresIn());
        mongo.setCreatedAt(type.getCreatedAt());
        mongo.setUpdatedAt(type.getUpdatedAt());
        return mongo;
    }

    private static boolean isDuplicateKey(Throwable ex) {
        return ex instanceof MongoWriteException writeException
                && ErrorCategory.fromErrorCode(writeException.getError().getCode()) == ErrorCategory.DUPLICATE_KEY;
    }
}
