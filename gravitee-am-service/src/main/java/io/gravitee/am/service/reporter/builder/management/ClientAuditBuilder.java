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
import io.gravitee.am.model.Client;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientAuditBuilder extends ManagementAuditBuilder<ClientAuditBuilder> {

    public ClientAuditBuilder() {
        super();
    }

    public ClientAuditBuilder client(Client client) {
        if (EventType.CLIENT_CREATED.equals(getType()) || EventType.CLIENT_UPDATED.equals(getType())) {
            setNewValue(client);
        }
        domain(client.getDomain());
        setTarget(client.getId(), EntityType.CLIENT, client.getClientId(), client.getClientName(), client.getDomain());
        return this;
    }
}
