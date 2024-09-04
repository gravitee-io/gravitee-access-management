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

package io.gravitee.am.model.safe;

import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.permissions.Permission;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.util.CollectionUtils.isEmpty;

@Data
public class RoleProperties {
    private final String id;
    private final String name;
    private final String description;
    private final Map<Permission, Set<Acl>> permissionAcls;
    private final List<String> oauthScopes;
    private final boolean system;
    private final boolean defaultRole;

    public static RoleProperties from(Role role) {
        if (role == null) {
            return null;
        }
        final Map<Permission, Set<Acl>> permissions = isEmpty(role.getPermissionAcls()) ? Map.of() : new HashMap<>(role.getPermissionAcls());
        final List<String> scopes = isEmpty(role.getOauthScopes()) ? List.of() : List.copyOf(role.getOauthScopes());
        return new RoleProperties(role.getId(), role.getName(), role.getDescription(), permissions, scopes, role.isSystem(), role.isDefaultRole());
    }

}
