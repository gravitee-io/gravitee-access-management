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
import io.gravitee.am.model.UserId;
import io.gravitee.am.repository.mongodb.oauth2.internal.model.RefreshTokenMongo;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableSource;
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
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoRefreshTokenRepository extends AbstractOAuth2MongoRepository implements RefreshTokenRepository {

    private MongoCollection<RefreshTokenMongo> refreshTokenCollection;
    private static final String FIELD_EXPIRE_AT = "expire_at";
    private static final String FIELD_TOKEN = "token";
    private static final String FIELD_SUBJECT = "subject";

    @PostConstruct
    public void init() {
        refreshTokenCollection = mongoOperations.getCollection("refresh_tokens", RefreshTokenMongo.class);
        super.init(refreshTokenCollection);
        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_TOKEN, 1), new IndexOptions().name("t1"));
        indexes.put(new Document(FIELD_SUBJECT, 1), new IndexOptions().name("s1"));
        indexes.put(new Document(FIELD_DOMAIN, 1).append(FIELD_CLIENT, 1).append(FIELD_SUBJECT, 1), new IndexOptions().name("d1c1s1"));
        // expire after index
        indexes.put(new Document(FIELD_EXPIRE_AT, 1), new IndexOptions().name("e1").expireAfter(0L, TimeUnit.SECONDS));
        super.createIndex(refreshTokenCollection, indexes);
    }

    @Override
    public Maybe<RefreshToken> findByToken(String token) {
        return Observable
                .fromPublisher(refreshTokenCollection.find(and(eq(FIELD_TOKEN, token),
                        or(gt(FIELD_EXPIRE_AT, new Date()), eq(FIELD_EXPIRE_AT, null)))).first())
                .firstElement()
                .map(this::convert)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<RefreshToken> create(RefreshToken refreshToken) {
        if (refreshToken.getId() == null) {
            refreshToken.setId(RandomString.generate());
        }

        return Single
                .fromPublisher(refreshTokenCollection.insertOne(convert(refreshToken)))
                .flatMap(success -> Single.just(refreshToken))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable delete(String token) {
        return Completable.fromPublisher(refreshTokenCollection.deleteOne(eq(FIELD_TOKEN, token)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByUserId(String userId) {
        return Completable.fromPublisher(refreshTokenCollection.deleteMany(eq(FIELD_SUBJECT, userId)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, UserId userId) {
        return Completable.fromPublisher(refreshTokenCollection.deleteMany(and(eq(FIELD_DOMAIN, domainId), eq(FIELD_CLIENT, clientId), eq(FIELD_SUBJECT, userId.id()))))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainIdAndUserId(String domainId, UserId userId) {
        return Completable.fromPublisher(refreshTokenCollection.deleteMany(and(eq(FIELD_DOMAIN, domainId), eq(FIELD_SUBJECT, userId.id()))))
                .observeOn(Schedulers.computation());
    }

    @Override
    public CompletableSource deleteByDomainIdAndClientId(String domainId, String clientId) {
        return Completable.fromPublisher(refreshTokenCollection.deleteMany(and(eq(FIELD_DOMAIN, domainId), eq(FIELD_CLIENT, clientId))))
                .observeOn(Schedulers.computation());
    }

    private RefreshTokenMongo convert(RefreshToken refreshToken) {
        if (refreshToken == null) {
            return null;
        }

        RefreshTokenMongo refreshTokenMongo = new RefreshTokenMongo();
        refreshTokenMongo.setId(refreshToken.getId());
        refreshTokenMongo.setToken(refreshToken.getToken());
        refreshTokenMongo.setDomain(refreshToken.getDomain());
        refreshTokenMongo.setClient(refreshToken.getClient());
        refreshTokenMongo.setSubject(refreshToken.getSubject());
        refreshTokenMongo.setCreatedAt(refreshToken.getCreatedAt());
        refreshTokenMongo.setExpireAt(refreshToken.getExpireAt());

        return refreshTokenMongo;
    }

    private RefreshToken convert(RefreshTokenMongo refreshTokenMongo) {
        if (refreshTokenMongo == null) {
            return null;
        }

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(refreshTokenMongo.getId());
        refreshToken.setToken(refreshTokenMongo.getToken());
        refreshToken.setDomain(refreshTokenMongo.getDomain());
        refreshToken.setClient(refreshTokenMongo.getClient());
        refreshToken.setSubject(refreshTokenMongo.getSubject());
        refreshToken.setCreatedAt(refreshTokenMongo.getCreatedAt());
        refreshToken.setExpireAt(refreshTokenMongo.getExpireAt());

        return refreshToken;
    }
}
