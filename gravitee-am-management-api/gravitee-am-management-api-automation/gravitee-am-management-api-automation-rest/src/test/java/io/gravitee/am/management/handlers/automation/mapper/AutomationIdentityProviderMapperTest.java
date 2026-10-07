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
package io.gravitee.am.management.handlers.automation.mapper;

import io.gravitee.am.management.handlers.automation.model.AutomationIdentityProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class AutomationIdentityProviderMapperTest {

    private static final Map<String, String> MAPPERS = Map.of("email", "mail");
    private static final Map<String, String[]> ROLE_MAPPER = Map.of("ADMIN", new String[]{"groupname=admins"});
    private static final Map<String, String[]> GROUP_MAPPER = Map.of("devs", new String[]{"groupname=developers"});

    private static AutomationIdentityProvider definition() {
        AutomationIdentityProvider definition = new AutomationIdentityProvider();
        definition.setAutomationKey("ldap");
        definition.setName("LDAP");
        definition.setType("ldap-am-idp");
        definition.setConfiguration("{}");
        definition.setMappers(MAPPERS);
        definition.setRoleMapper(ROLE_MAPPER);
        definition.setGroupMapper(GROUP_MAPPER);
        return definition;
    }

    @Test
    void carriesMappersOntoTheCreatePayload() {
        var newIdp = AutomationIdentityProviderMapper.toNewIdentityProvider(definition());

        assertThat(newIdp.getMappers()).isEqualTo(MAPPERS);
        assertThat(newIdp.getRoleMapper()).isEqualTo(ROLE_MAPPER);
        assertThat(newIdp.getGroupMapper()).isEqualTo(GROUP_MAPPER);
    }

    @Test
    void carriesMappersOntoTheUpdatePayload() {
        var update = AutomationIdentityProviderMapper.toUpdateIdentityProvider(definition());

        assertThat(update.getMappers()).isEqualTo(MAPPERS);
        assertThat(update.getRoleMapper()).isEqualTo(ROLE_MAPPER);
        assertThat(update.getGroupMapper()).isEqualTo(GROUP_MAPPER);
    }
}
