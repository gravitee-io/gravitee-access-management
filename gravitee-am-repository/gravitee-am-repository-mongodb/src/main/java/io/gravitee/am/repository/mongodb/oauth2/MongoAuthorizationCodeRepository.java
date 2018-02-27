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

import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.repository.mongodb.common.SerializationUtils;
import io.gravitee.am.repository.mongodb.oauth2.internal.model.OAuth2AuthorizationCodeMongo;
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.code.OAuth2AuthorizationCode;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subscribers.DefaultSubscriber;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(MongoAuthorizationCodeRepository.class);
    private static final String FIELD_ID = "_id";
    private static final String FIELD_RESET_TIME = "expiration";
    private MongoCollection<OAuth2AuthorizationCodeMongo> oAuth2AuthorizationCodesCollection;

    @PostConstruct
    public void init() {
        oAuth2AuthorizationCodesCollection = mongoOperations.getCollection("oauth2_authorization_codes", OAuth2AuthorizationCodeMongo.class);
        oAuth2AuthorizationCodesCollection.createIndex(new Document(FIELD_RESET_TIME, 1), new IndexOptions().expireAfter(0l, TimeUnit.SECONDS)).subscribe(new IndexSubscriber());
    }

    @Override
    public Single<OAuth2AuthorizationCode> store(OAuth2AuthorizationCode oAuth2AuthorizationCode) {
        return Single.fromPublisher(oAuth2AuthorizationCodesCollection
                .findOneAndReplace(eq(FIELD_ID, oAuth2AuthorizationCode.getCode()), convert(oAuth2AuthorizationCode), new FindOneAndReplaceOptions().upsert(true).returnDocument(ReturnDocument.AFTER)))
                .flatMap(success -> _findById(oAuth2AuthorizationCode.getCode()));
    }

    @Override
    public Maybe<OAuth2Authentication> remove(String code) {
        return Observable.fromPublisher(oAuth2AuthorizationCodesCollection.findOneAndDelete(eq(FIELD_ID, code)))
                .map(oAuth2AuthorizationCodeMongo -> deserializeAuthentication(oAuth2AuthorizationCodeMongo.getOAuth2Authentication())).firstElement();
    }

    private Single<OAuth2AuthorizationCode> _findById(String id) {
        return Single.fromPublisher(oAuth2AuthorizationCodesCollection.find(eq(FIELD_ID, id)).first()).map(this::convert);
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

    private OAuth2AuthorizationCodeMongo convert(OAuth2AuthorizationCode oAuth2AuthorizationCode) {
        OAuth2AuthorizationCodeMongo oAuth2AuthorizationCodeMongo = new OAuth2AuthorizationCodeMongo();
        oAuth2AuthorizationCodeMongo.setCode(oAuth2AuthorizationCode.getCode());
        oAuth2AuthorizationCodeMongo.setOAuth2Authentication(serializeAuthentication(oAuth2AuthorizationCode.getOAuth2Authentication()));
        oAuth2AuthorizationCodeMongo.setExpiration(oAuth2AuthorizationCode.getExpiration());
        oAuth2AuthorizationCodeMongo.setCreatedAt(oAuth2AuthorizationCode.getCreatedAt());
        oAuth2AuthorizationCodeMongo.setUpdatedAt(oAuth2AuthorizationCode.getUpdatedAt());

        return oAuth2AuthorizationCodeMongo;
    }

    private OAuth2AuthorizationCode convert(OAuth2AuthorizationCodeMongo oAuth2AuthorizationCodeMongo) {
        OAuth2AuthorizationCode oAuth2AuthorizationCode = new OAuth2AuthorizationCode();
        oAuth2AuthorizationCode.setCode(oAuth2AuthorizationCodeMongo.getCode());
        oAuth2AuthorizationCode.setOAuth2Authentication(deserializeAuthentication(oAuth2AuthorizationCodeMongo.getOAuth2Authentication()));
        oAuth2AuthorizationCode.setExpiration(oAuth2AuthorizationCodeMongo.getExpiration());
        oAuth2AuthorizationCode.setCreatedAt(oAuth2AuthorizationCodeMongo.getCreatedAt());
        oAuth2AuthorizationCode.setUpdatedAt(oAuth2AuthorizationCodeMongo.getUpdatedAt());

        return oAuth2AuthorizationCode;
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
