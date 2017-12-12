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

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.mongodb.management.internal.model.ScopeMongo;
import io.gravitee.am.repository.mongodb.oauth2.internal.model.ScopeApprovalMongo;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoScopeApprovalRepository implements ScopeApprovalRepository {

    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_CLIENT_ID = "clientId";
    private final static String FIELD_EXPIRES_AT = "expiresAt";


    @Autowired
    @Qualifier("oauth2MongoTemplate")
    private MongoOperations mongoOperations;

    @PostConstruct
    public void ensureIndexes() {
        mongoOperations.indexOps(ScopeApprovalMongo.class).ensureIndex(new IndexDefinition() {
            @Override
            public DBObject getIndexKeys() {
                return new BasicDBObject(FIELD_EXPIRES_AT, 1);
            }

            @Override
            public DBObject getIndexOptions() {
                // To expire Documents at a Specific Clock Time we have to specify an expireAfterSeconds value of 0.
                return new BasicDBObject("expireAfterSeconds", 0);
            }
        });

        mongoOperations.indexOps(ScopeApprovalMongo.class)
                .ensureIndex(new Index()
                        .on(FIELD_DOMAIN, Sort.Direction.ASC)
                        .on(FIELD_CLIENT_ID, Sort.Direction.ASC)
                        .on(FIELD_USER_ID, Sort.Direction.ASC));
    }

    @Override
    public Optional<ScopeApproval> findById(String s) throws TechnicalException {
        throw new IllegalStateException();
    }

    @Override
    public ScopeApproval create(ScopeApproval scopeApproval) throws TechnicalException {
        ScopeApprovalMongo scopeApprovalMongo = convert(scopeApproval);
        mongoOperations.save(scopeApprovalMongo);
        return convert(scopeApprovalMongo);
    }

    @Override
    public ScopeApproval update(ScopeApproval scopeApproval) throws TechnicalException {
        ScopeApprovalMongo scopeApprovalMongo = convert(scopeApproval);
        mongoOperations.save(scopeApprovalMongo);
        return convert(scopeApprovalMongo);
    }

    @Override
    public void delete(String id) throws TechnicalException {
        ScopeMongo scope = mongoOperations.findById(id, ScopeMongo.class);
        mongoOperations.remove(scope);
    }

    @Override
    public Set<ScopeApproval> findByDomainAndUserAndClient(String domain, String userId, String clientId) throws TechnicalException {
        Query query = new Query();
        query
                .addCriteria(Criteria.where(FIELD_DOMAIN).is(domain))
                .addCriteria(Criteria.where(FIELD_CLIENT_ID).is(clientId))
                .addCriteria(Criteria.where(FIELD_USER_ID).is(userId));

        return mongoOperations
                .find(query, ScopeApprovalMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    private ScopeApproval convert(ScopeApprovalMongo scopeApprovalMongo) {
        if (scopeApprovalMongo == null) {
            return null;
        }

        ScopeApproval scopeApproval = new ScopeApproval();
        scopeApproval.setClientId(scopeApprovalMongo.getClientId());
        scopeApproval.setUserId(scopeApprovalMongo.getUserId());
        scopeApproval.setScope(scopeApprovalMongo.getScope());
        scopeApproval.setExpiresAt(scopeApprovalMongo.getExpiresAt());
        scopeApproval.setStatus(ScopeApproval.ApprovalStatus.valueOf(scopeApprovalMongo.getStatus().toUpperCase()));
        scopeApproval.setDomain(scopeApprovalMongo.getDomain());
        scopeApproval.setUpdatedAt(scopeApprovalMongo.getUpdatedAt());

        return scopeApproval;
    }

    private ScopeApprovalMongo convert(ScopeApproval scopeApproval) {
        if (scopeApproval == null) {
            return null;
        }

        ScopeApprovalMongo scopeApprovalMongo = new ScopeApprovalMongo();
        scopeApprovalMongo.setClientId(scopeApproval.getClientId());
        scopeApprovalMongo.setUserId(scopeApproval.getUserId());
        scopeApprovalMongo.setScope(scopeApproval.getScope());
        scopeApprovalMongo.setExpiresAt(scopeApproval.getExpiresAt());
        scopeApprovalMongo.setStatus(scopeApproval.getStatus().name().toUpperCase());
        scopeApprovalMongo.setDomain(scopeApproval.getDomain());
        scopeApprovalMongo.setUpdatedAt(scopeApproval.getUpdatedAt());

        return scopeApprovalMongo;
    }
}
