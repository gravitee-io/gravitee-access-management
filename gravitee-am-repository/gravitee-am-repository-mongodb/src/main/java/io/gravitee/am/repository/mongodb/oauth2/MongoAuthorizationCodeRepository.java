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
import io.gravitee.am.repository.mongodb.common.IdGenerator;
import io.gravitee.am.repository.mongodb.common.LoggableIndexSubscriber;
import io.gravitee.am.repository.mongodb.oauth2.internal.model.AuthorizationCodeMongo;
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.model.AuthorizationCode;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoAuthorizationCodeRepository extends AbstractOAuth2MongoRepository implements AuthorizationCodeRepository {

    private static final String FIELD_ID = "_id";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_RESET_TIME = "expire_at";
    private MongoCollection<AuthorizationCodeMongo> authorizationCodeCollection;

    @Autowired
    private IdGenerator idGenerator;

    @PostConstruct
    public void init() {
        authorizationCodeCollection = mongoOperations.getCollection("authorization_codes", AuthorizationCodeMongo.class);
        authorizationCodeCollection.createIndex(new Document(FIELD_CODE, 1)).subscribe(new LoggableIndexSubscriber());
        authorizationCodeCollection.createIndex(new Document(FIELD_RESET_TIME, 1), new IndexOptions().expireAfter(0l, TimeUnit.SECONDS)).subscribe(new LoggableIndexSubscriber());
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
            authorizationCode.setId((String) idGenerator.generate());
        }

        return Single
                .fromPublisher(authorizationCodeCollection.insertOne(convert(authorizationCode)))
                .flatMap(success -> findById(authorizationCode.getId()).toSingle());
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
        authorizationCode.setCode(authorizationCodeMongo.getCode());
        authorizationCode.setClientId(authorizationCodeMongo.getClientId());
        authorizationCode.setCreatedAt(authorizationCodeMongo.getCreatedAt());
        authorizationCode.setExpireAt(authorizationCodeMongo.getExpireAt());
        authorizationCode.setSubject(authorizationCodeMongo.getSubject());
        authorizationCode.setRedirectUri(authorizationCodeMongo.getRedirectUri());
        authorizationCode.setScopes(authorizationCodeMongo.getScopes());

        return authorizationCode;
    }

    private AuthorizationCodeMongo convert(AuthorizationCode authorizationCode) {
        if (authorizationCode == null) {
            return null;
        }

        AuthorizationCodeMongo authorizationCodeMongo = new AuthorizationCodeMongo();
        authorizationCodeMongo.setId(authorizationCode.getId());
        authorizationCodeMongo.setCode(authorizationCode.getCode());
        authorizationCodeMongo.setClientId(authorizationCode.getClientId());
        authorizationCodeMongo.setCreatedAt(authorizationCode.getCreatedAt());
        authorizationCodeMongo.setExpireAt(authorizationCode.getExpireAt());
        authorizationCodeMongo.setSubject(authorizationCode.getSubject());
        authorizationCodeMongo.setRedirectUri(authorizationCode.getRedirectUri());
        authorizationCodeMongo.setScopes(authorizationCode.getScopes());

        return authorizationCodeMongo;
    }
}
