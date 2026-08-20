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

import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author GraviteeSource Team
 */
class PermissionTest {

    @Nested
    class Unflatten {

        @Test
        void shouldReturnEmptyPermissionsWhenListIsNull() {
            assertThat(Permission.unflatten(null)).isEmpty();
        }

        @Test
        void shouldReturnEmptyPermissionsWhenListIsEmpty() {
            assertThat(Permission.unflatten(List.of())).isEmpty();
        }

        @Test
        void shouldGroupEveryAclUnderItsPermission() {
            Map<Permission, Set<Acl>> permissions = Permission.unflatten(List.of("domain_read", "domain_update", "application_read"));

            assertThat(permissions).containsOnly(
                    Map.entry(Permission.DOMAIN, Set.of(Acl.READ, Acl.UPDATE)),
                    Map.entry(Permission.APPLICATION, Set.of(Acl.READ)));
        }

        @Test
        void shouldParsePermissionNameContainingUnderscores() {
            Map<Permission, Set<Acl>> permissions = Permission.unflatten(List.of("organization_identity_provider_read"));

            assertThat(permissions).containsOnly(Map.entry(Permission.ORGANIZATION_IDENTITY_PROVIDER, Set.of(Acl.READ)));
        }

        @Test
        void shouldParseRegardlessOfCase() {
            Map<Permission, Set<Acl>> permissions = Permission.unflatten(List.of("DOMAIN_READ", "domain_list", "Domain_Update"));

            assertThat(permissions).containsOnly(Map.entry(Permission.DOMAIN, Set.of(Acl.READ, Acl.LIST, Acl.UPDATE)));
        }

        @Test
        void shouldCollapseDuplicateEntries() {
            Map<Permission, Set<Acl>> permissions = Permission.unflatten(List.of("domain_read", "domain_read"));

            assertThat(permissions).containsOnly(Map.entry(Permission.DOMAIN, Set.of(Acl.READ)));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {
                "",
                " ",
                "read",                 // no separator at all
                "domain",               // permission with no acl
                "_read",                // empty permission
                "domain_",              // empty acl
                "domain_foo",           // unknown acl
                "foo_read",             // unknown permission
                "domain_read_read",     // acl left in the permission half
                " domain_read",         // untrimmed
                "domain_read ",
                "domain-read",          // wrong separator
        })
        void shouldRejectMalformedPermission(String flatPermission) {
            List<String> flatPermissions = Collections.singletonList(flatPermission);

            assertThatThrownBy(() -> Permission.unflatten(flatPermissions))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid permission(s)")
                    .hasMessageContaining(String.valueOf(flatPermission));
        }

        @Test
        void shouldRejectTheWholeListWhenASingleEntryIsInvalid() {
            List<String> flatPermissions = List.of("domain_read", "foo_read");

            assertThatThrownBy(() -> Permission.unflatten(flatPermissions))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("foo_read")
                    .hasMessageNotContaining("domain_read");
        }

        @Test
        void shouldReportEveryInvalidEntry() {
            List<String> flatPermissions = Arrays.asList("foo_read", "domain_foo", null);

            assertThatThrownBy(() -> Permission.unflatten(flatPermissions))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("foo_read")
                    .hasMessageContaining("domain_foo")
                    .hasMessageContaining("null");
        }
    }

    @Nested
    class RoundTrip {

        @Test
        void shouldUnflattenWhatFlattenProduced() {
            Map<Permission, Set<Acl>> permissions = Map.of(
                    Permission.ORGANIZATION_IDENTITY_PROVIDER, Set.of(Acl.READ, Acl.LIST),
                    Permission.DOMAIN, Acl.all());

            assertThat(Permission.unflatten(Permission.flatten(permissions))).isEqualTo(permissions);
        }

        @Test
        void shouldUnflattenEveryPermissionOfAReferenceType() {
            Map<Permission, Set<Acl>> permissions = Permission.allPermissionAcls(ReferenceType.DOMAIN);

            assertThat(Permission.unflatten(Permission.flatten(permissions))).isEqualTo(permissions);
        }
    }
}
