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

import com.mongodb.reactivestreams.client.MongoCollection;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.model.Role;
import io.gravitee.am.repository.management.api.RoleRepository;
import io.gravitee.am.repository.mongodb.common.LoggableIndexSubscriber;
import io.gravitee.am.repository.mongodb.management.internal.model.RoleMongo;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.mongodb.client.model.Filters.*;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoRoleRepository extends AbstractManagementMongoRepository implements RoleRepository {

    private static final String FIELD_ID = "_id";
    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_SCOPE = "scope";
    private MongoCollection<RoleMongo> rolesCollection;

    @PostConstruct
    public void init() {
        rolesCollection = mongoOperations.getCollection("roles", RoleMongo.class);
        rolesCollection.createIndex(new Document(FIELD_DOMAIN, 1)).subscribe(new LoggableIndexSubscriber());
        rolesCollection.createIndex(new Document(FIELD_DOMAIN, 1).append(FIELD_NAME, 1).append(FIELD_SCOPE, 1)).subscribe(new LoggableIndexSubscriber());
    }

    @Override
    public Single<Set<Role>> findByDomain(String domain) {
        return Observable.fromPublisher(rolesCollection.find(eq(FIELD_DOMAIN, domain))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Set<Role>> findByIdIn(List<String> ids) {
        return Observable.fromPublisher(rolesCollection.find(in(FIELD_ID, ids))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Maybe<Role> findById(String role) {
        return Observable.fromPublisher(rolesCollection.find(eq(FIELD_ID, role)).first()).firstElement().map(this::convert);
    }

    @Override
    public Single<Role> create(Role item) {
        RoleMongo role = convert(item);
        role.setId(role.getId() == null ? RandomString.generate() : role.getId());
        return Single.fromPublisher(rolesCollection.insertOne(role)).flatMap(success -> findById(role.getId()).toSingle());
    }

    @Override
    public Single<Role> update(Role item) {
        RoleMongo role = convert(item);
        return Single.fromPublisher(rolesCollection.replaceOne(eq(FIELD_ID, role.getId()), role)).flatMap(updateResult -> findById(role.getId()).toSingle());
    }

    @Override
    public Completable delete(String id) {
        return Completable.fromPublisher(rolesCollection.deleteOne(eq(FIELD_ID, id)));
    }

    @Override
    public Maybe<Role> findByDomainAndNameAndScope(String domain, String name, int scope) {
        return Observable.fromPublisher(rolesCollection.find(and(eq(FIELD_DOMAIN, domain), eq(FIELD_NAME, name), eq(FIELD_SCOPE, scope))).first()).firstElement().map(this::convert);
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
        role.setScope(roleMongo.getScope());
        role.setSystem(roleMongo.isSystem());
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
        roleMongo.setScope(role.getScope());
        roleMongo.setSystem(role.isSystem());
        roleMongo.setPermissions(role.getPermissions());
        roleMongo.setCreatedAt(role.getCreatedAt());
        roleMongo.setUpdatedAt(role.getUpdatedAt());
        return roleMongo;
    }
}
