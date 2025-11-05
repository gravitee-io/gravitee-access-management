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
package io.gravitee.am.repository.mongodb.management.upgrader;

import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.common.scope.ManagementRepositoryScope;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.repository.mongodb.management.internal.model.RoleMongo;
import io.gravitee.node.api.upgrader.Upgrader;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.in;
import static io.gravitee.am.model.permissions.DefaultRole.DOMAIN_OWNER;
import static io.gravitee.am.model.permissions.DefaultRole.ENVIRONMENT_OWNER;
import static io.gravitee.am.model.permissions.DefaultRole.ORGANIZATION_OWNER;

@RequiredArgsConstructor
@Slf4j
@Component
@ManagementRepositoryScope
public class ProtectedResourcePermissionMongoUpgrader implements Upgrader {
    private final MongoDatabase mongo;
    private final String ProtectedResourceFieldName = "permissionAcls." + Permission.PROTECTED_RESOURCE.name();
    private final Set<String> roles = Set.of(ORGANIZATION_OWNER.name(), DOMAIN_OWNER.name(), ENVIRONMENT_OWNER.name());

    @Override
    public boolean upgrade() {
        var rolesCollection = mongo.getCollection("roles", RoleMongo.class);
        var filter = and(in("name", roles), exists(ProtectedResourceFieldName, false));
        var update = Updates.set(ProtectedResourceFieldName, Acl.all());

        return Completable.fromPublisher(rolesCollection.updateMany(filter, update))
                .toSingleDefault(true)
                .onErrorReturn(ex -> {
                    log.error("Error adding PROTECTED_RESOURCE permissions on {}", roles, ex);
                    return false;
                })
                .blockingGet();
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
