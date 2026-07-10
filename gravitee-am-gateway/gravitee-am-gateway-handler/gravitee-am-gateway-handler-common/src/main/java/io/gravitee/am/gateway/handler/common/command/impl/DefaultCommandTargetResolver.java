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
package io.gravitee.am.gateway.handler.common.command.impl;

import io.gravitee.am.gateway.handler.common.client.ClientManager;
import io.gravitee.am.gateway.handler.common.command.CommandTargetResolver;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Flowable;

/**
 * Preregistered targets: deployed clients of the domain having registered a
 * command_endpoint, excluding templates and, on a master domain, other domains'
 * clients.
 *
 * @author GraviteeSource Team
 */
public class DefaultCommandTargetResolver implements CommandTargetResolver {

    private final Domain domain;
    private final ClientManager clientManager;

    public DefaultCommandTargetResolver(Domain domain, ClientManager clientManager) {
        this.domain = domain;
        this.clientManager = clientManager;
    }

    @Override
    public Flowable<Client> resolveTargets() {
        return Flowable.fromIterable(clientManager.entities())
                .filter(client -> client.isEnabled() && !client.isTemplate() && domain.getId().equals(client.getDomain()))
                .filter(client -> client.getCommandEndpoint() != null && !client.getCommandEndpoint().isBlank());
    }
}
