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
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Reference;

import java.util.List;


public class ProtectedResourceAuditBuilder extends ManagementAuditBuilder<ProtectedResourceAuditBuilder> {

    public ProtectedResourceAuditBuilder() {
        super();
    }

    public ProtectedResourceAuditBuilder protectedResource(ProtectedResource resource) {
        if (resource != null) {
            if (EventType.PROTECTED_RESOURCE_CREATED.equals(getType()) || EventType.PROTECTED_RESOURCE_UPDATED.equals(getType())) {
                setNewValue(resource);
            }
            reference(Reference.domain(resource.getDomainId()));
            setTarget(resource.getId(), EntityType.PROTECTED_RESOURCE, null, resource.getName(), ReferenceType.DOMAIN, resource.getDomainId());
        }
        return this;
    }

    @Override
    protected Object removeSensitiveData(Object value) {
        if (value != null && value instanceof ProtectedResource protectedResource) {
            ProtectedResource safeValue = new ProtectedResource(protectedResource);
            safeValue.setSecretSettings(List.of());
            safeValue.setSecrets(List.of());
            return safeValue;
        }
        return value;
    }
}
