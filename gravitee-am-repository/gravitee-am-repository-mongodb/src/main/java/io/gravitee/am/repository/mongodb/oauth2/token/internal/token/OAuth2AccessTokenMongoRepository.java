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
package io.gravitee.am.repository.mongodb.oauth2.token.internal.token;

import com.mongodb.*;
import io.gravitee.am.repository.mongodb.oauth2.token.internal.model.OAuth2AccessTokenMongo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class OAuth2AccessTokenMongoRepository {

    private static final String ACCESS_TOKEN_COLLECTION = "oauth2_access_tokens";
    private static final String FIELD_KEY = "_id";
    private static final String FIELD_TOKEN_TYPE = "tokenType";
    private static final String FIELD_REFRESH_TOKEN = "refreshToken";
    private static final String FIELD_SCOPE = "scope";
    private static final String FIELD_ADDITIONAL_INFORMATION = "additionalInformation";
    private static final String FIELD_RESET_TIME = "expiration";
    private static final String FIELD_AUTHENTICATION = "authentication";
    private static final String FIELD_AUTHENTICATION_KEY = "authenticationKey";
    private static final String FIELD_CLIENT_ID = "clientId";
    private static final String FIELD_USER_NAME = "userName";
    private static final String FIELD_UPDATED_AT = "updated_at";
    private static final String FIELD_CREATED_AT = "created_at";

    @Autowired
    @Qualifier("oauth2MongoTemplate")
    private MongoOperations mongoOperations;

    @PostConstruct
    public void ensureTTLIndex() {
        mongoOperations.indexOps(ACCESS_TOKEN_COLLECTION).ensureIndex(new IndexDefinition() {
            @Override
            public DBObject getIndexKeys() {
                return new BasicDBObject(FIELD_RESET_TIME, 1);
            }

            @Override
            public DBObject getIndexOptions() {
                // To expire Documents at a Specific Clock Time we have to specify an expireAfterSeconds value of 0.
                return new BasicDBObject("expireAfterSeconds", 0);
            }
        });

        mongoOperations.indexOps(ACCESS_TOKEN_COLLECTION)
                .ensureIndex(new Index()
                        .on(FIELD_CLIENT_ID, Sort.Direction.ASC));

        mongoOperations.indexOps(ACCESS_TOKEN_COLLECTION)
                .ensureIndex(new Index()
                        .on(FIELD_AUTHENTICATION_KEY, Sort.Direction.ASC));

        mongoOperations.indexOps(ACCESS_TOKEN_COLLECTION)
                .ensureIndex(new Index()
                        .on(FIELD_CLIENT_ID, Sort.Direction.ASC)
                        .on(FIELD_USER_NAME, Sort.Direction.ASC));
    }

    public OAuth2AccessTokenMongo findOne(String value) {
        DBObject result = mongoOperations
                .getCollection(ACCESS_TOKEN_COLLECTION)
                .findOne(value);

        OAuth2AccessTokenMongo oAuth2AccessTokenMongo = null;

        if (result != null) {
            oAuth2AccessTokenMongo = convert(result);
        }

        return oAuth2AccessTokenMongo;
    }

    public void save(OAuth2AccessTokenMongo oAuth2AccessTokenMongo) {
        final DBObject doc = BasicDBObjectBuilder.start()
                .add(FIELD_KEY, oAuth2AccessTokenMongo.getValue())
                .add(FIELD_AUTHENTICATION, oAuth2AccessTokenMongo.getAuthentication())
                .add(FIELD_AUTHENTICATION_KEY, oAuth2AccessTokenMongo.getAuthenticationKey())
                .add(FIELD_TOKEN_TYPE, oAuth2AccessTokenMongo.getTokenType())
                .add(FIELD_REFRESH_TOKEN, oAuth2AccessTokenMongo.getRefreshToken())
                .add(FIELD_SCOPE, oAuth2AccessTokenMongo.getScope())
                .add(FIELD_ADDITIONAL_INFORMATION, oAuth2AccessTokenMongo.getAdditionalInformation())
                .add(FIELD_CLIENT_ID, oAuth2AccessTokenMongo.getClientId())
                .add(FIELD_USER_NAME, oAuth2AccessTokenMongo.getUserName())
                .add(FIELD_RESET_TIME, oAuth2AccessTokenMongo.getExpiration())
                .add(FIELD_UPDATED_AT, oAuth2AccessTokenMongo.getUpdatedAt())
                .add(FIELD_CREATED_AT, oAuth2AccessTokenMongo.getCreatedAt())
                .get();

        mongoOperations
                .getCollection(ACCESS_TOKEN_COLLECTION)
                .save(doc);
    }

    public void delete(String value) {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_KEY).is(value));

        mongoOperations
                .remove(query, ACCESS_TOKEN_COLLECTION);
    }

    public void deleteByRefreshToken(String refreshValue) {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_REFRESH_TOKEN).is(refreshValue));

        mongoOperations
                .remove(query, ACCESS_TOKEN_COLLECTION);
    }

    public OAuth2AccessTokenMongo findByAuthenticationKey(String authenticationKey) {
        BasicDBObject whereQuery = new BasicDBObject();
        whereQuery.put(FIELD_AUTHENTICATION_KEY, authenticationKey);

        DBObject result = mongoOperations
                .getCollection(ACCESS_TOKEN_COLLECTION)
                .findOne(whereQuery);

        OAuth2AccessTokenMongo oAuth2AccessTokenMongo = null;

        if (result != null) {
            oAuth2AccessTokenMongo = convert(result);
        }

        return oAuth2AccessTokenMongo;
    }

    public List<OAuth2AccessTokenMongo> findByClientIdAndUserName(String clientId, String userName) {
        BasicDBObject andQuery = new BasicDBObject();
        List<BasicDBObject> obj = new ArrayList<>();
        obj.add(new BasicDBObject(FIELD_CLIENT_ID, clientId));
        obj.add(new BasicDBObject(FIELD_USER_NAME, userName));
        andQuery.put("$and", obj);

        DBCursor cursor = mongoOperations
                .getCollection(ACCESS_TOKEN_COLLECTION)
                .find(andQuery);

        List<OAuth2AccessTokenMongo> oAuth2AccessTokensMongo = new ArrayList<>();

        while (cursor.hasNext()) {
            DBObject result = cursor.next();
            OAuth2AccessTokenMongo oAuth2AccessTokenMongo = convert(result);
            oAuth2AccessTokensMongo.add(oAuth2AccessTokenMongo);
        }

        return  oAuth2AccessTokensMongo;
    }

    public List<OAuth2AccessTokenMongo> findByClientId(String clientId) {
        BasicDBObject whereQuery = new BasicDBObject();
        whereQuery.put(FIELD_CLIENT_ID, clientId);

        DBCursor cursor = mongoOperations
                .getCollection(ACCESS_TOKEN_COLLECTION)
                .find(whereQuery);

        List<OAuth2AccessTokenMongo> oAuth2AccessTokensMongo = new ArrayList<>();

        while (cursor.hasNext()) {
            DBObject result = cursor.next();
            OAuth2AccessTokenMongo oAuth2AccessTokenMongo = convert(result);
            oAuth2AccessTokensMongo.add(oAuth2AccessTokenMongo);
        }

        return  oAuth2AccessTokensMongo;
    }

    private OAuth2AccessTokenMongo convert(DBObject result) {
        OAuth2AccessTokenMongo oAuth2AccessTokenMongo = new OAuth2AccessTokenMongo();
        oAuth2AccessTokenMongo.setValue((String) result.get(FIELD_KEY));
        oAuth2AccessTokenMongo.setTokenType((String) result.get(FIELD_TOKEN_TYPE));
        oAuth2AccessTokenMongo.setRefreshToken((String) result.get(FIELD_REFRESH_TOKEN));
        Set<String> scopes = null;
        if (result.get(FIELD_SCOPE) != null) {
            scopes = new HashSet<>(((BasicDBList) result.get(FIELD_SCOPE)).toMap().values());
        }
        oAuth2AccessTokenMongo.setScope(scopes);
        oAuth2AccessTokenMongo.setAdditionalInformation((Map<String, Object>) result.get(FIELD_ADDITIONAL_INFORMATION));
        oAuth2AccessTokenMongo.setAuthentication((byte []) result.get(FIELD_AUTHENTICATION));
        oAuth2AccessTokenMongo.setAuthenticationKey((String) result.get(FIELD_AUTHENTICATION_KEY));
        oAuth2AccessTokenMongo.setClientId((String) result.get(FIELD_CLIENT_ID));
        oAuth2AccessTokenMongo.setUserName((String) result.get(FIELD_USER_NAME));
        oAuth2AccessTokenMongo.setExpiration((Date) result.get(FIELD_RESET_TIME));
        oAuth2AccessTokenMongo.setUpdatedAt((Date) result.get(FIELD_UPDATED_AT));
        oAuth2AccessTokenMongo.setCreatedAt((Date) result.get(FIELD_CREATED_AT));

        return oAuth2AccessTokenMongo;
    }

}
