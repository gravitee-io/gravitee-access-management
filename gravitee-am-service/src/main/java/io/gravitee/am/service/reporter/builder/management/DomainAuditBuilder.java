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
import io.gravitee.am.model.Domain;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainAuditBuilder extends ManagementAuditBuilder<DomainAuditBuilder> {

    /**
     * Domain create events are triggered by the admin domain
     */
    private static final String ADMIN_DOMAIN = "admin";

    public DomainAuditBuilder domain(Domain domain) {
        //  Domain create and delete events are triggered by the admin domain
        if (EventType.DOMAIN_CREATED.equals(getType()) || EventType.DOMAIN_DELETED.equals(getType())) {
            domain(ADMIN_DOMAIN);
        } else {
            domain(domain.getId());
        }

        if (EventType.DOMAIN_CREATED.equals(getType()) || EventType.DOMAIN_UPDATED.equals(getType())) {
            setNewValue(domain);
        }
        setTarget(domain.getId(), EntityType.DOMAIN, null, domain.getName(), domain.getId());
        return this;
    }
}
