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
import io.gravitee.am.model.IdentityProvider;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderAuditBuilder extends ManagementAuditBuilder<IdentityProviderAuditBuilder> {

    public IdentityProviderAuditBuilder() {
        super();
    }

    public IdentityProviderAuditBuilder identityProvider(IdentityProvider identityProvider) {
        if (EventType.IDENTITY_PROVIDER_CREATED.equals(getType()) || EventType.IDENTITY_PROVIDER_UPDATED.equals(getType())) {
            setNewValue(identityProvider);
        }
        domain(identityProvider.getDomain());
        setTarget(identityProvider.getId(), EntityType.IDENTITY_PROVIDER, null, identityProvider.getName(), identityProvider.getDomain());
        return this;
    }
}
