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

import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ScopeRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.ScopeMongo;
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
public class MongoScopeRepository extends AbstractManagementMongoRepository implements ScopeRepository {

    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_KEY = "key";

    @PostConstruct
    public void ensureIndexes() {
        mongoOperations.indexOps(ScopeMongo.class)
                .ensureIndex(new Index()
                        .on(FIELD_DOMAIN, Sort.Direction.ASC));

        mongoOperations.indexOps(ScopeMongo.class)
                .ensureIndex(new Index()
                        .on(FIELD_DOMAIN, Sort.Direction.ASC)
                        .on(FIELD_KEY, Sort.Direction.ASC));
    }

    @Override
    public Optional<Scope> findById(String id) throws TechnicalException {
        return Optional.ofNullable(convert(mongoOperations.findById(id, ScopeMongo.class)));
    }

    @Override
    public Scope create(Scope scope) throws TechnicalException {
        ScopeMongo domain = convert(scope);
        mongoOperations.save(domain);
        return convert(domain);
    }

    @Override
    public Scope update(Scope scope) throws TechnicalException {
        ScopeMongo domain = convert(scope);
        mongoOperations.save(domain);
        return convert(domain);
    }

    @Override
    public void delete(String id) throws TechnicalException {
        ScopeMongo scope = mongoOperations.findById(id, ScopeMongo.class);
        mongoOperations.remove(scope);
    }

    @Override
    public Set<Scope> findByDomain(String domain) throws TechnicalException {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_DOMAIN).is(domain));

        return mongoOperations
                .find(query, ScopeMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<Scope> findByDomainAndKey(String domain, String key) throws TechnicalException {
        Query query = new Query();
        query.addCriteria(
                Criteria.where(FIELD_DOMAIN).is(domain)
                        .andOperator(Criteria.where(FIELD_KEY).is(key)));

        ScopeMongo scope = mongoOperations.findOne(query, ScopeMongo.class);
        return Optional.ofNullable(convert(scope));
    }

    private Scope convert(ScopeMongo scopeMongo) {
        if (scopeMongo == null) {
            return null;
        }

        Scope scope = new Scope();
        scope.setId(scopeMongo.getId());
        scope.setKey(scopeMongo.getKey());
        scope.setName(scopeMongo.getName());
        scope.setDescription(scopeMongo.getDescription());
        scope.setDomain(scopeMongo.getDomain());
        scope.setNames(scopeMongo.getNames());
        scope.setDescriptions(scopeMongo.getDescriptions());
        scope.setCreatedAt(scopeMongo.getCreatedAt());
        scope.setUpdatedAt(scopeMongo.getUpdatedAt());

        return scope;
    }

    private ScopeMongo convert(Scope scope) {
        if (scope == null) {
            return null;
        }

        ScopeMongo scopeMongo = new ScopeMongo();
        scopeMongo.setId(scope.getId());
        scopeMongo.setKey(scope.getKey());
        scopeMongo.setName(scope.getName());
        scopeMongo.setDescription(scope.getDescription());
        scopeMongo.setDomain(scope.getDomain());
        scopeMongo.setNames(scope.getNames());
        scopeMongo.setDescriptions(scope.getDescriptions());
        scopeMongo.setCreatedAt(scope.getCreatedAt());
        scopeMongo.setUpdatedAt(scope.getUpdatedAt());

        return scopeMongo;
    }
}
