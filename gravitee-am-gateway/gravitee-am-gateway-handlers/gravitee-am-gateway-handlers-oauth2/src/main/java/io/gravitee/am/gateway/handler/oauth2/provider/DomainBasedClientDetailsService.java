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
package io.gravitee.am.gateway.handler.oauth2.provider;

import io.gravitee.am.definition.Client;
import io.gravitee.am.definition.Domain;
import io.gravitee.am.definition.oauth2.GrantType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.ClientRegistrationException;
import org.springframework.security.oauth2.provider.NoSuchClientException;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainBasedClientDetailsService implements ClientDetailsService {

    private Map<String, ClientDetails> clientDetailsStore = new HashMap<>();

    public DomainBasedClientDetailsService(@Autowired Domain domain) {
        this.init(domain);
    }

    private void init(Domain domain) {
        for(Client client : domain.getClients()) {
            clientDetailsStore.put(client.getClientId(), convert(client));
        }
    }

    private ClientDetails convert(Client client) {
        BaseClientDetails details = new BaseClientDetails();

        details.setClientId(client.getClientId());
        details.setClientSecret(client.getClientSecret());
        details.setRegisteredRedirectUri(new HashSet<>(client.getRedirectUris()));
        details.setAccessTokenValiditySeconds(client.getAccessTokenValiditySeconds());
        details.setRefreshTokenValiditySeconds(client.getRefreshTokenValiditySeconds());
        details.setScope(client.getScopes());
        details.setAuthorizedGrantTypes(client.getAuthorizedGrantTypes().stream().map(GrantType::type).collect(Collectors.toList()));

        return details;
    }

    public ClientDetails loadClientByClientId(String clientId) throws ClientRegistrationException {
        ClientDetails details = clientDetailsStore.get(clientId);
        if (details == null) {
            throw new NoSuchClientException("No client with requested id: " + clientId);
        }
        return details;
    }
}
