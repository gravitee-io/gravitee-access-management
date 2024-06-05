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
import io.gravitee.am.model.Environment;
import io.gravitee.am.model.ReferenceType;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EnvironmentAuditBuilder extends ManagementAuditBuilder<EnvironmentAuditBuilder> {

    public EnvironmentAuditBuilder() {
        super();
    }

    public EnvironmentAuditBuilder environment(Environment environment) {
        if (environment != null) {
            if (EventType.ENVIRONMENT_CREATED.equals(getType()) || EventType.ENVIRONMENT_UPDATED.equals(getType())) {
                setNewValue(environment);
            }

            referenceType(ReferenceType.ORGANIZATION);
            referenceId(environment.getOrganizationId());

            setTarget(environment.getId(), EntityType.ENVIRONMENT, null, environment.getName(), ReferenceType.ORGANIZATION, environment.getOrganizationId());
        }
        return this;
    }
}
