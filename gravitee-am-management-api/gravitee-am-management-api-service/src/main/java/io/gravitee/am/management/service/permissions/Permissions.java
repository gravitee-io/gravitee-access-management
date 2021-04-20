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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class Permissions {

    public static PermissionAcls of(ReferenceType referenceType, String referenceId, Permission permission, Acl... acls) {
        return new SimplePermissionAcls(referenceType, referenceId, permission, new HashSet<>(Arrays.asList(acls)));
    }

    public static PermissionAcls and(PermissionAcls... permissionAcls) {
        return new AndPermissionAcls(permissionAcls);
    }

    public static PermissionAcls or(PermissionAcls... permissionAcls) {
        return new OrPermissionAcls(permissionAcls);
    }
}
