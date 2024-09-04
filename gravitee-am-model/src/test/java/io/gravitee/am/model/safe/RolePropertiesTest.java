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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.permissions.Permission;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RolePropertiesTest {
    @Test
    public void should_return_null() {
        Assertions.assertThat(RoleProperties.from(null)).isNull();
    }

    @Test
    public void should_contains_data() {
        final var role = new Role();
        role.setId(UUID.randomUUID().toString());
        role.setName(UUID.randomUUID().toString());
        role.setDescription(UUID.randomUUID().toString());
        role.setDefaultRole(true);
        role.setSystem(false);
        role.setAssignableType(ReferenceType.DOMAIN);
        role.setOauthScopes(List.of(UUID.randomUUID().toString()));
        role.setPermissionAcls(Map.of(Permission.APPLICATION, Set.of(Acl.READ)));

        final var roleProperties = RoleProperties.from(role);
        Assertions.assertThat(role)
                .hasFieldOrPropertyWithValue("id", roleProperties.getId())
                .hasFieldOrPropertyWithValue("name", roleProperties.getName())
                .hasFieldOrPropertyWithValue("description", roleProperties.getDescription())
                .hasFieldOrPropertyWithValue("defaultRole", roleProperties.isDefaultRole())
                .hasFieldOrPropertyWithValue("system", roleProperties.isSystem());
        Assertions.assertThat(roleProperties.getOauthScopes()).isEqualTo(role.getOauthScopes());
        Assertions.assertThat(roleProperties.getPermissionAcls()).isEqualTo(role.getPermissionAcls());
    }

    @Test
    public void should_accept_null_scopes_and_acls() {
        final var role = new Role();
        role.setId(UUID.randomUUID().toString());
        role.setName(UUID.randomUUID().toString());
        role.setDescription(UUID.randomUUID().toString());
        role.setDefaultRole(true);
        role.setSystem(false);
        role.setAssignableType(ReferenceType.DOMAIN);
        role.setOauthScopes(null);
        role.setPermissionAcls(null);

        final var roleProperties = RoleProperties.from(role);
        Assertions.assertThat(role)
                .hasFieldOrPropertyWithValue("id", roleProperties.getId())
                .hasFieldOrPropertyWithValue("name", roleProperties.getName())
                .hasFieldOrPropertyWithValue("description", roleProperties.getDescription())
                .hasFieldOrPropertyWithValue("defaultRole", roleProperties.isDefaultRole())
                .hasFieldOrPropertyWithValue("system", roleProperties.isSystem());
        Assertions.assertThat(roleProperties.getOauthScopes()).isEqualTo(List.of());
        Assertions.assertThat(roleProperties.getPermissionAcls()).isEqualTo(Map.of());
    }
}
