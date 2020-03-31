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
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.repository.management.api.RoleRepository;
import io.gravitee.am.repository.mongodb.management.internal.model.RoleMongo;
import io.reactivex.*;
import io.reactivex.Observable;
import org.bson.Document;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.*;
import static io.gravitee.am.model.ReferenceType.DOMAIN;

/**
 * @author Titouan COMPIEGNE (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoRoleRepository extends AbstractManagementMongoRepository implements RoleRepository {

    private static final String FIELD_ID = "_id";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_SCOPE = "scope";
    private static final String FIELD_ASSIGNABLE_TYPE = "assignableType";
    public static final String FIELD_SYSTEM = "system";
    private MongoCollection<RoleMongo> rolesCollection;

    @PostConstruct
    public void init() {
        rolesCollection = mongoOperations.getCollection("roles", RoleMongo.class);
        super.createIndex(rolesCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1));
        super.createIndex(rolesCollection, new Document(FIELD_REFERENCE_TYPE, 1).append(FIELD_REFERENCE_ID, 1).append(FIELD_NAME, 1).append(FIELD_SCOPE, 1));
    }

    @Override
    public Flowable<Role> findAll(ReferenceType referenceType, String referenceId) {
        return Flowable.fromPublisher(rolesCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType == null ? null : referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId)))).map(this::convert);
    }

    @Override
    public Single<Set<Role>> findByDomain(String domain) {
        return findAll(DOMAIN, domain).collect(HashSet::new, Set::add);
    }

    @Override
    public Single<Set<Role>> findByIdIn(List<String> ids) {
        return Observable.fromPublisher(rolesCollection.find(in(FIELD_ID, ids))).map(this::convert).collect(HashSet::new, Set::add);
    }

    @Override
    public Maybe<Role> findById(ReferenceType referenceType, String referenceId, String role) {
        return Observable.fromPublisher(rolesCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_ID, role))).first()).firstElement().map(this::convert);
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
    public Maybe<Role> findByNameAndAssignableType(ReferenceType referenceType, String referenceId, String name, ReferenceType assignableType) {
        return Observable.fromPublisher(rolesCollection.find(and(eq(FIELD_REFERENCE_TYPE, referenceType.name()), eq(FIELD_REFERENCE_ID, referenceId), eq(FIELD_NAME, name), eq(FIELD_ASSIGNABLE_TYPE, assignableType.name()))).first()).firstElement().map(this::convert);
    }

    private Role convert(RoleMongo roleMongo) {
        if (roleMongo == null) {
            return null;
        }

        Role role = new Role();
        role.setId(roleMongo.getId());
        role.setName(roleMongo.getName());
        role.setDescription(roleMongo.getDescription());
        role.setReferenceType(roleMongo.getReferenceType() == null ? null : ReferenceType.valueOf(roleMongo.getReferenceType()));
        role.setReferenceId(roleMongo.getReferenceId());
        role.setAssignableType(roleMongo.getAssignableType() == null ? null : ReferenceType.valueOf(roleMongo.getAssignableType()));
        role.setSystem(roleMongo.isSystem());

        if (roleMongo.getPermissions() != null) {
            Map<Permission, Set<Acl>> permissions = new HashMap<>();
            roleMongo.getPermissions().forEach((key, value) -> {
                try {
                    permissions.put(Permission.valueOf(key), new HashSet<>(value));
                } catch (IllegalArgumentException iae) {
                    // Ignore invalid Permission enum.
                }
            });

            role.setPermissions(permissions);
        }

        role.setOauthScopes(roleMongo.getOauthScopes());
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
        roleMongo.setReferenceType(role.getReferenceType() == null ? null : role.getReferenceType().name());
        roleMongo.setReferenceId(role.getReferenceId());
        roleMongo.setAssignableType(role.getAssignableType() == null ? null : role.getAssignableType().name());
        roleMongo.setSystem(role.isSystem());
        roleMongo.setPermissions(role.getPermissions() == null ? null : role.getPermissions().entrySet().stream().collect(Collectors.toMap(o -> o.getKey().name(), Map.Entry::getValue)));
        roleMongo.setOauthScopes(role.getOauthScopes());
        roleMongo.setCreatedAt(role.getCreatedAt());
        roleMongo.setUpdatedAt(role.getUpdatedAt());
        return roleMongo;
    }
}