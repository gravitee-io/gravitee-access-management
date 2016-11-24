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


import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import io.gravitee.am.repository.mongodb.oauth2.token.internal.model.OAuth2RefreshTokenMongo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class OAuth2RefreshTokenMongoRepository {

    private static final String REFRESH_TOKEN_COLLECTION = "oauth2_refresh_tokens";
    private static final String FIELD_KEY = "_id";
    private static final String FIELD_RESET_TIME = "expiration";
    private static final String FIELD_AUTHENTICATION = "authentication";
    private static final String FIELD_UPDATED_AT = "updated_at";
    private static final String FIELD_CREATED_AT = "created_at";

    @Autowired
    @Qualifier("tokenMongoTemplate")
    private MongoOperations mongoOperations;

    @PostConstruct
    public void ensureTTLIndex() {
        mongoOperations.indexOps(REFRESH_TOKEN_COLLECTION).ensureIndex(new IndexDefinition() {
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

    public OAuth2RefreshTokenMongo findOne(String value) {
        DBObject result = mongoOperations
                .getCollection(REFRESH_TOKEN_COLLECTION)
                .findOne(value);

        OAuth2RefreshTokenMongo oAuth2RefreshTokenMongo = null;

        if (result != null) {
            oAuth2RefreshTokenMongo = new OAuth2RefreshTokenMongo();
            oAuth2RefreshTokenMongo.setValue(value);
            oAuth2RefreshTokenMongo.setAuthentication((byte []) result.get(FIELD_AUTHENTICATION));
            oAuth2RefreshTokenMongo.setExpiration((Date) result.get(FIELD_RESET_TIME));
            oAuth2RefreshTokenMongo.setUpdatedAt((Date) result.get(FIELD_UPDATED_AT));
            oAuth2RefreshTokenMongo.setCreatedAt((Date) result.get(FIELD_CREATED_AT));
        }

        return oAuth2RefreshTokenMongo;
    }

    public void save(OAuth2RefreshTokenMongo oAuth2RefreshTokenMongo) {
        final DBObject doc = BasicDBObjectBuilder.start()
                .add(FIELD_KEY, oAuth2RefreshTokenMongo.getValue())
                .add(FIELD_AUTHENTICATION, oAuth2RefreshTokenMongo.getAuthentication())
                .add(FIELD_RESET_TIME, oAuth2RefreshTokenMongo.getExpiration())
                .add(FIELD_UPDATED_AT, oAuth2RefreshTokenMongo.getUpdatedAt())
                .add(FIELD_CREATED_AT, oAuth2RefreshTokenMongo.getCreatedAt())
                .get();

        mongoOperations
                .getCollection(REFRESH_TOKEN_COLLECTION)
                .save(doc);
    }

    public void delete(String value) {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_KEY).is(value));

        mongoOperations
                .remove(query, REFRESH_TOKEN_COLLECTION);
    }
}
