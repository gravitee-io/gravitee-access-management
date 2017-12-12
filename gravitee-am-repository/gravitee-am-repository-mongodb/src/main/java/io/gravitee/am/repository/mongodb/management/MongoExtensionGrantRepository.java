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
package io.gravitee.am.repository.mongodb.management;

import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.ExtensionGrantMongo;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoExtensionGrantRepository extends AbstractManagementMongoRepository implements ExtensionGrantRepository {

    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_GRANT_TYPE = "grantType";

    @PostConstruct
    public void ensureIndexes() {
        mongoOperations.indexOps(ExtensionGrantMongo.class)
                .ensureIndex(new Index()
                        .on(FIELD_DOMAIN, Sort.Direction.ASC));

        mongoOperations.indexOps(ExtensionGrantMongo.class)
                .ensureIndex(new Index()
                        .on(FIELD_DOMAIN, Sort.Direction.ASC)
                        .on(FIELD_GRANT_TYPE, Sort.Direction.ASC));
    }

    @Override
    public Set<ExtensionGrant> findByDomain(String domain) throws TechnicalException {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_DOMAIN).is(domain));

        return mongoOperations
                .find(query, ExtensionGrantMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<ExtensionGrant> findByDomainAndGrantType(String domain, String grantType) throws TechnicalException {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_DOMAIN).is(domain).and(FIELD_GRANT_TYPE).is(grantType));

        return Optional.ofNullable(convert(mongoOperations.findOne(query, ExtensionGrantMongo.class)));
    }

    @Override
    public Optional<ExtensionGrant> findById(String tokenGranterId) throws TechnicalException {
        return Optional.ofNullable(convert(mongoOperations.findById(tokenGranterId, ExtensionGrantMongo.class)));
    }

    @Override
    public ExtensionGrant create(ExtensionGrant item) throws TechnicalException {
        ExtensionGrantMongo tokenGranter = convert(item);
        mongoOperations.save(tokenGranter);
        return convert(tokenGranter);
    }

    @Override
    public ExtensionGrant update(ExtensionGrant item) throws TechnicalException {
        ExtensionGrantMongo tokenGranter = convert(item);
        mongoOperations.save(tokenGranter);
        return convert(tokenGranter);
    }

    @Override
    public void delete(String id) throws TechnicalException {
        ExtensionGrantMongo tokenGranter = mongoOperations.findById(id, ExtensionGrantMongo.class);
        mongoOperations.remove(tokenGranter);
    }

    private ExtensionGrant convert(ExtensionGrantMongo extensionGrantMongo) {
        if (extensionGrantMongo == null) {
            return null;
        }

        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setId(extensionGrantMongo.getId());
        extensionGrant.setName(extensionGrantMongo.getName());
        extensionGrant.setType(extensionGrantMongo.getType());
        extensionGrant.setConfiguration(extensionGrantMongo.getConfiguration());
        extensionGrant.setDomain(extensionGrantMongo.getDomain());
        extensionGrant.setGrantType(extensionGrantMongo.getGrantType());
        extensionGrant.setIdentityProvider(extensionGrantMongo.getIdentityProvider());
        extensionGrant.setCreateUser(extensionGrantMongo.isCreateUser());
        extensionGrant.setCreatedAt(extensionGrantMongo.getCreatedAt());
        extensionGrant.setUpdatedAt(extensionGrantMongo.getUpdatedAt());
        return extensionGrant;
    }

    private ExtensionGrantMongo convert(ExtensionGrant extensionGrant) {
        if (extensionGrant == null) {
            return null;
        }

        ExtensionGrantMongo extensionGrantMongo = new ExtensionGrantMongo();
        extensionGrantMongo.setId(extensionGrant.getId());
        extensionGrantMongo.setName(extensionGrant.getName());
        extensionGrantMongo.setType(extensionGrant.getType());
        extensionGrantMongo.setConfiguration(extensionGrant.getConfiguration());
        extensionGrantMongo.setDomain(extensionGrant.getDomain());
        extensionGrantMongo.setGrantType(extensionGrant.getGrantType());
        extensionGrantMongo.setIdentityProvider(extensionGrant.getIdentityProvider());
        extensionGrantMongo.setCreateUser(extensionGrant.isCreateUser());
        extensionGrantMongo.setCreatedAt(extensionGrant.getCreatedAt());
        extensionGrantMongo.setUpdatedAt(extensionGrant.getUpdatedAt());
        return extensionGrantMongo;
    }
}
