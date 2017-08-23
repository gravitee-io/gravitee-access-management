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
package io.gravitee.am.gateway.handler.management.api.resources.enhancer;

import io.gravitee.am.gateway.service.model.TopClient;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.ClientListItem;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TopClientListItem;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ClientEnhancer {

    public Function<Client, ClientListItem> enhanceClient(Map<String,Domain> domains) {
        return client -> convert(client, domains, ClientListItem.class);
    }

    public Function<TopClient, TopClientListItem> enhanceTopClient(Map<String,Domain> domains) {
        return topClient -> {
            TopClientListItem topClientListItem = convert(topClient.getClient(), domains, TopClientListItem.class);
            topClientListItem.setAccessTokens(topClient.getAccessTokens());
            return topClientListItem;
        };
    }

    private <T extends ClientListItem> T convert(Client client, Map<String,Domain> domains, Class<T> clazz) {
        try {
            T clientListItem = clazz.newInstance();
            clientListItem.setId(client.getId());
            clientListItem.setClientId(client.getClientId());
            if (domains.get(client.getDomain()) != null) {
                clientListItem.setDomainId(domains.get(client.getDomain()).getId());
                clientListItem.setDomainName(domains.get(client.getDomain()).getName());
            } else {
                clientListItem.setDomainId("unkown-domain");
                clientListItem.setDomainName("Unknown domain");
            }
            clientListItem.setEnabled(client.isEnabled());
            clientListItem.setCreatedAt(client.getCreatedAt());
            clientListItem.setUpdatedAt(client.getUpdatedAt());

            return clientListItem;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert client to client list item", e);
        }
    }
}
