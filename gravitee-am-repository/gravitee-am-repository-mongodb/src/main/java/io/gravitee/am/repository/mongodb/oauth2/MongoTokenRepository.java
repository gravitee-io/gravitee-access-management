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
import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.repository.mongodb.common.SerializationUtils;
import io.gravitee.am.repository.mongodb.oauth2.internal.model.OAuth2AccessTokenMongo;
import io.gravitee.am.repository.mongodb.oauth2.internal.model.OAuth2RefreshTokenMongo;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.OAuth2AccessToken;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subscribers.DefaultSubscriber;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoTokenRepository extends AbstractOAuth2MongoRepository implements TokenRepository {

    private static final Logger logger = LoggerFactory.getLogger(MongoTokenRepository.class);
    private static final String FIELD_ID = "_id";
    private static final String FIELD_REFRESH_TOKEN = "refreshToken";
    private static final String FIELD_RESET_TIME = "expiration";
    private static final String FIELD_AUTHENTICATION_KEY = "authenticationKey";
    private static final String FIELD_CLIENT_ID = "clientId";
    private static final String FIELD_USER_NAME = "userName";

    private MongoCollection<OAuth2AccessTokenMongo> oAuth2AccessTokensCollection;
    private MongoCollection<OAuth2RefreshTokenMongo> oAuth2RefreshTokensCollection;

    @PostConstruct
    public void init() {
        oAuth2AccessTokensCollection = mongoOperations.getCollection("oauth2_access_tokens", OAuth2AccessTokenMongo.class);
        oAuth2AccessTokensCollection.createIndex(new Document(FIELD_RESET_TIME, 1), new IndexOptions().expireAfter(0l, TimeUnit.SECONDS)).subscribe(new IndexSubscriber());
        oAuth2AccessTokensCollection.createIndex(new Document(FIELD_CLIENT_ID, 1)).subscribe(new IndexSubscriber());
        oAuth2AccessTokensCollection.createIndex(new Document(FIELD_AUTHENTICATION_KEY, 1)).subscribe(new IndexSubscriber());
        oAuth2AccessTokensCollection.createIndex(new Document(FIELD_CLIENT_ID, 1).append(FIELD_USER_NAME, 1)).subscribe(new IndexSubscriber());

        oAuth2RefreshTokensCollection = mongoOperations.getCollection("oauth2_refresh_tokens", OAuth2RefreshTokenMongo.class);
        oAuth2RefreshTokensCollection.createIndex(new Document(FIELD_RESET_TIME, 1), new IndexOptions().expireAfter(0l, TimeUnit.SECONDS)).subscribe(new IndexSubscriber());
    }

    @Override
    public Maybe<OAuth2Authentication> readAuthentication(OAuth2AccessToken oAuth2AccessToken) {
        return readAuthentication(oAuth2AccessToken.getValue());
    }

    @Override
    public Maybe<OAuth2Authentication> readAuthentication(String tokenValue) {
        return Observable.fromPublisher(oAuth2AccessTokensCollection.find(eq(FIELD_ID, tokenValue)).first()).map(oAuth2AccessTokenMongo -> deserializeAuthentication(oAuth2AccessTokenMongo.getAuthentication())).firstElement();
    }

    @Override
    public Single<OAuth2AccessToken> storeAccessToken(OAuth2AccessToken oAuth2AccessToken, OAuth2Authentication oAuth2Authentication, String authenticationKey) {
        String refreshToken = null;
        if (oAuth2AccessToken.getRefreshToken() != null) {
            refreshToken = oAuth2AccessToken.getRefreshToken().getValue();
        }
        OAuth2AccessTokenMongo oAuth2AccessTokenMongo = convert(oAuth2AccessToken, refreshToken, oAuth2Authentication, authenticationKey);
        return readAccessToken(oAuth2AccessToken.getValue())
                .switchIfEmpty(Maybe.just(new OAuth2AccessToken(null)))
                .flatMapSingle(accessToken -> {
                    if (accessToken.getValue() == null) {
                        return _storeAccessToken(oAuth2AccessTokenMongo);
                    } else {
                        return _removeAccessToken(oAuth2AccessToken.getValue()).flatMap(success -> _storeAccessToken(oAuth2AccessTokenMongo));
                    }
                });
    }

    @Override
    public Maybe<OAuth2AccessToken> readAccessToken(String tokenValue) {
        return _findAccessTokenById(tokenValue)
                .switchIfEmpty(Observable.just(new OAuth2AccessTokenMongo()))
                .flatMap(accessTokenMongo -> {
                    if (accessTokenMongo.getValue() == null) {
                        return Observable.empty();
                    }
                    if (accessTokenMongo.getRefreshToken() == null) {
                        return Observable.just(convert(accessTokenMongo));
                    }
                    return _findRefreshTokenById(accessTokenMongo.getRefreshToken()).switchIfEmpty(Observable.just(new OAuth2RefreshTokenMongo())).map(refreshTokenMongo -> this.convert(accessTokenMongo, refreshTokenMongo));
                }).firstElement();
    }

    @Override
    public Single<Irrelevant> removeAccessToken(OAuth2AccessToken oAuth2AccessToken) {
        return removeAccessToken(oAuth2AccessToken.getValue());
    }

    @Override
    public Single<Irrelevant> removeAccessToken(String tokenValue) {
        return _removeAccessToken(tokenValue).map(deleteResult -> Irrelevant.OAUTH2_ACCESS_TOKEN);
    }

    private Single<DeleteResult> _removeAccessToken(String tokenValue) {
        return Single.fromPublisher(oAuth2AccessTokensCollection.deleteOne(eq(FIELD_ID, tokenValue)));
    }

    @Override
    public Single<OAuth2RefreshToken> storeRefreshToken(OAuth2RefreshToken oAuth2RefreshToken, OAuth2Authentication oAuth2Authentication) {
        OAuth2RefreshTokenMongo oAuth2RefreshTokenMongo = convert(oAuth2RefreshToken, oAuth2Authentication);
        return Observable.fromPublisher(oAuth2RefreshTokensCollection.insertOne(oAuth2RefreshTokenMongo))
                .flatMap(success -> _findRefreshTokenById(oAuth2RefreshTokenMongo.getValue())).map(this::convert).firstOrError();
    }

    @Override
    public Maybe<OAuth2RefreshToken> readRefreshToken(String tokenValue) {
        return _findRefreshTokenById(tokenValue).map(this::convert).firstElement();
    }

    @Override
    public Maybe<OAuth2Authentication> readAuthenticationForRefreshToken(OAuth2RefreshToken oAuth2RefreshToken) {
        return readAuthenticationForRefreshToken(oAuth2RefreshToken.getValue());
    }

    private Maybe<OAuth2Authentication> readAuthenticationForRefreshToken(String tokenValue) {
        return _findRefreshTokenById(tokenValue).map(oAuth2RefreshTokenMongo -> deserializeAuthentication(oAuth2RefreshTokenMongo.getAuthentication())).firstElement();
    }

    @Override
    public Single<Irrelevant> removeRefreshToken(OAuth2RefreshToken oAuth2RefreshToken) {
        return removeRefreshToken(oAuth2RefreshToken.getValue());
    }

    private Single<Irrelevant> removeRefreshToken(String tokenValue) {
        return Single.fromPublisher(oAuth2RefreshTokensCollection.deleteOne(eq(FIELD_ID, tokenValue))).map(deleteResult -> Irrelevant.OAUTH2_REFRESH_TOKEN);
    }

    @Override
    public Single<Irrelevant> removeAccessTokenUsingRefreshToken(OAuth2RefreshToken oAuth2RefreshToken) {
        return removeAccessTokenUsingRefreshToken(oAuth2RefreshToken.getValue());
    }

    private Single<Irrelevant> removeAccessTokenUsingRefreshToken(String refreshTokenValue) {
        return Single.fromPublisher(oAuth2AccessTokensCollection.deleteOne(eq(FIELD_REFRESH_TOKEN, refreshTokenValue))).map(deleteResult -> Irrelevant.OAUTH2_ACCESS_TOKEN);
    }

    @Override
    public Maybe<OAuth2AccessToken> getAccessToken(String authenticationKey) {
        return Observable.fromPublisher(oAuth2AccessTokensCollection.find(eq(FIELD_AUTHENTICATION_KEY, authenticationKey)).first())
                .flatMap(accessTokenMongo -> {
                    if (accessTokenMongo.getValue() == null) {
                        return Observable.empty();
                    }
                    if (accessTokenMongo.getRefreshToken() == null) {
                        return Observable.just(convert(accessTokenMongo));
                    }
                    return _findRefreshTokenById(accessTokenMongo.getRefreshToken()).switchIfEmpty(Observable.just(new OAuth2RefreshTokenMongo())).map(refreshTokenMongo -> this.convert(accessTokenMongo, refreshTokenMongo));
                }).firstElement();
    }

    @Override
    public Single<List<OAuth2AccessToken>> findTokensByClientIdAndUserName(String clientId, String userName) {
        return Observable.fromPublisher(oAuth2AccessTokensCollection.find(and(eq(FIELD_CLIENT_ID, clientId), eq(FIELD_USER_NAME, userName))))
                .flatMap(accessTokenMongo -> {
                    if (accessTokenMongo.getValue() == null) {
                        return Observable.empty();
                    }
                    if (accessTokenMongo.getRefreshToken() == null) {
                        return Observable.just(convert(accessTokenMongo));
                    }
                    return _findRefreshTokenById(accessTokenMongo.getRefreshToken()).switchIfEmpty(Observable.just(new OAuth2RefreshTokenMongo())).map(refreshTokenMongo -> this.convert(accessTokenMongo, refreshTokenMongo));
                }).toList();
    }

    @Override
    public Single<List<OAuth2AccessToken>> findTokensByClientId(String clientId) {
        return Observable.fromPublisher(oAuth2AccessTokensCollection.find(eq(FIELD_CLIENT_ID, clientId)))
                .flatMap(accessTokenMongo -> {
                    if (accessTokenMongo.getValue() == null) {
                        return Observable.empty();
                    }
                    if (accessTokenMongo.getRefreshToken() == null) {
                        return Observable.just(convert(accessTokenMongo));
                    }
                    return _findRefreshTokenById(accessTokenMongo.getRefreshToken()).switchIfEmpty(Observable.just(new OAuth2RefreshTokenMongo())).map(refreshTokenMongo -> this.convert(accessTokenMongo, refreshTokenMongo));
                }).toList();
    }

    private Observable<OAuth2AccessTokenMongo> _findAccessTokenById(String id) {
        return Observable.fromPublisher(oAuth2AccessTokensCollection.find(eq(FIELD_ID, id)).first());
    }

    private Observable<OAuth2RefreshTokenMongo> _findRefreshTokenById(String id) {
        return Observable.fromPublisher(oAuth2RefreshTokensCollection.find(eq(FIELD_ID, id)).first());
    }

    private Single<OAuth2AccessToken> _storeAccessToken(OAuth2AccessTokenMongo oAuth2AccessTokenMongo) {
        return Observable.fromPublisher(oAuth2AccessTokensCollection.insertOne(oAuth2AccessTokenMongo))
                .flatMap(success -> _findAccessTokenById(oAuth2AccessTokenMongo.getValue()))
                .flatMap(accessTokenMongo -> {
                    if (accessTokenMongo.getRefreshToken() == null) {
                        return Observable.just(convert(accessTokenMongo));
                    }
                    return _findRefreshTokenById(accessTokenMongo.getRefreshToken()).switchIfEmpty(Observable.just(new OAuth2RefreshTokenMongo())).map(refreshTokenMongo -> this.convert(accessTokenMongo, refreshTokenMongo));
                }).singleOrError();

    }

    private OAuth2RefreshToken convert(OAuth2RefreshTokenMongo oAuth2RefreshTokenMongo) {
        OAuth2RefreshToken oAuth2RefreshToken = new OAuth2RefreshToken(oAuth2RefreshTokenMongo.getValue());
        oAuth2RefreshToken.setExpiration(oAuth2RefreshTokenMongo.getExpiration());
        oAuth2RefreshToken.setCreatedAt(oAuth2RefreshTokenMongo.getCreatedAt());
        oAuth2RefreshToken.setUpdatedAt(oAuth2RefreshTokenMongo.getUpdatedAt());

        return oAuth2RefreshToken;
    }

    private OAuth2RefreshTokenMongo convert(OAuth2RefreshToken oAuth2RefreshToken, OAuth2Authentication oAuth2Authentication) {
        OAuth2RefreshTokenMongo oAuth2RefreshTokenMongo = new OAuth2RefreshTokenMongo();
        oAuth2RefreshTokenMongo.setValue(oAuth2RefreshToken.getValue());
        oAuth2RefreshTokenMongo.setExpiration(oAuth2RefreshToken.getExpiration());
        oAuth2RefreshTokenMongo.setAuthentication(serializeAuthentication(oAuth2Authentication));
        oAuth2RefreshTokenMongo.setCreatedAt(oAuth2RefreshToken.getCreatedAt());
        oAuth2RefreshTokenMongo.setUpdatedAt(oAuth2RefreshToken.getUpdatedAt());

        return oAuth2RefreshTokenMongo;
    }

    private OAuth2AccessToken convert(OAuth2AccessTokenMongo oAuth2AccessTokenMongo) {
        OAuth2AccessToken oAuth2AccessToken = new OAuth2AccessToken(oAuth2AccessTokenMongo.getValue());
        oAuth2AccessToken.setAdditionalInformation(oAuth2AccessTokenMongo.getAdditionalInformation());
        oAuth2AccessToken.setExpiration(oAuth2AccessTokenMongo.getExpiration());
        oAuth2AccessToken.setScope(oAuth2AccessTokenMongo.getScope());
        oAuth2AccessToken.setTokenType(oAuth2AccessTokenMongo.getTokenType());
        oAuth2AccessToken.setCreatedAt(oAuth2AccessTokenMongo.getCreatedAt());
        oAuth2AccessToken.setUpdatedAt(oAuth2AccessTokenMongo.getUpdatedAt());

        return oAuth2AccessToken;
    }

    private OAuth2AccessToken convert(OAuth2AccessTokenMongo oAuth2AccessTokenMongo, OAuth2RefreshTokenMongo oAuth2RefreshTokenMongo) {
        OAuth2AccessToken oAuth2AccessToken = new OAuth2AccessToken(oAuth2AccessTokenMongo.getValue());
        oAuth2AccessToken.setAdditionalInformation(oAuth2AccessTokenMongo.getAdditionalInformation());

        // set refresh token
        if (oAuth2RefreshTokenMongo != null && oAuth2RefreshTokenMongo.getValue() != null) {
            OAuth2RefreshToken oAuth2RefreshToken = new OAuth2RefreshToken(oAuth2AccessTokenMongo.getRefreshToken());
            oAuth2RefreshToken.setExpiration(oAuth2RefreshTokenMongo.getExpiration());
            oAuth2AccessToken.setRefreshToken(oAuth2RefreshToken);
        }
        oAuth2AccessToken.setExpiration(oAuth2AccessTokenMongo.getExpiration());
        oAuth2AccessToken.setScope(oAuth2AccessTokenMongo.getScope());
        oAuth2AccessToken.setTokenType(oAuth2AccessTokenMongo.getTokenType());
        oAuth2AccessToken.setCreatedAt(oAuth2AccessTokenMongo.getCreatedAt());
        oAuth2AccessToken.setUpdatedAt(oAuth2AccessTokenMongo.getUpdatedAt());

        return oAuth2AccessToken;
    }

    private OAuth2AccessTokenMongo convert(OAuth2AccessToken oAuth2AccessToken, String oAuth2RefreshToken, OAuth2Authentication oAuth2Authentication, String authenticationKey) {
        OAuth2AccessTokenMongo oAuth2AccessTokenMongo = new OAuth2AccessTokenMongo();
        oAuth2AccessTokenMongo.setValue(oAuth2AccessToken.getValue());
        oAuth2AccessTokenMongo.setAdditionalInformation(oAuth2AccessToken.getAdditionalInformation() != null ? new Document(oAuth2AccessToken.getAdditionalInformation()) : new Document());
        oAuth2AccessTokenMongo.setRefreshToken(oAuth2RefreshToken);
        oAuth2AccessTokenMongo.setExpiration(oAuth2AccessToken.getExpiration());
        oAuth2AccessTokenMongo.setScope(oAuth2AccessToken.getScope());
        oAuth2AccessTokenMongo.setTokenType(oAuth2AccessToken.getTokenType());
        oAuth2AccessTokenMongo.setUserName(oAuth2Authentication.isClientOnly() ? null : oAuth2Authentication.getName());
        oAuth2AccessTokenMongo.setClientId(oAuth2Authentication.getOAuth2Request().getClientId());
        oAuth2AccessTokenMongo.setAuthenticationKey(authenticationKey);
        oAuth2AccessTokenMongo.setAuthentication(serializeAuthentication(oAuth2Authentication));
        oAuth2AccessTokenMongo.setCreatedAt(oAuth2AccessToken.getCreatedAt());
        oAuth2AccessTokenMongo.setUpdatedAt(oAuth2AccessToken.getUpdatedAt());

        return oAuth2AccessTokenMongo;
    }

    private OAuth2Authentication deserializeAuthentication(byte[] authentication) {
        try {
            return SerializationUtils.deserialize(authentication);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] serializeAuthentication(OAuth2Authentication authentication) {
        try {
            return SerializationUtils.serialize(authentication);
        } catch (Exception e) {
            return null;
        }
    }

    private class IndexSubscriber extends DefaultSubscriber<String> {
        @Override
        public void onNext(String value) {
            logger.debug("Created an index named : " + value);
        }

        @Override
        public void onError(Throwable throwable) {
            logger.error("Error occurs during indexing", throwable);
        }

        @Override
        public void onComplete() {
            logger.debug("Index creation complete");
        }
    }
}
