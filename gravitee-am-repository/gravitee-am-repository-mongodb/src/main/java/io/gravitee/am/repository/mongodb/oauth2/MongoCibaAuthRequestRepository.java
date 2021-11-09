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
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
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
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoCibaAuthRequestRepository extends AbstractOAuth2MongoRepository implements CibaAuthRequestRepository {

    private MongoCollection<CibaAuthRequestMongo> cibaAuthRequestCollection;

    private static final String FIELD_EXPIRE_AT = "expire_at";

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
    public Single<CibaAuthRequest> create(CibaAuthRequest authreq) {
        authreq.setId(authreq.getId() == null ? RandomString.generate() : authreq.getId());
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
        authReqMongo.setUserCode(authReq.getUserCode());
        authReqMongo.setExpireAt(authReq.getExpireAt());
        authReqMongo.setCreatedAt(authReq.getCreatedAt());
        authReqMongo.setLastAccessAt(authReq.getLastAccessAt());

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
        authReq.setUserCode(authReqMongo.getUserCode());
        authReq.setCreatedAt(authReqMongo.getCreatedAt());
        authReq.setExpireAt(authReqMongo.getExpireAt());
        authReq.setLastAccessAt(authReqMongo.getLastAccessAt());

        return authReq;
    }
}
