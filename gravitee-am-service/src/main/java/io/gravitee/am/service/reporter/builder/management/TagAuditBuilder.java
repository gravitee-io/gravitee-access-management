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
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Tag;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TagAuditBuilder extends ManagementAuditBuilder<TagAuditBuilder> {

    private static final String DOMAIN_ADMIN = "domain";

    public TagAuditBuilder() {
        // Tags are managed at organization level.
        referenceType(ReferenceType.ORGANIZATION);
    }

    public TagAuditBuilder tag(Tag tag) {
        if (EventType.TAG_CREATED.equals(getType()) || EventType.TAG_UPDATED.equals(getType())) {
            setNewValue(tag);
        }

        referenceId(tag.getOrganizationId());

        setTarget(tag.getId(), EntityType.TAG, null, tag.getName(), ReferenceType.ORGANIZATION, tag.getOrganizationId());
        return this;
    }
}
