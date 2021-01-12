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
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.repository.management.api.AuthenticationFlowContextRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.AuthenticationFlowContextMongo;
import io.reactivex.*;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoAuthenticationFlowContextRepository extends AbstractManagementMongoRepository implements AuthenticationFlowContextRepository {

    private final static String FIELD_TRANSACTION_ID = "transactionId";
    private final static String FIELD_VERSION = "version";
    private static final String FIELD_RESET_TIME = "expire_at";

    private MongoCollection<AuthenticationFlowContextMongo> authContextCollection;

    @PostConstruct
    public void init() {
        authContextCollection = mongoOperations.getCollection("auth_flow_ctx", AuthenticationFlowContextMongo.class);
        super.init(authContextCollection);
        super.createIndex(authContextCollection, new Document(FIELD_TRANSACTION_ID, 1).append(FIELD_VERSION, -1));
        super.createIndex(authContextCollection, new Document(FIELD_RESET_TIME, 1), new IndexOptions().expireAfter(0l, TimeUnit.SECONDS));
    }

    @Override
    public Maybe<AuthenticationFlowContext> findById(String id) {
        return Observable.fromPublisher(authContextCollection.find(eq(FIELD_ID, id)).first()).firstElement().map(this::convert);
    }

    @Override
    public Maybe<AuthenticationFlowContext> findLastByTransactionId(String transactionId) {
        return Observable.fromPublisher(authContextCollection.find(and(eq(FIELD_TRANSACTION_ID, transactionId), gt(FIELD_RESET_TIME, new Date()))).sort(new BasicDBObject(FIELD_VERSION, -1)).first()).firstElement().map(this::convert);
    }

    @Override
    public Flowable<AuthenticationFlowContext> findByTransactionId(String transactionId) {
        return Flowable.fromPublisher(authContextCollection.find(and(eq(FIELD_TRANSACTION_ID, transactionId), gt(FIELD_RESET_TIME, new Date()))).sort(new BasicDBObject(FIELD_VERSION, -1))).map(this::convert);
    }

    @Override
    public Single<AuthenticationFlowContext> create(AuthenticationFlowContext context) {
        AuthenticationFlowContextMongo contextMongo = convert(context);
        contextMongo.setId(context.getTransactionId() + "-" + context.getVersion());
        return Single.fromPublisher(authContextCollection.insertOne(contextMongo))
                .flatMap(success -> findById(contextMongo.getId()).toSingle());
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
