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
package io.gravitee.am.repository.mongodb.oauth2;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.repository.mongodb.oauth2.internal.model.AAuthBootstrapRequestMongo;
import io.gravitee.am.repository.oidc.api.AAuthBootstrapRequestRepository;
import io.gravitee.am.repository.oidc.model.AAuthBootstrapRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;

/**
 * MongoDB repository for AAUTH bootstrap requests.
 * Follows the same pattern as {@link MongoAAuthPendingRequestRepository}.
 *
 * @author GraviteeSource Team
 */
@Component
public class MongoAAuthBootstrapRequestRepository extends AbstractOAuth2MongoRepository implements AAuthBootstrapRequestRepository {

    private static final String COLLECTION = "aauth_bootstrap_requests";
    private static final String FIELD_EXPIRE_AT = "expire_at";
    private static final String FIELD_INTERACTION_CODE = "interaction_code";
    private static final String FIELD_EPHEMERAL_KEY_THUMBPRINT = "ephemeral_key_thumbprint";

    private MongoCollection<AAuthBootstrapRequestMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection(COLLECTION, AAuthBootstrapRequestMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(
                new Document(FIELD_EXPIRE_AT, 1), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS).name("e1"),
                new Document(FIELD_INTERACTION_CODE, 1), new IndexOptions().name("ic1"),
                new Document(FIELD_EPHEMERAL_KEY_THUMBPRINT, 1), new IndexOptions().name("ekt1")
        ));
    }

    @Override
    public Maybe<AAuthBootstrapRequest> findById(String id) {
        return Observable
                .fromPublisher(collection.find(and(eq(FIELD_ID, id), gte(FIELD_EXPIRE_AT, new Date()))).limit(1).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AAuthBootstrapRequest> findByInteractionCode(String code) {
        return Observable
                .fromPublisher(collection.find(and(eq(FIELD_INTERACTION_CODE, code), gte(FIELD_EXPIRE_AT, new Date()))).limit(1).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AAuthBootstrapRequest> findByEphemeralKeyThumbprint(String thumbprint) {
        return Observable
                .fromPublisher(collection.find(and(eq(FIELD_EPHEMERAL_KEY_THUMBPRINT, thumbprint), gte(FIELD_EXPIRE_AT, new Date()))).limit(1).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthBootstrapRequest> create(AAuthBootstrapRequest request) {
        request.setId(request.getId() == null ? SecureRandomString.generate() : request.getId());
        return Single
                .fromPublisher(collection.insertOne(convert(request)))
                .flatMap(success -> findById(request.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthBootstrapRequest> update(AAuthBootstrapRequest request) {
        return Single
                .fromPublisher(collection.replaceOne(eq(FIELD_ID, request.getId()), convert(request)))
                .flatMap(success -> findById(request.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthBootstrapRequest> updateStatus(String id, String status) {
        return Single.fromPublisher(collection.updateOne(eq(FIELD_ID, id), Updates.set("status", status)))
                .flatMap(result -> findById(id).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.findOneAndDelete(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private AAuthBootstrapRequestMongo convert(AAuthBootstrapRequest req) {
        if (req == null) return null;

        AAuthBootstrapRequestMongo mongo = new AAuthBootstrapRequestMongo();
        mongo.setId(req.getId());
        mongo.setStatus(req.getStatus());
        mongo.setDomain(req.getDomain());
        mongo.setAgentServerUrl(req.getAgentServerUrl());
        mongo.setAgentServerName(req.getAgentServerName());
        mongo.setAgentServerLogoUri(req.getAgentServerLogoUri());
        mongo.setEphemeralKeyJwk(req.getEphemeralKeyJwk());
        mongo.setEphemeralKeyThumbprint(req.getEphemeralKeyThumbprint());
        mongo.setInteractionCode(req.getInteractionCode());
        mongo.setBootstrapToken(req.getBootstrapToken());
        mongo.setUserId(req.getUserId());
        mongo.setPairwiseSub(req.getPairwiseSub());
        mongo.setDomainHint(req.getDomainHint());
        mongo.setLoginHint(req.getLoginHint());
        mongo.setTenant(req.getTenant());
        mongo.setCreatedAt(req.getCreatedAt());
        mongo.setLastAccessAt(req.getLastAccessAt());
        mongo.setExpireAt(req.getExpireAt());
        return mongo;
    }

    private AAuthBootstrapRequest convert(AAuthBootstrapRequestMongo mongo) {
        if (mongo == null) return null;

        AAuthBootstrapRequest req = new AAuthBootstrapRequest();
        req.setId(mongo.getId());
        req.setStatus(mongo.getStatus());
        req.setDomain(mongo.getDomain());
        req.setAgentServerUrl(mongo.getAgentServerUrl());
        req.setAgentServerName(mongo.getAgentServerName());
        req.setAgentServerLogoUri(mongo.getAgentServerLogoUri());
        req.setEphemeralKeyJwk(mongo.getEphemeralKeyJwk());
        req.setEphemeralKeyThumbprint(mongo.getEphemeralKeyThumbprint());
        req.setInteractionCode(mongo.getInteractionCode());
        req.setBootstrapToken(mongo.getBootstrapToken());
        req.setUserId(mongo.getUserId());
        req.setPairwiseSub(mongo.getPairwiseSub());
        req.setDomainHint(mongo.getDomainHint());
        req.setLoginHint(mongo.getLoginHint());
        req.setTenant(mongo.getTenant());
        req.setCreatedAt(mongo.getCreatedAt());
        req.setLastAccessAt(mongo.getLastAccessAt());
        req.setExpireAt(mongo.getExpireAt());
        return req;
    }
}
