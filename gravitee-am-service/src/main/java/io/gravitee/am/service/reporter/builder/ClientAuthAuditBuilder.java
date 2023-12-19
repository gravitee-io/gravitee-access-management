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
package io.gravitee.am.service.reporter.builder;

import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.Client;

public class ClientAuthAuditBuilder extends AuditBuilder<ClientAuthAuditBuilder> {
    public ClientAuthAuditBuilder() {
        super();
        type(EventType.CLIENT_AUTHENTICATION);
    }

    public ClientAuthAuditBuilder clientActor(Client client) {
        if (client != null) {
            var domainId = client.getDomain();
            setActor(client.getId(), EntityType.APPLICATION, client.getClientName(), client.getClientName(), ReferenceType.DOMAIN, domainId);
            super.client(client);
            super.domain(domainId);
        }
        return this;
    }
}
