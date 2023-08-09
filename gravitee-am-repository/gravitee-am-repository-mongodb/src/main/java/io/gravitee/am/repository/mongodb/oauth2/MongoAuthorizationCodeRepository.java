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
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.bson.Document;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoAuthorizationCodeRepository extends AbstractOAuth2MongoRepository implements AuthorizationCodeRepository {

    private static final String FIELD_TRANSACTION_ID = "transactionId";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_EXPIRE_AT = "expire_at";
    private MongoCollection<AuthorizationCodeMongo> authorizationCodeCollection;

    @PostConstruct
    public void init() {
        authorizationCodeCollection = mongoOperations.getCollection("authorization_codes", AuthorizationCodeMongo.class);
        super.init(authorizationCodeCollection);
        super.createIndex(authorizationCodeCollection, new Document(FIELD_CODE, 1), new IndexOptions().name("c1"));
        super.createIndex(authorizationCodeCollection, new Document(FIELD_TRANSACTION_ID, 1), new IndexOptions().name("t1"));

        // expire after index
        super.createIndex(authorizationCodeCollection, new Document(FIELD_EXPIRE_AT, 1), new IndexOptions().expireAfter(0l, TimeUnit.SECONDS).name("e1"));
    }

    private Maybe<AuthorizationCode> findById(String id) {
        return Observable
                .fromPublisher(authorizationCodeCollection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Single<AuthorizationCode> create(AuthorizationCode authorizationCode) {
        if (authorizationCode.getId() == null) {
            authorizationCode.setId(RandomString.generate());
        }

        return Single
                .fromPublisher(authorizationCodeCollection.insertOne(convert(authorizationCode)))
                .flatMap(success -> Single.just(authorizationCode));
    }

    @Override
    public Maybe<AuthorizationCode> delete(String code) {
        return Observable.fromPublisher(authorizationCodeCollection.findOneAndDelete(eq(FIELD_ID, code))).firstElement().map(this::convert);
    }

    @Override
    public Maybe<AuthorizationCode> findByCode(String code) {
        return Observable.fromPublisher(authorizationCodeCollection.find(eq(FIELD_CODE, code)).first()).firstElement().map(this::convert);
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

        if (authorizationCode.getRequestParameters() != null) {
            Document document = new Document();
            authorizationCode.getRequestParameters().forEach(document::append);
            authorizationCodeMongo.setRequestParameters(document);
        }
        return authorizationCodeMongo;
    }
}
