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
import io.gravitee.am.repository.mongodb.oauth2.internal.model.PushedAuthorizationRequestMongo;
import io.gravitee.am.repository.oauth2.api.PushedAuthorizationRequestRepository;
import io.gravitee.am.repository.oauth2.model.PushedAuthorizationRequest;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.or;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoPushedAuthorizationRequestRepository extends AbstractOAuth2MongoRepository implements PushedAuthorizationRequestRepository {

    private MongoCollection<PushedAuthorizationRequestMongo> parCollection;

    private static final String FIELD_EXPIRE_AT = "expire_at";

    @PostConstruct
    public void init() {
        parCollection = mongoOperations.getCollection("pushed_authorization_requests", PushedAuthorizationRequestMongo.class);
        super.init(parCollection);

        // expire after index
        super.createIndex(parCollection, Map.of(new Document(FIELD_EXPIRE_AT, 1), new IndexOptions().name("e1").expireAfter(0L, TimeUnit.SECONDS)));
    }

    @Override
    public Maybe<PushedAuthorizationRequest> findById(String id) {
        return Observable
                .fromPublisher(parCollection.find(and(eq(FIELD_ID, id),
                        or(gt(FIELD_EXPIRE_AT, new Date()), eq(FIELD_EXPIRE_AT, null)))).limit(1).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<PushedAuthorizationRequest> create(PushedAuthorizationRequest item) {
        final PushedAuthorizationRequestMongo par = convert(item);
        par.setId(item.getId() == null ? RandomString.generate() : item.getId());
        return Single
                .fromPublisher(parCollection.insertOne(par))
                .flatMap(success -> { item.setId(par.getId()); return Single.just(item); })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(parCollection.findOneAndDelete(eq(FIELD_ID, id)))
                .observeOn(Schedulers.computation());
    }

    private PushedAuthorizationRequestMongo convert(PushedAuthorizationRequest par) {
        if (par == null) {
            return null;
        }

        PushedAuthorizationRequestMongo parMongo = new PushedAuthorizationRequestMongo();
        parMongo.setId(par.getId());
        parMongo.setDomain(par.getDomain());
        parMongo.setClient(par.getClient());
        parMongo.setCreatedAt(par.getCreatedAt());
        parMongo.setExpireAt(par.getExpireAt());

        if (par.getParameters() != null) {
            Document document = new Document();
            par.getParameters().forEach(document::append);
            parMongo.setParameters(document);
        }

        return parMongo;
    }

    private PushedAuthorizationRequest convert(PushedAuthorizationRequestMongo parMongo) {
        if (parMongo == null) {
            return null;
        }

        PushedAuthorizationRequest par = new PushedAuthorizationRequest();
        par.setId(parMongo.getId());
        par.setDomain(parMongo.getDomain());
        par.setClient(parMongo.getClient());
        par.setCreatedAt(parMongo.getCreatedAt());
        par.setExpireAt(parMongo.getExpireAt());

        if (parMongo.getParameters() != null) {
            MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
            parMongo.getParameters().entrySet().forEach(entry -> parameters.put(entry.getKey(), (List<String>) entry.getValue()));
            par.setParameters(parameters);
        }

        return par;
    }
}
