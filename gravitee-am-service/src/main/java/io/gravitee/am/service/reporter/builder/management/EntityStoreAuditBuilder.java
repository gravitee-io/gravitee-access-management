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
import io.gravitee.am.model.EntityStore;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reference;

/**
 * @author GraviteeSource Team
 */
public class EntityStoreAuditBuilder extends ManagementAuditBuilder<EntityStoreAuditBuilder> {

    public EntityStoreAuditBuilder() {
        super();
    }

    public EntityStoreAuditBuilder entityStore(EntityStore entityStore) {
        if (entityStore != null) {
            if (EventType.ENTITY_STORE_CREATED.equals(getType()) || EventType.ENTITY_STORE_UPDATED.equals(getType())) {
                setNewValue(entityStore);
            }

            reference(new Reference(ReferenceType.DOMAIN, entityStore.getDomainId()));
            setTarget(entityStore.getId(), EntityType.ENTITY_STORE, null, entityStore.getName(),
                    ReferenceType.DOMAIN, entityStore.getDomainId());
        }
        return this;
    }
}
