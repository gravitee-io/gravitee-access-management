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
package io.gravitee.am.management.handlers.oauth2.provider.client;

import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.ClientService;
import io.gravitee.am.service.exception.ClientNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.ClientRegistrationException;
import org.springframework.security.oauth2.provider.NoSuchClientException;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainBasedClientDetailsService implements ClientDetailsService {

    @Autowired
    private ClientService clientService;

    @Autowired
    private Domain domain;

    @Override
    public org.springframework.security.oauth2.provider.ClientDetails loadClientByClientId(String clientId) throws ClientRegistrationException {
        try {
            Client client = clientService.findByDomainAndClientId(domain.getId(), clientId);

            if (client != null && client.isEnabled()) {
                return new DelegateClientDetails(client);
            }
        } catch (ClientNotFoundException ignored) {
        }

        throw new NoSuchClientException("No client with requested id: " + clientId);
    }
}
