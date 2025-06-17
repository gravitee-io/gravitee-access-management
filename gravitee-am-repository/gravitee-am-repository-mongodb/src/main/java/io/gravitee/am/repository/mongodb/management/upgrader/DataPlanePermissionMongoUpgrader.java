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

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.gravitee.am.common.scope.ManagementRepositoryScope;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.repository.mongodb.management.internal.model.ReporterMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.RoleMongo;
import io.gravitee.node.api.upgrader.Upgrader;
import io.reactivex.rxjava3.core.Flowable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.in;

@RequiredArgsConstructor
@Slf4j
@Component
@ManagementRepositoryScope
public class DataPlanePermissionMongoUpgrader implements Upgrader {
    private final MongoDatabase mongo;

    @Override
    public boolean upgrade() {
        var reportersCollection = mongo.getCollection("roles", RoleMongo.class);
        return Flowable.fromPublisher(reportersCollection.find(and(in("name", "ORGANIZATION_OWNER", "ORGANIZATION_USER", "ENVIRONMENT_OWNER", "ENVIRONMENT_USER"), exists("permissionAcls.DATA_PLANE", false))))
                .flatMap(role -> {
                    role.getPermissionAcls().putIfAbsent(Permission.DATA_PLANE.name(), Set.of(Acl.READ, Acl.LIST));
                    return reportersCollection.replaceOne(eq("_id", role.getId()), role);
                })
                .map(r -> r.getModifiedCount() > 0)
                .switchIfEmpty(Flowable.just(true))
                .reduce(Boolean.TRUE, (acc, opResult) -> acc && opResult)
                .onErrorReturn(ex -> {
                    log.error("Error adding DATA_PLANE permission on ORGANIZATION_OWNER and ENVIRONMENT_OWNER", ex);
                    return false;
                })
                .blockingGet();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
