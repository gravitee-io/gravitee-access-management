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

import io.gravitee.am.management.handlers.automation.model.AutomationDataPlane;
import io.gravitee.am.service.model.DataPlaneDefinitionSummary;
import io.gravitee.am.service.model.NewDataPlaneDefinition;

/**
 * Maps between the {@link DataPlaneDefinitionSummary} the service layer returns and the
 * {@link AutomationDataPlane} projection.
 *
 * @author GraviteeSource Team
 */
public final class AutomationDataPlaneMapper {

    private AutomationDataPlaneMapper() {
    }

    public static AutomationDataPlane toAutomationDataPlane(DataPlaneDefinitionSummary summary) {
        AutomationDataPlane out = new AutomationDataPlane();
        out.setId(summary.id());
        out.setName(summary.name());
        out.setType(summary.type());
        out.setGatewayUrl(summary.gatewayUrl());
        out.setDatabase(summary.database());
        out.setHosts(summary.hosts());
        out.setOrganizationId(summary.organizationId());
        out.setEnvironmentId(summary.environmentId());
        out.setCreatedAt(summary.createdAt());
        out.setUpdatedAt(summary.updatedAt());
        return out;
    }

    public static NewDataPlaneDefinition toNewDataPlaneDefinition(AutomationDataPlane definition, String id,
                                                                  String organizationId, String environmentId) {
        NewDataPlaneDefinition out = new NewDataPlaneDefinition();
        out.setId(id);
        out.setName(definition.getName());
        out.setType(definition.getType());
        out.setGatewayUrl(definition.getGatewayUrl());
        out.setConfiguration(definition.getConfiguration());
        out.setOrganizationId(organizationId);
        out.setEnvironmentId(environmentId);
        return out;
    }
}
