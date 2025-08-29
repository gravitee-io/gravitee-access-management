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
package io.gravitee.am.repository.mongodb.gateway;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.repository.gateway.api.AuthenticationFlowContextRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AuthenticationFlowContextMongo;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoAuthenticationFlowContextRepository extends AbstractGatewayMongoRepository implements AuthenticationFlowContextRepository {

    private static final String FIELD_TRANSACTION_ID = "transactionId";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_EXPIRES_AT = "expire_at";

    private MongoCollection<AuthenticationFlowContextMongo> authContextCollection;

    @Autowired
    private Environment environment;

    @PostConstruct
    public void init() {
        authContextCollection = mongoOperations.getCollection("auth_flow_ctx", AuthenticationFlowContextMongo.class);
        super.init(authContextCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_TRANSACTION_ID, 1).append(FIELD_VERSION, -1), new IndexOptions().name("t1v_1"));
        indexes.put(new Document(FIELD_EXPIRES_AT, 1), new IndexOptions().name("e1").expireAfter(0L, TimeUnit.SECONDS));

        super.createIndex(authContextCollection, indexes, getEnsureIndexOnStart());
    }

    @Override
    public Maybe<AuthenticationFlowContext> findById(String id) {
        return Observable.fromPublisher(authContextCollection.find(and(eq(FIELD_ID, id), gt(FIELD_EXPIRES_AT, new Date()))).first()).firstElement().map(this::convert);
    }

    @Override
    public Maybe<AuthenticationFlowContext> findLastByTransactionId(String transactionId) {
        return Observable.fromPublisher(authContextCollection.find(and(eq(FIELD_TRANSACTION_ID, transactionId), gt(FIELD_EXPIRES_AT, new Date()))).sort(new BasicDBObject(Map.of(FIELD_VERSION, -1, "createdAt", -1))).first()).firstElement().map(this::convert);
    }

    @Override
    public Flowable<AuthenticationFlowContext> findByTransactionId(String transactionId) {
        return Flowable.fromPublisher(authContextCollection.find(and(eq(FIELD_TRANSACTION_ID, transactionId), gt(FIELD_EXPIRES_AT, new Date()))).sort(new BasicDBObject(Map.of(FIELD_VERSION, -1, "createdAt", -1)))).map(this::convert);
    }

    @Override
    public Single<AuthenticationFlowContext> create(AuthenticationFlowContext context) {
        AuthenticationFlowContextMongo contextMongo = convert(context);
        contextMongo.setId(context.identifier());
        return Single.fromPublisher(authContextCollection.insertOne(contextMongo))
                .flatMap(success -> Single.just(context));
    }

    @Override
    public Single<AuthenticationFlowContext> replace(AuthenticationFlowContext context) {
        AuthenticationFlowContextMongo contextMongo = convert(context);
        contextMongo.setId(context.identifier());
        return Single.fromPublisher(authContextCollection
                        .replaceOne(eq(FIELD_ID, contextMongo.getId()), contextMongo, new ReplaceOptions().upsert(true)))
                .flatMap(success -> Single.just(context));
    }


    @Override
    public Completable delete(String transactionId) {
        return Completable.fromPublisher(authContextCollection.deleteMany(eq(FIELD_TRANSACTION_ID, transactionId)));
    }

    @Override
    public Completable delete(String transactionId, int version) {
        return Completable.fromPublisher(authContextCollection.deleteOne(and(eq(FIELD_TRANSACTION_ID, transactionId), eq(FIELD_VERSION, version))));
    }

    private AuthenticationFlowContext convert(AuthenticationFlowContextMongo entity) {
        AuthenticationFlowContext bean = new AuthenticationFlowContext();
        bean.setTransactionId(entity.getTransactionId());
        bean.setVersion(entity.getVersion());
        bean.setData(entity.getData() == null ? null : entity.getData());
        bean.setCreatedAt(entity.getCreatedAt());
        bean.setExpireAt(entity.getExpireAt());
        return bean;
    }

    private AuthenticationFlowContextMongo convert(AuthenticationFlowContext bean) {
        AuthenticationFlowContextMongo entity = new AuthenticationFlowContextMongo();
        entity.setTransactionId(bean.getTransactionId());
        entity.setVersion(bean.getVersion());
        entity.setData(bean.getData() == null ? null : new Document(bean.getData()));
        entity.setCreatedAt(bean.getCreatedAt());
        entity.setExpireAt(bean.getExpireAt());
        return entity;
    }

}
