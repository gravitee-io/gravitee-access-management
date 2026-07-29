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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.model.GraviteeLicense;

/**
 * Audits changes to the license attached to an organization.
 * <p>
 * The persisted license is a signed secret, so the audited value is always the decoded metadata
 * ({@link GraviteeLicense}: tier, packs, features, expiry) and never the raw base64 license.
 *
 * @author GraviteeSource Team
 */
public class LicenseAuditBuilder extends ManagementAuditBuilder<LicenseAuditBuilder> {

    public LicenseAuditBuilder organization(String organizationId) {
        if (organizationId != null) {
            reference(Reference.organization(organizationId));
            setTarget(organizationId, EntityType.LICENSE, null, null, ReferenceType.ORGANIZATION, organizationId);
        }
        return this;
    }

    public LicenseAuditBuilder license(GraviteeLicense license) {
        setNewValue(license);
        return this;
    }

    @Override
    public Audit build(ObjectMapper mapper) {
        Audit audit = super.build(mapper);
        if (isSuccess() && newValue == null && oldValue != null) {
            // record the previous license rather than the diff, which would not carry any information about the downgrade.
            audit.getOutcome().setMessage(mapper.convertValue(oldValue, ObjectNode.class).toString());
        }
        return audit;
    }
}
