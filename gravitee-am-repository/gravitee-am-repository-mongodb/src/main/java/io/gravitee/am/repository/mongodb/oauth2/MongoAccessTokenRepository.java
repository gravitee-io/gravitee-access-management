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
import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.mongodb.oauth2.internal.model.AccessTokenMongo;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.or;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_CLIENT;
import static io.gravitee.am.repository.mongodb.common.MongoUtils.FIELD_DOMAIN;
/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoAccessTokenRepository extends AbstractOAuth2MongoRepository implements AccessTokenRepository {

    private MongoCollection<AccessTokenMongo> accessTokenCollection;

    private static final String FIELD_TOKEN = "token";
    private static final String FIELD_EXPIRE_AT = "expire_at";
    private static final String FIELD_SUBJECT = "subject";
    private static final String FIELD_AUTHORIZATION_CODE = "authorization_code";

    @PostConstruct
    public void init() {
        accessTokenCollection = mongoOperations.getCollection("access_tokens", AccessTokenMongo.class);
        super.init(accessTokenCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_TOKEN, 1), new IndexOptions().name("t1"));
        indexes.put(new Document(FIELD_CLIENT, 1), new IndexOptions().name("c1"));
        indexes.put(new Document(FIELD_AUTHORIZATION_CODE, 1), new IndexOptions().name("ac1"));
        indexes.put(new Document(FIELD_SUBJECT, 1), new IndexOptions().name("s1"));
        indexes.put(new Document(FIELD_DOMAIN, 1).append(FIELD_CLIENT, 1).append(FIELD_SUBJECT, 1), new IndexOptions().name("d1c1s1"));
        // expire after index
        indexes.put(new Document(FIELD_EXPIRE_AT, 1), new IndexOptions().name("e1").expireAfter(0L, TimeUnit.SECONDS));

        super.createIndex(accessTokenCollection, indexes);
    }

    @Override
    public Maybe<AccessToken> findByToken(String token) {
        return Observable
                .fromPublisher(accessTokenCollection.find(and(eq(FIELD_TOKEN, token),
                        or(gt(FIELD_EXPIRE_AT, new Date()), eq(FIELD_EXPIRE_AT, null)))).limit(1).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AccessToken> create(AccessToken accessToken) {
        return Single
                .fromPublisher(accessTokenCollection.insertOne(convert(accessToken)))
                .flatMap(success -> Single.just(accessToken))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String token) {
        return Completable.fromPublisher(accessTokenCollection.findOneAndDelete(eq(FIELD_TOKEN, token)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Observable<AccessToken> findByClientIdAndSubject(String clientId, String subject) {
        return Observable
                .fromPublisher(accessTokenCollection.find(and(eq(FIELD_CLIENT, clientId), eq(FIELD_SUBJECT, subject),
                        or(gt(FIELD_EXPIRE_AT, new Date()), eq(FIELD_EXPIRE_AT, null)))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Observable<AccessToken> findByClientId(String clientId) {
        return Observable
                .fromPublisher(accessTokenCollection.find(and(eq(FIELD_CLIENT, clientId),
                        or(gt(FIELD_EXPIRE_AT, new Date()), eq(FIELD_EXPIRE_AT, null)))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Observable<AccessToken> findByAuthorizationCode(String authorizationCode) {
        return Observable
                .fromPublisher(accessTokenCollection.find(and(eq(FIELD_AUTHORIZATION_CODE, authorizationCode),
                        or(gt(FIELD_EXPIRE_AT, new Date()), eq(FIELD_EXPIRE_AT, null)))))
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByUserId(String userId) {
        return Completable.fromPublisher(accessTokenCollection.deleteMany(eq(FIELD_SUBJECT, userId)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, UserId userId) {
        return Completable.fromPublisher(accessTokenCollection.deleteMany(and(eq(FIELD_DOMAIN, domainId), eq(FIELD_CLIENT, clientId), eq(FIELD_SUBJECT, userId.id()))))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainIdAndClientId(String domainId, String clientId) {
        return Completable.fromPublisher(accessTokenCollection.deleteMany(and(eq(FIELD_DOMAIN, domainId), eq(FIELD_CLIENT, clientId))))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainIdAndUserId(String domainId, UserId userId) {
        return Completable.fromPublisher(accessTokenCollection.deleteMany(and(eq(FIELD_DOMAIN, domainId), eq(FIELD_SUBJECT, userId.id()))))
                .observeOn(Schedulers.computation());
    }

    private AccessTokenMongo convert(AccessToken accessToken) {
        if (accessToken == null) {
            return null;
        }

        AccessTokenMongo accessTokenMongo = new AccessTokenMongo();
        accessTokenMongo.setId(accessToken.getId());
        accessTokenMongo.setToken(accessToken.getToken());
        accessTokenMongo.setDomain(accessToken.getDomain());
        accessTokenMongo.setClient(accessToken.getClient());
        accessTokenMongo.setSubject(accessToken.getSubject());
        accessTokenMongo.setAuthorizationCode(accessToken.getAuthorizationCode());
        accessTokenMongo.setRefreshToken(accessToken.getRefreshToken());
        accessTokenMongo.setCreatedAt(accessToken.getCreatedAt());
        accessTokenMongo.setExpireAt(accessToken.getExpireAt());

        return accessTokenMongo;
    }

    private AccessToken convert(AccessTokenMongo accessTokenMongo) {
        if (accessTokenMongo == null) {
            return null;
        }

        AccessToken accessToken = new AccessToken();
        accessToken.setId(accessTokenMongo.getId());
        accessToken.setToken(accessTokenMongo.getToken());
        accessToken.setDomain(accessTokenMongo.getDomain());
        accessToken.setClient(accessTokenMongo.getClient());
        accessToken.setSubject(accessTokenMongo.getSubject());
        accessToken.setAuthorizationCode(accessTokenMongo.getAuthorizationCode());
        accessToken.setRefreshToken(accessTokenMongo.getRefreshToken());
        accessToken.setCreatedAt(accessTokenMongo.getCreatedAt());
        accessToken.setExpireAt(accessTokenMongo.getExpireAt());

        return accessToken;
    }
}
