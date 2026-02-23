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
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Filters.gt;
import static io.gravitee.am.repository.mongodb.oauth2.internal.model.TokenMongo.*;

@Component
public class MongoTokenRepository extends AbstractOAuth2MongoRepository implements TokenRepository {
    private static final String COLLECTION_NAME = "tokens";
    private MongoCollection<TokenMongo> tokenCollection;

    @PostConstruct
    public void init() {
        tokenCollection = mongoOperations.getCollection(COLLECTION_NAME, TokenMongo.class);
        super.init(tokenCollection);

        final var indexes = new HashMap<Document, IndexOptions>();
        indexes.put(new Document(FIELD_JTI, 1), new IndexOptions().name("t1"));
        indexes.put(new Document(FIELD_CLIENT, 1), new IndexOptions().name("c1"));
        indexes.put(new Document(FIELD_AUTHORIZATION_CODE, 1), new IndexOptions().name("ac1"));
        indexes.put(new Document(FIELD_SUBJECT, 1), new IndexOptions().name("s1"));
        indexes.put(new Document(FIELD_PARENT_JTIS, 1), new IndexOptions().name("pj1"));
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

    private Completable deleteRecursivelyWithGraphLookup(Document matcher) {
        var pipeline = List.<Bson>of(
                matcher,
                new Document("$graphLookup", new Document("from", COLLECTION_NAME)
                        .append("startWith", "$" + FIELD_JTI)
                        .append("connectFromField", FIELD_JTI)
                        .append("connectToField", FIELD_PARENT_JTIS)
                        .append("as", "descendants")));

        return Observable.fromPublisher(tokenCollection.aggregate(pipeline, Document.class))
                .collect(() -> new HashSet<>(),
                        (Set<String> jtis, Document result) -> {
                    String rootJti = result.getString(FIELD_JTI);
                    if (rootJti != null) {
                        jtis.add(rootJti);
                    }

                    var descendants = result.getList("descendants", Document.class, List.of());
                    for (Document descendant : descendants) {
                        String descendantJti = descendant.getString(FIELD_JTI);
                        if (descendantJti != null) {
                            jtis.add(descendantJti);
                        }
                    }
                })
                .flatMapCompletable(jtis -> {
                    if (jtis.isEmpty()) {
                        return Completable.complete();
                    }

                    return Completable.fromPublisher(tokenCollection.deleteMany(in(FIELD_JTI, jtis)));
                })
                .observeOn(Schedulers.computation());
    }

    @Override
    public Completable deleteByJti(String jti) {
        return deleteRecursivelyWithGraphLookup(new Document("$match", eq(FIELD_JTI, jti)));
    }

    @Override
    public Completable deleteByUserId(String userId) {
        return deleteRecursivelyWithGraphLookup(new Document("$match", eq(FIELD_SUBJECT, userId)));
    }

    @Override
    public Completable deleteByDomainIdClientIdAndUserId(String domainId, String clientId, UserId userId) {
        return deleteRecursivelyWithGraphLookup(new Document("$match", and(eq(FIELD_DOMAIN, domainId), eq(FIELD_CLIENT, clientId), eq(FIELD_SUBJECT, userId.id()))));
    }

    @Override
    public Completable deleteByDomainIdAndClientId(String domainId, String clientId) {
        return deleteRecursivelyWithGraphLookup(new Document("$match", and(eq(FIELD_DOMAIN, domainId), eq(FIELD_CLIENT, clientId))));
    }

    @Override
    public Completable deleteByDomainIdAndUserId(String domainId, UserId userId) {
        return deleteRecursivelyWithGraphLookup(new Document("$match", and(eq(FIELD_DOMAIN, domainId), eq(FIELD_SUBJECT, userId.id()))));
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

    private TokenMongo convert(Token token, TokenType tokenType) {
        if (token == null) {
            return null;
        }

        TokenMongo tokenMongo = new TokenMongo();
        tokenMongo.setType(tokenType);
        tokenMongo.setId(token.getId());
        tokenMongo.setJti(token.getToken());
        tokenMongo.setDomainId(token.getDomain());
        tokenMongo.setClientId(token.getClient());
        tokenMongo.setSubject(token.getSubject());
        tokenMongo.setCreatedAt(token.getCreatedAt());
        tokenMongo.setExpireAt(token.getExpireAt());
        tokenMongo.setParentSubjectJti(token.getParentSubjectJti());
        tokenMongo.setParentActorJti(token.getParentActorJti());
        tokenMongo.setParentJtis(Stream.of(token.getParentSubjectJti(), token.getParentActorJti())
                .filter(parentJti -> parentJti != null && !parentJti.isBlank())
                .distinct()
                .toList());
        return tokenMongo;
    }

    private AccessToken convertToAccessToken(TokenMongo tokenMongo) {
        if (tokenMongo == null) {
            return null;
        }

        AccessToken accessToken = new AccessToken();
        accessToken.setId(tokenMongo.getId());
        accessToken.setToken(tokenMongo.getJti());
        accessToken.setDomain(tokenMongo.getDomainId());
        accessToken.setClient(tokenMongo.getClientId());
        accessToken.setSubject(tokenMongo.getSubject());
        accessToken.setAuthorizationCode(tokenMongo.getAuthorizationCode());
        accessToken.setRefreshToken(tokenMongo.getRefreshTokenJti());
        accessToken.setCreatedAt(tokenMongo.getCreatedAt());
        accessToken.setExpireAt(tokenMongo.getExpireAt());
        accessToken.setParentSubjectJti(tokenMongo.getParentSubjectJti());
        accessToken.setParentActorJti(tokenMongo.getParentActorJti());

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
        refreshToken.setParentSubjectJti(tokenMongo.getParentSubjectJti());
        refreshToken.setParentActorJti(tokenMongo.getParentActorJti());

        return refreshToken;
    }
}
