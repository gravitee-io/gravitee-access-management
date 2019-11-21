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
package io.gravitee.am.model.permissions;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum RolePermissionAction {
    CREATE("CREATE", "create"),
    READ("READ", "read"),
    UPDATE("UPDATE", "update"),
    DELETE("DELETE", "delete");

    String id;
    String mask;

    RolePermissionAction(String id, String mask) {
        this.id = id;
        this.mask = mask;
    }

    public String getId() {
        return id;
    }

    public String getMask() {
        return mask;
    }

    public static RolePermissionAction findById(String id) {
        for (RolePermissionAction rolePermissionAction : RolePermissionAction.values()) {
            if (id == rolePermissionAction.id) {
                return rolePermissionAction;
            }
        }
        throw new IllegalArgumentException(id + " not a RolePermissionAction id");
    }

    public static List<String> actions() {
        return Arrays.asList(values()).stream().map(RolePermissionAction::getMask).collect(Collectors.toList());
    }
}
