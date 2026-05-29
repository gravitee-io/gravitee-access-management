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
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.model.AutomationNewIdentityProvider;
import io.gravitee.am.service.model.UpdateIdentityProvider;

/**
 * Maps between the shared {@link IdentityProvider} model and the symmetric
 * {@link AutomationIdentityProvider} projection used by both request and response
 * bodies. Internal-id fields ({@code id}, {@code referenceId}, {@code referenceType}),
 * operational flags ({@code system}, {@code external}, {@code managedBy},
 * {@code dataPlaneId}) and the {@code passwordPolicy} reference are intentionally not
 * surfaced.
 *
 * @author GraviteeSource Team
 */
public final class AutomationIdentityProviderMapper {

    private AutomationIdentityProviderMapper() {
    }

    public static AutomationIdentityProvider toAutomationIdentityProvider(IdentityProvider idp) {
        AutomationIdentityProvider out = new AutomationIdentityProvider();
        out.setAutomationKey(idp.getAutomationKey());
        out.setName(idp.getName());
        out.setType(idp.getType());
        out.setSystem(idp.isSystem());
        out.setConfiguration(idp.getConfiguration());
        out.setMappers(idp.getMappers());
        out.setRoleMapper(idp.getRoleMapper());
        out.setGroupMapper(idp.getGroupMapper());
        out.setDomainWhitelist(idp.getDomainWhitelist());
        out.setCreatedAt(idp.getCreatedAt());
        out.setUpdatedAt(idp.getUpdatedAt());
        return out;
    }

    public static AutomationNewIdentityProvider toNewIdentityProvider(AutomationIdentityProvider definition) {
        AutomationNewIdentityProvider newIdp = new AutomationNewIdentityProvider();
        newIdp.setAutomationKey(definition.getAutomationKey());
        newIdp.setName(definition.getName());
        newIdp.setType(definition.getType());
        newIdp.setConfiguration(definition.getConfiguration());
        newIdp.setDomainWhitelist(definition.getDomainWhitelist());
        return newIdp;
    }

    public static UpdateIdentityProvider toUpdateIdentityProvider(AutomationIdentityProvider definition) {
        UpdateIdentityProvider update = new UpdateIdentityProvider();
        update.setName(definition.getName());
        update.setType(definition.getType());
        update.setConfiguration(definition.getConfiguration());
        update.setMappers(definition.getMappers());
        update.setRoleMapper(definition.getRoleMapper());
        update.setGroupMapper(definition.getGroupMapper());
        update.setDomainWhitelist(definition.getDomainWhitelist());
        return update;
    }
}
