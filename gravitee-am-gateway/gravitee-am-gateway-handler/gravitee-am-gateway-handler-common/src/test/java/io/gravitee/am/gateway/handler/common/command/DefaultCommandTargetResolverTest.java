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
package io.gravitee.am.gateway.handler.common.command;

import io.gravitee.am.gateway.handler.common.client.ClientManager;
import io.gravitee.am.gateway.handler.common.command.impl.DefaultCommandTargetResolver;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DefaultCommandTargetResolverTest {

    @Mock
    private ClientManager clientManager;

    private Domain domain;
    private DefaultCommandTargetResolver resolver;

    @BeforeEach
    public void setUp() {
        domain = new Domain("domain-id");
        domain.setName("domain-name");
        resolver = new DefaultCommandTargetResolver(domain, clientManager);
    }

    @Test
    public void onlyDeployedClientsWithACommandEndpointAreTargets() {
        Client optedIn = client("app-1", "client-1", "https://rp.example.com/commands");
        Client notOptedIn = client("app-2", "client-2", null);
        Client blankEndpoint = client("app-3", "client-3", " ");
        when(clientManager.entities()).thenReturn(List.of(optedIn, notOptedIn, blankEndpoint));

        resolver.resolveTargets().test()
                .assertResult(optedIn);
    }

    @Test
    public void templateDisabledAndForeignDomainClientsAreNotTargets() {
        Client template = client("template-app", "template-client", "https://template.example.com/commands");
        template.setTemplate(true);
        Client disabled = client("disabled-app", "disabled-client", "https://disabled.example.com/commands");
        disabled.setEnabled(false);
        // on a master domain the ClientManager also holds other domains' clients
        Client foreign = client("foreign-app", "foreign-client", "https://foreign.example.com/commands");
        foreign.setDomain("other-domain-id");
        when(clientManager.entities()).thenReturn(List.of(template, disabled, foreign));

        resolver.resolveTargets().test()
                .assertResult();
    }

    private Client client(String id, String clientId, String commandEndpoint) {
        Client client = new Client();
        client.setId(id);
        client.setClientId(clientId);
        client.setDomain(domain.getId());
        client.setEnabled(true);
        client.setCommandEndpoint(commandEndpoint);
        return client;
    }
}
