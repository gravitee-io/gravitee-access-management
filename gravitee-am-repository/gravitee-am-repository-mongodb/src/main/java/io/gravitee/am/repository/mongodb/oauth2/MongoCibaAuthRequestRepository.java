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
import io.gravitee.am.repository.mongodb.oauth2.internal.model.CibaAuthRequestMongo;
import io.gravitee.am.repository.oidc.api.CibaAuthRequestRepository;
import io.gravitee.am.repository.oidc.model.CibaAuthRequest;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoCibaAuthRequestRepository extends AbstractOAuth2MongoRepository implements CibaAuthRequestRepository {

    private MongoCollection<CibaAuthRequestMongo> cibaAuthRequestCollection;

    private static final String FIELD_EXPIRE_AT = "expire_at";
    private static final String FIELD_EXTERNAL_ID = "ext_transaction_id";

    @PostConstruct
    public void init() {
        cibaAuthRequestCollection = mongoOperations.getCollection("ciba_auth_requests", CibaAuthRequestMongo.class);
        super.init(cibaAuthRequestCollection);

        // expire after index
        super.createIndex(cibaAuthRequestCollection, new Document(FIELD_EXPIRE_AT, 1), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
    }

    @Override
    public Maybe<CibaAuthRequest> findById(String id) {
        return Observable
                .fromPublisher(cibaAuthRequestCollection.find(eq(FIELD_ID, id)).limit(1).first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Maybe<CibaAuthRequest> findByExternalId(String externalId) {
        return Observable
                .fromPublisher(cibaAuthRequestCollection.find(eq(FIELD_EXTERNAL_ID, externalId)).limit(1).first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Single<CibaAuthRequest> create(CibaAuthRequest authreq) {
        authreq.setId(authreq.getId() == null ? SecureRandomString.generate() : authreq.getId());
        return Single
                .fromPublisher(cibaAuthRequestCollection.insertOne(convert(authreq)))
                .flatMap(success -> findById(authreq.getId()).toSingle());
    }

    @Override
    public Single<CibaAuthRequest> update(CibaAuthRequest authreq) {
        return Single
                .fromPublisher(cibaAuthRequestCollection.replaceOne(eq(FIELD_ID, authreq.getId()), convert(authreq)))
                .flatMap(success -> findById(authreq.getId()).toSingle());
    }

    @Override
    public Single<CibaAuthRequest> updateStatus(String authReqId, String status) {
        return Single.fromPublisher(cibaAuthRequestCollection.updateOne(and(eq(FIELD_ID, authReqId)), Updates.set("status", status)))
                .flatMap(updateResult -> findById(authReqId).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(cibaAuthRequestCollection.findOneAndDelete(eq(FIELD_ID, id)));
    }

    private CibaAuthRequestMongo convert(CibaAuthRequest authReq) {
        if (authReq == null) {
            return null;
        }

        CibaAuthRequestMongo authReqMongo = new CibaAuthRequestMongo();
        authReqMongo.setId(authReq.getId());
        authReqMongo.setStatus(authReq.getStatus());
        authReqMongo.setSubject(authReq.getSubject());
        authReqMongo.setScopes(authReq.getScopes());
        authReqMongo.setClient(authReq.getClientId());
        authReqMongo.setDeviceNotifierId(authReq.getDeviceNotifierId());
        authReqMongo.setExpireAt(authReq.getExpireAt());
        authReqMongo.setCreatedAt(authReq.getCreatedAt());
        authReqMongo.setLastAccessAt(authReq.getLastAccessAt());
        authReqMongo.setExternalTrxId(authReq.getExternalTrxId());

        if (authReq.getExternalInformation() != null) {
            authReqMongo.setExternalInformation(new Document(authReq.getExternalInformation()));
        }

        return authReqMongo;
    }

    private CibaAuthRequest convert(CibaAuthRequestMongo authReqMongo) {
        if (authReqMongo == null) {
            return null;
        }

        CibaAuthRequest authReq = new CibaAuthRequest();
        authReq.setId(authReqMongo.getId());
        authReq.setStatus(authReqMongo.getStatus());
        authReq.setClientId(authReqMongo.getClient());
        authReq.setSubject(authReqMongo.getSubject());
        authReq.setScopes(authReqMongo.getScopes());
        authReq.setCreatedAt(authReqMongo.getCreatedAt());
        authReq.setExpireAt(authReqMongo.getExpireAt());
        authReq.setLastAccessAt(authReqMongo.getLastAccessAt());
        authReq.setExternalTrxId(authReqMongo.getExternalTrxId());
        authReq.setDeviceNotifierId(authReqMongo.getDeviceNotifierId());

        if (authReqMongo.getExternalInformation() != null) {
            authReq.setExternalInformation(new HashMap<>(authReqMongo.getExternalInformation()));
        }

        return authReq;
    }
}
