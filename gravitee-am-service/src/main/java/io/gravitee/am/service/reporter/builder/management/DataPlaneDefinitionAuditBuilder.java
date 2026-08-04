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
package io.gravitee.am.service.reporter.builder.management;

import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.model.DataPlaneDefinitionSummary;

/**
 * Data plane audits are recorded against the owning organization.
 *
 * @author GraviteeSource Team
 */
public class DataPlaneDefinitionAuditBuilder extends ManagementAuditBuilder<DataPlaneDefinitionAuditBuilder> {

    public DataPlaneDefinitionAuditBuilder() {
        super();
    }

    /**
     * Takes the summary, not the definition: the stored configuration holds connection credentials
     * and the audit is written to a reporter.
     */
    public DataPlaneDefinitionAuditBuilder dataPlane(DataPlaneDefinitionSummary dataPlane) {
        if (dataPlane != null) {
            setNewValue(dataPlane);

            reference(new Reference(ReferenceType.ORGANIZATION, dataPlane.organizationId()));
            setTarget(dataPlane.id(), EntityType.DATA_PLANE, null, dataPlane.name(), ReferenceType.ORGANIZATION, dataPlane.organizationId());
        }
        return this;
    }
}
