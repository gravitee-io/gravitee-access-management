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
package io.gravitee.am.management.service.permissions;

import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.permissions.Permission;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
class SimplePermissionAcls implements PermissionAcls {

    private ReferenceType referenceType;
    private String referenceId;
    private Permission permission;
    private Set<Acl> acls;

    public SimplePermissionAcls(ReferenceType referenceType, String referenceId, Permission permission, Set<Acl> acls) {

        Objects.requireNonNull(referenceType);
        Objects.requireNonNull(referenceId);
        Objects.requireNonNull(permission);
        Objects.requireNonNull(acls);

        if (!permission.isRelevantWith(referenceType)) {
            throw new IllegalArgumentException(String.format("The permission [%s] is not relevant with type [%s]. ", permission, referenceType));
        }

        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.permission = permission;
        this.acls = acls;
    }

    @Override
    public boolean match(Map<Membership, Map<Permission, Set<Acl>>> permissions) {

        return permissions.keySet().stream()
                .filter(membership -> permission.isRelevantWith(membership.getReferenceType()))
                .filter(membership -> membership.getReferenceType() == referenceType && membership.getReferenceId().equals(referenceId))
                .flatMap(membership -> permissions.get(membership).getOrDefault(permission, emptySet()).stream())
                .collect(Collectors.toSet())
                .containsAll(acls);
    }

    @Override
    public Stream<Map.Entry<ReferenceType, String>> referenceStream() {

        return Stream.of(new AbstractMap.SimpleEntry<>(referenceType, referenceId));
    }

    public Permission getPermission() {
        return permission;
    }

    public void setPermission(Permission permission) {
        this.permission = permission;
    }

    public Set<Acl> getAcls() {
        return acls;
    }

    public void setAcls(Set<Acl> acls) {
        this.acls = acls;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }
}