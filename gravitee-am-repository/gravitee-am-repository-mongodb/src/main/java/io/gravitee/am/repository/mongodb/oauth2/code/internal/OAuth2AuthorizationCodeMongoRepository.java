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
package io.gravitee.am.repository.mongodb.oauth2.code.internal;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import io.gravitee.am.repository.mongodb.oauth2.code.model.OAuth2AuthorizationCodeMongo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class OAuth2AuthorizationCodeMongoRepository {

    private static final String AUTHORIZATION_CODE_TOKEN_COLLECTION = "oauth2_authorization_codes";
    private static final String FIELD_KEY = "_id";
    private static final String FIELD_RESET_TIME = "expiration";
    private static final String FIELD_AUTHENTICATION = "authentication";
    private static final String FIELD_CREATED_AT = "created_at";
    private static final String FIELD_UPDATED_AT = "updated_at";

    @Autowired
    @Qualifier("oauth2MongoTemplate")
    private MongoOperations mongoOperations;

    @PostConstruct
    public void ensureTTLIndex() {
        mongoOperations.indexOps(AUTHORIZATION_CODE_TOKEN_COLLECTION).ensureIndex(new IndexDefinition() {
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
    }

    public void store(OAuth2AuthorizationCodeMongo authorizationCode) {
        final DBObject doc = BasicDBObjectBuilder.start()
                .add(FIELD_KEY, authorizationCode.getCode())
                .add(FIELD_AUTHENTICATION, authorizationCode.getOAuth2Authentication())
                .add(FIELD_RESET_TIME, authorizationCode.getExpiration())
                .add(FIELD_CREATED_AT, authorizationCode.getCreatedAt())
                .add(FIELD_UPDATED_AT, authorizationCode.getUpdatedAt())
                .get();

        mongoOperations
                .getCollection(AUTHORIZATION_CODE_TOKEN_COLLECTION)
                .save(doc);
    }

    public OAuth2AuthorizationCodeMongo remove(String code) {
        BasicDBObject query = new BasicDBObject();
        query.put(FIELD_KEY, code);

        DBObject result = mongoOperations.getCollection(AUTHORIZATION_CODE_TOKEN_COLLECTION).findAndRemove(query);

        OAuth2AuthorizationCodeMongo oAuth2AuthorizationCodeMongo = null;
        if (result != null) {
            oAuth2AuthorizationCodeMongo = new OAuth2AuthorizationCodeMongo();
            oAuth2AuthorizationCodeMongo.setCode((String) result.get(FIELD_KEY));
            oAuth2AuthorizationCodeMongo.setOAuth2Authentication((byte []) result.get(FIELD_AUTHENTICATION));
            oAuth2AuthorizationCodeMongo.setExpiration((Date) result.get(FIELD_RESET_TIME));
            oAuth2AuthorizationCodeMongo.setUpdatedAt((Date) result.get(FIELD_UPDATED_AT));
            oAuth2AuthorizationCodeMongo.setCreatedAt((Date) result.get(FIELD_CREATED_AT));
        }
        return oAuth2AuthorizationCodeMongo;
    }
}
