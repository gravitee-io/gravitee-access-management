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
import io.gravitee.am.repository.mongodb.oauth2.internal.model.TokenMongo;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.repository.oauth2.model.RefreshToken;
import io.gravitee.am.repository.oauth2.model.Token;
import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Filters.gt;
import static io.gravitee.am.repository.mongodb.oauth2.internal.model.TokenMongo.*;

@Component
public class MongoTokenRepository extends AbstractOAuth2MongoRepository implements TokenRepository {
    private MongoCollection<TokenMongo> tokenCollection;

    @PostConstruct
    public void init() {
        tokenCollection = mongoOperations.getCollection("tokens", TokenMongo.class);
        super.init(tokenCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_JTI, 1), new IndexOptions().name("t1"));
        indexes.put(new Document(FIELD_CLIENT, 1), new IndexOptions().name("c1"));
        indexes.put(new Document(FIELD_AUTHORIZATION_CODE, 1), new IndexOptions().name("ac1"));
        indexes.put(new Document(FIELD_SUBJECT, 1), new IndexOptions().name("s1"));
        indexes.put(new Document(FIELD_DOMAIN, 1).append(FIELD_CLIENT, 1).append(FIELD_SUBJECT, 1), new IndexOptions().name("d1c1s1"));
        // expire after index
        indexes.put(new Document(FIELD_EXPIRE_AT, 1), new IndexOptions().name("e1").expireAfter(0L, TimeUnit.SECONDS));

        super.createIndex(tokenCollection, indexes);
    }

    @Override
    public Maybe<RefreshToken> findRefreshTokenByJti(String token) {
        return Observable
                .fromPublisher(tokenCollection.find(and(
                        eq(FIELD_JTI, token),
                        eq(FIELD_TYPE, TokenType.REFRESH_TOKEN),
                        or(gt(FIELD_EXPIRE_AT, new Date()), eq(FIELD_EXPIRE_AT, null)))).limit(1).first())
                .firstElement()
                .map(this::convertToRefreshToken)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<RefreshToken> create(RefreshToken refreshToken) {
        if (refreshToken.getId() == null) {
            refreshToken.setId(RandomString.generate());
        }

        return Single
                .fromPublisher(tokenCollection.insertOne(convert(refreshToken)))
                .flatMap(success -> Single.just(refreshToken))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Single<AccessToken> create(AccessToken accessToken) {
        return Single
                .fromPublisher(tokenCollection.insertOne(convert(accessToken)))
                .flatMap(success -> Single.just(accessToken))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Maybe<AccessToken> findAccessTokenByJti(String token) {
        return Observable
                .fromPublisher(tokenCollection.find(and(
                        eq(FIELD_JTI, token),
                        eq(FIELD_TYPE, TokenType.ACCESS_TOKEN),
                        or(gt(FIELD_EXPIRE_AT, new Date()), eq(FIELD_EXPIRE_AT, null)))).limit(1).first())
                .firstElement()
                .map(this::convertToAccessToken)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Observable<AccessToken> findAccessTokenByAuthorizationCode(String authorizationCode) {
        return Observable
                .fromPublisher(tokenCollection.find(and(
                        eq(FIELD_AUTHORIZATION_CODE, authorizationCode),
                        eq(FIELD_TYPE, TokenType.ACCESS_TOKEN),
                        or(gt(FIELD_EXPIRE_AT, new Date()), eq(FIELD_EXPIRE_AT, null)))))
                .map(this::convertToAccessToken)
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByJti(String jti) {
        return Completable.fromPublisher(tokenCollection.findOneAndDelete(eq(FIELD_JTI, jti)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByUserId(String userId) {
        return Completable.fromPublisher(tokenCollection.deleteMany(eq(FIELD_SUBJECT, userId)))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, UserId userId) {
        return Completable.fromPublisher(tokenCollection.deleteMany(and(eq(FIELD_DOMAIN, domainId), eq(FIELD_CLIENT, clientId), eq(FIELD_SUBJECT, userId.id()))))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainIdAndClientId(String domainId, String clientId) {
        return Completable.fromPublisher(tokenCollection.deleteMany(and(eq(FIELD_DOMAIN, domainId), eq(FIELD_CLIENT, clientId))))
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByDomainIdAndUserId(String domainId, UserId userId) {
        return Completable.fromPublisher(tokenCollection.deleteMany(and(eq(FIELD_DOMAIN, domainId), eq(FIELD_SUBJECT, userId.id()))))
                .observeOn(Schedulers.computation());
    }

    private TokenMongo convert(AccessToken token) {
        if (token == null) {
            return null;
        }

        TokenMongo tokenMongo = convert(token, TokenType.ACCESS_TOKEN);
        tokenMongo.setAuthorizationCode(token.getAuthorizationCode());
        tokenMongo.setRefreshTokenJti(token.getRefreshToken());

        return tokenMongo;
    }

    private TokenMongo convert(RefreshToken token) {
        return convert(token, TokenType.REFRESH_TOKEN);
    }

    private TokenMongo convert(Token refreshToken, TokenType tokenType) {
        if (refreshToken == null) {
            return null;
        }

        TokenMongo tokenMongo = new TokenMongo();
        tokenMongo.setType(tokenType);
        tokenMongo.setId(refreshToken.getId());
        tokenMongo.setJti(refreshToken.getToken());
        tokenMongo.setDomainId(refreshToken.getDomain());
        tokenMongo.setClientId(refreshToken.getClient());
        tokenMongo.setSubject(refreshToken.getSubject());
        tokenMongo.setCreatedAt(refreshToken.getCreatedAt());
        tokenMongo.setExpireAt(refreshToken.getExpireAt());

        return tokenMongo;
    }

    private AccessToken convertToAccessToken(TokenMongo accessTokenMongo) {
        if (accessTokenMongo == null) {
            return null;
        }

        AccessToken accessToken = new AccessToken();
        accessToken.setId(accessTokenMongo.getId());
        accessToken.setToken(accessTokenMongo.getJti());
        accessToken.setDomain(accessTokenMongo.getDomainId());
        accessToken.setClient(accessTokenMongo.getClientId());
        accessToken.setSubject(accessTokenMongo.getSubject());
        accessToken.setAuthorizationCode(accessTokenMongo.getAuthorizationCode());
        accessToken.setRefreshToken(accessTokenMongo.getRefreshTokenJti());
        accessToken.setCreatedAt(accessTokenMongo.getCreatedAt());
        accessToken.setExpireAt(accessTokenMongo.getExpireAt());

        return accessToken;
    }

    private RefreshToken convertToRefreshToken(TokenMongo tokenMongo) {
        if (tokenMongo == null) {
            return null;
        }

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(tokenMongo.getId());
        refreshToken.setToken(tokenMongo.getJti());
        refreshToken.setDomain(tokenMongo.getDomainId());
        refreshToken.setClient(tokenMongo.getClientId());
        refreshToken.setSubject(tokenMongo.getSubject());
        refreshToken.setCreatedAt(tokenMongo.getCreatedAt());
        refreshToken.setExpireAt(tokenMongo.getExpireAt());

        return refreshToken;
    }
}
