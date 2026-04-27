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
import io.gravitee.am.repository.mongodb.oauth2.internal.model.AAuthPendingRequestMongo;
import io.gravitee.am.repository.oidc.api.AAuthPendingRequestRepository;
import io.gravitee.am.repository.oidc.model.AAuthPendingRequest;
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
 * MongoDB repository for AAUTH pending requests.
 * Follows the same pattern as {@link MongoCibaAuthRequestRepository}.
 *
 * @author GraviteeSource Team
 */
@Component
public class MongoAAuthPendingRequestRepository extends AbstractOAuth2MongoRepository implements AAuthPendingRequestRepository {

    private static final String COLLECTION = "aauth_pending_requests";
    private static final String FIELD_EXPIRE_AT = "expire_at";
    private static final String FIELD_INTERACTION_CODE = "interaction_code";

    private MongoCollection<AAuthPendingRequestMongo> collection;

    @PostConstruct
    public void init() {
        collection = mongoOperations.getCollection(COLLECTION, AAuthPendingRequestMongo.class);
        super.init(collection);
        super.createIndex(collection, Map.of(
                new Document(FIELD_EXPIRE_AT, 1), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS).name("e1"),
                new Document(FIELD_INTERACTION_CODE, 1), new IndexOptions().name("ic1")
        ));
    }

    @Override
    public Maybe<AAuthPendingRequest> findById(String id) {
        return Observable
                .fromPublisher(collection.find(and(eq(FIELD_ID, id), gte(FIELD_EXPIRE_AT, new Date()))).limit(1).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AAuthPendingRequest> findByInteractionCode(String interactionCode) {
        return Observable
                .fromPublisher(collection.find(and(eq(FIELD_INTERACTION_CODE, interactionCode), gte(FIELD_EXPIRE_AT, new Date()))).limit(1).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthPendingRequest> create(AAuthPendingRequest request) {
        request.setId(request.getId() == null ? SecureRandomString.generate() : request.getId());
        return Single
                .fromPublisher(collection.insertOne(convert(request)))
                .flatMap(success -> findById(request.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthPendingRequest> update(AAuthPendingRequest request) {
        return Single
                .fromPublisher(collection.replaceOne(eq(FIELD_ID, request.getId()), convert(request)))
                .flatMap(success -> findById(request.getId()).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AAuthPendingRequest> updateStatus(String id, String status) {
        return Single.fromPublisher(collection.updateOne(eq(FIELD_ID, id), Updates.set("status", status)))
                .flatMap(result -> findById(id).toSingle())
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(collection.findOneAndDelete(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private AAuthPendingRequestMongo convert(AAuthPendingRequest req) {
        if (req == null) return null;

        AAuthPendingRequestMongo mongo = new AAuthPendingRequestMongo();
        mongo.setId(req.getId());
        mongo.setStatus(req.getStatus());
        mongo.setDomain(req.getDomain());
        mongo.setAgentServerUrl(req.getAgentServerUrl());
        mongo.setAgentIdentifier(req.getAgentIdentifier());
        mongo.setAgentJkt(req.getAgentJkt());
        mongo.setAgentPublicKey(req.getAgentPublicKey());
        mongo.setApplicationId(req.getApplicationId());
        mongo.setResourceIss(req.getResourceIss());
        mongo.setScope(req.getScope());
        mongo.setJustification(req.getJustification());
        mongo.setLoginHint(req.getLoginHint());
        mongo.setDomainHint(req.getDomainHint());
        mongo.setTenant(req.getTenant());
        mongo.setInteractionCode(req.getInteractionCode());
        mongo.setPsIssuerUrl(req.getPsIssuerUrl());
        mongo.setAuthToken(req.getAuthToken());
        mongo.setAuthTokenExpiresIn(req.getAuthTokenExpiresIn());
        mongo.setUserId(req.getUserId());
        mongo.setClarification(req.getClarification());
        mongo.setClarificationResponse(req.getClarificationResponse());
        mongo.setClarificationSupported(req.isClarificationSupported());
        mongo.setClarificationRoundCount(req.getClarificationRoundCount());
        mongo.setCreatedAt(req.getCreatedAt());
        mongo.setLastAccessAt(req.getLastAccessAt());
        mongo.setExpireAt(req.getExpireAt());
        return mongo;
    }

    private AAuthPendingRequest convert(AAuthPendingRequestMongo mongo) {
        if (mongo == null) return null;

        AAuthPendingRequest req = new AAuthPendingRequest();
        req.setId(mongo.getId());
        req.setStatus(mongo.getStatus());
        req.setDomain(mongo.getDomain());
        req.setAgentServerUrl(mongo.getAgentServerUrl());
        req.setAgentIdentifier(mongo.getAgentIdentifier());
        req.setAgentJkt(mongo.getAgentJkt());
        req.setAgentPublicKey(mongo.getAgentPublicKey());
        req.setApplicationId(mongo.getApplicationId());
        req.setResourceIss(mongo.getResourceIss());
        req.setScope(mongo.getScope());
        req.setJustification(mongo.getJustification());
        req.setLoginHint(mongo.getLoginHint());
        req.setDomainHint(mongo.getDomainHint());
        req.setTenant(mongo.getTenant());
        req.setInteractionCode(mongo.getInteractionCode());
        req.setPsIssuerUrl(mongo.getPsIssuerUrl());
        req.setAuthToken(mongo.getAuthToken());
        req.setAuthTokenExpiresIn(mongo.getAuthTokenExpiresIn());
        req.setUserId(mongo.getUserId());
        req.setClarification(mongo.getClarification());
        req.setClarificationResponse(mongo.getClarificationResponse());
        req.setClarificationSupported(mongo.isClarificationSupported());
        req.setClarificationRoundCount(mongo.getClarificationRoundCount());
        req.setCreatedAt(mongo.getCreatedAt());
        req.setLastAccessAt(mongo.getLastAccessAt());
        req.setExpireAt(mongo.getExpireAt());
        return req;
    }
}
