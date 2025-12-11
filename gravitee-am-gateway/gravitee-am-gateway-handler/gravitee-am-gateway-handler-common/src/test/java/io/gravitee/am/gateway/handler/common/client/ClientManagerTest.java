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
package io.gravitee.am.gateway.handler.common.client;

import io.gravitee.am.common.event.ApplicationEvent;
import io.gravitee.am.gateway.handler.common.client.impl.ClientManagerImpl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.common.event.Event;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

/**
 * @author Rafal Podles (rafal.podles at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ClientManagerTest {
    @InjectMocks
    private ClientManagerImpl clientManager = new ClientManagerImpl();

    @Mock
    private Domain domain;
    @Mock
    private Payload payload;
    @Mock
    private Event<ApplicationEvent, Payload> event;
    @Mock
    private GatewayMetricProvider gatewayMetricProvider;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private DomainReadinessService domainReadinessService;

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn("domain_id");
        when(domain.getName()).thenReturn("domain_name");

        when(payload.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(payload.getReferenceId()).thenReturn("domain_id");

        when(event.type()).thenReturn(ApplicationEvent.UPDATE);
        when(event.content()).thenReturn(payload);

        Client c1 = new Client();
        c1.setId("client1");
        c1.setEnabled(true);
        clientManager.deploy(c1);
        Client c2 = new Client();
        c2.setId("client2");
        c2.setEnabled(true);
        clientManager.deploy(c2);
    }

    @Test
    public void shouldNotDeployDisabledClient() {
        Application application = new Application();
        application.setId("client_id");
        application.setEnabled(false);
        when(applicationRepository.findById("client_id")).thenReturn(Maybe.just(application));
        when(payload.getId()).thenReturn("client_id");
        clientManager.onEvent(event);
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(clientManager.entities().size()).isEqualTo(2));
    }

    @Test
    public void shouldDeployClientWhenEnable() {
        Application application = new Application();
        application.setId("client_id");
        application.setEnabled(true);
        when(applicationRepository.findById("client_id")).thenReturn(Maybe.just(application));
        when(payload.getId()).thenReturn("client_id");
        clientManager.onEvent(event);
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(clientManager.entities().size()).isEqualTo(3));
    }

    @Test
    public void shouldRemoveDisabledClient() {
        Application application = new Application();
        application.setId("client2");
        application.setEnabled(false);
        when(applicationRepository.findById("client2")).thenReturn(Maybe.just(application));
        when(payload.getId()).thenReturn("client2");
        clientManager.onEvent(event);
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(clientManager.entities().size()).isEqualTo(1));
    }
}
