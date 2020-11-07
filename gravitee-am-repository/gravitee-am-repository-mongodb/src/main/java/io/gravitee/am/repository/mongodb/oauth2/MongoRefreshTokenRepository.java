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
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.repository.mongodb.oauth2.internal.model.RefreshTokenMongo;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoRefreshTokenRepository extends AbstractOAuth2MongoRepository implements RefreshTokenRepository {

    private MongoCollection<RefreshTokenMongo> refreshTokenCollection;
    private static final String FIELD_RESET_TIME = "expire_at";
    private static final String FIELD_TOKEN = "token";
    private static final String FIELD_SUBJECT = "subject";

    @PostConstruct
    public void init() {
        refreshTokenCollection = mongoOperations.getCollection("refresh_tokens", RefreshTokenMongo.class);
        super.init(refreshTokenCollection);
        super.createIndex(refreshTokenCollection, new Document(FIELD_TOKEN, 1));
        super.createIndex(refreshTokenCollection, new Document(FIELD_SUBJECT, 1));
        super.createIndex(refreshTokenCollection, new Document(FIELD_DOMAIN, 1).append(FIELD_CLIENT, 1).append(FIELD_SUBJECT, 1));

        // expire after index
        super.createIndex(refreshTokenCollection, new Document(FIELD_RESET_TIME, 1), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
    }

    private Maybe<RefreshToken> findById(String id) {
        return Observable
                .fromPublisher(refreshTokenCollection.find(eq(FIELD_ID, id)).first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Maybe<RefreshToken> findByToken(String token) {
        return Observable
                .fromPublisher(refreshTokenCollection.find(eq(FIELD_TOKEN, token)).first())
                .firstElement()
                .map(this::convert);
    }

    @Override
    public Single<RefreshToken> create(RefreshToken refreshToken) {
        if (refreshToken.getId() == null) {
            refreshToken.setId(RandomString.generate());
        }

        return Single
                .fromPublisher(refreshTokenCollection.insertOne(convert(refreshToken)))
                .flatMap(success -> findById(refreshToken.getId()).toSingle());
    }

    @Override
    public Completable bulkWrite(List<RefreshToken> refreshTokens) {
        return Completable.fromPublisher(refreshTokenCollection.bulkWrite(convert(refreshTokens)));
    }

    @Override
    public Completable delete(String token) {
        return Completable.fromPublisher(refreshTokenCollection.deleteOne(eq(FIELD_TOKEN, token)));
    }

    @Override
    public Completable deleteByUserId(String userId) {
        return Completable.fromPublisher(refreshTokenCollection.deleteMany(eq(FIELD_SUBJECT, userId)));
    }

    @Override
    public Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, String userId) {
        return Completable.fromPublisher(refreshTokenCollection.deleteMany(and(eq(FIELD_DOMAIN, domainId), eq(FIELD_CLIENT, clientId), eq(FIELD_SUBJECT, userId))));
    }

    @Override
    public Completable deleteByDomainIdAndUserId(String domainId, String userId) {
        return Completable.fromPublisher(refreshTokenCollection.deleteMany(and(eq(FIELD_DOMAIN, domainId), eq(FIELD_SUBJECT, userId))));
    }

    private List<WriteModel<RefreshTokenMongo>> convert(List<RefreshToken> refreshTokens) {
        return refreshTokens.stream().map(refreshToken -> new InsertOneModel<>(convert(refreshToken))).collect(Collectors.toList());
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
