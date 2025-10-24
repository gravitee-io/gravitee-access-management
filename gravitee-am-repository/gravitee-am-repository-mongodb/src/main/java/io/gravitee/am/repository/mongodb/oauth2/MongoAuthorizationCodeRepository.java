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
import io.gravitee.am.repository.mongodb.oauth2.internal.model.AuthorizationCodeMongo;
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.or;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_ID;
/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Slf4j
public class MongoAuthorizationCodeRepository extends AbstractOAuth2MongoRepository implements AuthorizationCodeRepository {

    private static final String FIELD_TRANSACTION_ID = "transactionId";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_CLIENT_ID = "client_id";
    private static final String FIELD_EXPIRE_AT = "expire_at";
    private MongoCollection<AuthorizationCodeMongo> authorizationCodeCollection;

    @PostConstruct
    public void init() {
        authorizationCodeCollection = mongoOperations.getCollection("authorization_codes", AuthorizationCodeMongo.class);
        super.init(authorizationCodeCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_CODE, 1), new IndexOptions().name("c1"));
        indexes.put(new Document(FIELD_TRANSACTION_ID, 1), new IndexOptions().name("t1"));
        // expire after index
        indexes.put(new Document(FIELD_EXPIRE_AT, 1), new IndexOptions().expireAfter(0l, TimeUnit.SECONDS).name("e1"));

        super.createIndex(authorizationCodeCollection, indexes);
    }

    @Override
    public Single<AuthorizationCode> create(AuthorizationCode authorizationCode) {
        if (authorizationCode.getId() == null) {
            authorizationCode.setId(RandomString.generate());
        }

        log.debug("Create authorizationCode with id {}", authorizationCode);

        return Single
                .fromPublisher(authorizationCodeCollection.insertOne(convert(authorizationCode)))
                .flatMap(success -> Single.just(authorizationCode))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String code) {
        return Completable.fromPublisher(authorizationCodeCollection.deleteOne(eq(FIELD_ID, code)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationCode> findByCode(String code) {
        return Observable.fromPublisher(authorizationCodeCollection.find((and(eq(FIELD_CODE, code),
                or(gt(FIELD_EXPIRE_AT, new Date()), eq(FIELD_EXPIRE_AT, null))))).first()).firstElement().map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AuthorizationCode> findAndRemoveByCodeAndClientId(String code, String clientId) {
        return Observable.fromPublisher(authorizationCodeCollection.findOneAndDelete((and(eq(FIELD_CODE, code), eq(FIELD_CLIENT_ID, clientId),
                or(gt(FIELD_EXPIRE_AT, new Date()), eq(FIELD_EXPIRE_AT, null)))))).firstElement().map(this::convert);
    }

    private AuthorizationCode convert(AuthorizationCodeMongo authorizationCodeMongo) {
        if (authorizationCodeMongo == null) {
            return null;
        }

        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setId(authorizationCodeMongo.getId());
        authorizationCode.setTransactionId(authorizationCodeMongo.getTransactionId());
        authorizationCode.setContextVersion(authorizationCodeMongo.getContextVersion());
        authorizationCode.setCode(authorizationCodeMongo.getCode());
        authorizationCode.setClientId(authorizationCodeMongo.getClientId());
        authorizationCode.setCreatedAt(authorizationCodeMongo.getCreatedAt());
        authorizationCode.setExpireAt(authorizationCodeMongo.getExpireAt());
        authorizationCode.setSubject(authorizationCodeMongo.getSubject());
        authorizationCode.setScopes(authorizationCodeMongo.getScopes());
        authorizationCode.setResources(authorizationCodeMongo.getResources());

        if (authorizationCodeMongo.getRequestParameters() != null) {
            MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>();
            authorizationCodeMongo.getRequestParameters().forEach((key, value) -> requestParameters.put(key, (List<String>) value));
            authorizationCode.setRequestParameters(requestParameters);
        }
        return authorizationCode;
    }

    private AuthorizationCodeMongo convert(AuthorizationCode authorizationCode) {
        if (authorizationCode == null) {
            return null;
        }

        AuthorizationCodeMongo authorizationCodeMongo = new AuthorizationCodeMongo();
        authorizationCodeMongo.setId(authorizationCode.getId());
        authorizationCodeMongo.setTransactionId(authorizationCode.getTransactionId());
        authorizationCodeMongo.setContextVersion(authorizationCode.getContextVersion());
        authorizationCodeMongo.setCode(authorizationCode.getCode());
        authorizationCodeMongo.setClientId(authorizationCode.getClientId());
        authorizationCodeMongo.setCreatedAt(authorizationCode.getCreatedAt());
        authorizationCodeMongo.setExpireAt(authorizationCode.getExpireAt());
        authorizationCodeMongo.setSubject(authorizationCode.getSubject());
        authorizationCodeMongo.setScopes(authorizationCode.getScopes());
        authorizationCodeMongo.setResources(authorizationCode.getResources());

        if (authorizationCode.getRequestParameters() != null) {
            Document document = new Document();
            authorizationCode.getRequestParameters().forEach(document::append);
            authorizationCodeMongo.setRequestParameters(document);
        }

        return authorizationCodeMongo;
    }
}
