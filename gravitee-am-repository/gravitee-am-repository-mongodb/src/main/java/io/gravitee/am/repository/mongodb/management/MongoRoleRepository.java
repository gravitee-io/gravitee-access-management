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

import io.gravitee.am.model.Role;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.RoleRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.RoleMongo;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoRoleRepository extends AbstractManagementMongoRepository implements RoleRepository {

    private static final String FIELD_DOMAIN = "domain";
    private static final String ID_FIELD = "_id";

    @PostConstruct
    public void ensureIndexes() {
        mongoOperations.indexOps(RoleMongo.class)
                .ensureIndex(new Index()
                        .on(FIELD_DOMAIN, Sort.Direction.ASC));
    }

    @Override
    public Set<Role> findByDomain(String domain) throws TechnicalException {
        Query query = new Query();
        query.addCriteria(Criteria.where(FIELD_DOMAIN).is(domain));

        return mongoOperations
                .find(query, RoleMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Role> findByIdIn(List<String> ids) throws TechnicalException {
        Query query = new Query();
        query.addCriteria(Criteria.where(ID_FIELD).in(ids));

        return mongoOperations
                .find(query, RoleMongo.class)
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<Role> findById(String role) throws TechnicalException {
        return Optional.ofNullable(convert(mongoOperations.findById(role, RoleMongo.class)));
    }

    @Override
    public Role create(Role item) throws TechnicalException {
        RoleMongo role = convert(item);
        mongoOperations.save(role);
        return convert(role);
    }

    @Override
    public Role update(Role item) throws TechnicalException {
        RoleMongo role = convert(item);
        mongoOperations.save(role);
        return convert(role);
    }

    @Override
    public void delete(String id) throws TechnicalException {
        RoleMongo role = mongoOperations.findById(id, RoleMongo.class);
        mongoOperations.remove(role);
    }

    private Role convert(RoleMongo roleMongo) {
        if (roleMongo == null) {
            return null;
        }

        Role role = new Role();
        role.setId(roleMongo.getId());
        role.setName(roleMongo.getName());
        role.setDescription(roleMongo.getDescription());
        role.setDomain(roleMongo.getDomain());
        role.setPermissions(roleMongo.getPermissions());
        role.setCreatedAt(roleMongo.getCreatedAt());
        role.setUpdatedAt(roleMongo.getUpdatedAt());
        return role;
    }

    private RoleMongo convert(Role role) {
        if (role == null) {
            return null;
        }

        RoleMongo roleMongo = new RoleMongo();
        roleMongo.setId(role.getId());
        roleMongo.setName(role.getName());
        roleMongo.setDescription(role.getDescription());
        roleMongo.setDomain(role.getDomain());
        roleMongo.setPermissions(role.getPermissions());
        roleMongo.setCreatedAt(role.getCreatedAt());
        roleMongo.setUpdatedAt(role.getUpdatedAt());
        return roleMongo;
    }
}
