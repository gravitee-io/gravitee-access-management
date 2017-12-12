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

import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.IdentityProviderMongo;
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
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoIdentityProviderRepository extends AbstractManagementMongoRepository implements IdentityProviderRepository {

    private static final String FIELD_DOMAIN = "domain";

    @PostConstruct
    public void ensureIndexes() {
        mongoOperations.indexOps(IdentityProviderMongo.class)
                .ensureIndex(new Index()
                        .on(FIELD_DOMAIN, Sort.Direction.ASC));
    }

    @Override
    public Set<IdentityProvider> findByDomain(String domain) throws TechnicalException {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_DOMAIN).is(domain));

        return mongoOperations
                .find(query, IdentityProviderMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<IdentityProvider> findById(String identityProviderId) throws TechnicalException {
        return Optional.ofNullable(convert(mongoOperations.findById(identityProviderId, IdentityProviderMongo.class)));
    }

    @Override
    public IdentityProvider create(IdentityProvider item) throws TechnicalException {
        IdentityProviderMongo identityProvider = convert(item);
        mongoOperations.save(identityProvider);
        return convert(identityProvider);
    }

    @Override
    public IdentityProvider update(IdentityProvider item) throws TechnicalException {
        IdentityProviderMongo identityProvider = convert(item);
        mongoOperations.save(identityProvider);
        return convert(identityProvider);
    }

    @Override
    public void delete(String id) throws TechnicalException {
        IdentityProviderMongo identityProvider = mongoOperations.findById(id, IdentityProviderMongo.class);
        mongoOperations.remove(identityProvider);
    }

    private IdentityProvider convert(IdentityProviderMongo identityProviderMongo) {
        if (identityProviderMongo == null) {
            return null;
        }

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setId(identityProviderMongo.getId());
        identityProvider.setName(identityProviderMongo.getName());
        identityProvider.setType(identityProviderMongo.getType());
        identityProvider.setConfiguration(identityProviderMongo.getConfiguration());
        identityProvider.setMappers(identityProviderMongo.getMappers());
        identityProvider.setRoleMapper(identityProviderMongo.getRoleMapper());
        identityProvider.setDomain(identityProviderMongo.getDomain());
        identityProvider.setCreatedAt(identityProviderMongo.getCreatedAt());
        identityProvider.setUpdatedAt(identityProviderMongo.getUpdatedAt());
        return identityProvider;
    }

    private IdentityProviderMongo convert(IdentityProvider identityProvider) {
        if (identityProvider == null) {
            return null;
        }

        IdentityProviderMongo identityProviderMongo = new IdentityProviderMongo();
        identityProviderMongo.setId(identityProvider.getId());
        identityProviderMongo.setName(identityProvider.getName());
        identityProviderMongo.setType(identityProvider.getType());
        identityProviderMongo.setConfiguration(identityProvider.getConfiguration());
        identityProviderMongo.setMappers(identityProvider.getMappers());
        identityProviderMongo.setRoleMapper(identityProvider.getRoleMapper());
        identityProviderMongo.setDomain(identityProvider.getDomain());
        identityProviderMongo.setCreatedAt(identityProvider.getCreatedAt());
        identityProviderMongo.setUpdatedAt(identityProvider.getUpdatedAt());
        return identityProviderMongo;
    }
}
