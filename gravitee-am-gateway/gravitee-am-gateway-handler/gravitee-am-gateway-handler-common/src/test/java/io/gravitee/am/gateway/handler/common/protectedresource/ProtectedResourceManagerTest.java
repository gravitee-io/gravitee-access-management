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
package io.gravitee.am.gateway.handler.common.protectedresource;

import io.gravitee.am.common.event.ProtectedResourceEvent;
import io.gravitee.am.gateway.handler.common.protectedresource.impl.ProtectedResourceManagerImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.repository.management.api.ProtectedResourceRepository;
import io.gravitee.common.event.Event;
import io.reactivex.rxjava3.core.Flowable;
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
 * @author Stuart Clark (stuart.clark at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ProtectedResourceManagerTest {
    @InjectMocks
    private ProtectedResourceManagerImpl manager = new ProtectedResourceManagerImpl();

    @Mock
    private Domain domain;
    @Mock
    private Payload payload;
    @Mock
    private Event<ProtectedResourceEvent, Payload> event;
    @Mock
    private ProtectedResourceRepository repository;
    @Mock
    private GatewayMetricProvider gatewayMetricProvider;

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn("domain_id");
        when(domain.getName()).thenReturn("domain_name");

        when(payload.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(payload.getReferenceId()).thenReturn("domain_id");

        when(event.type()).thenReturn(ProtectedResourceEvent.UPDATE);
        when(event.content()).thenReturn(payload);

        ProtectedResource res1 = new ProtectedResource();
        res1.setId("resource1");
        manager.deploy(res1);

        ProtectedResource res2 = new ProtectedResource();
        res2.setId("resource2");
        manager.deploy(res2);
    }

    @Test
    public void shouldDeployNewProtectedResource() {
        ProtectedResource res = new ProtectedResource();
        res.setId("res_id");
        when(repository.findById("res_id")).thenReturn(Maybe.just(res));
        when(payload.getId()).thenReturn("res_id");

        manager.onEvent(event);
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(manager.entities().size()).isEqualTo(3));
    }

    @Test
    public void shouldLoadExistingProtectedResource() throws Exception {
        when(domain.isMaster()).thenReturn(true);

        manager.undeploy("resource1");
        manager.undeploy("resource2");

        ProtectedResource res = new ProtectedResource();
        res.setId("res_id");

        when(repository.findAll()).thenReturn(Flowable.just(res));

        manager.afterPropertiesSet();

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(manager.entities().size()).isEqualTo(1));
    }

    @Test
    public void shouldLoadProtectedResourcesForSpecificDomain() throws Exception {
        when(domain.isMaster()).thenReturn(false);
        when(domain.getId()).thenReturn("domain_id");

        manager.undeploy("resource1");
        manager.undeploy("resource2");

        ProtectedResource res1 = new ProtectedResource();
        res1.setId("res1");
        res1.setDomainId("domain_id");
        ProtectedResource res2 = new ProtectedResource();
        res2.setId("res2");
        res2.setDomainId("domain_id");

        when(repository.findByDomain("domain_id")).thenReturn(Flowable.just(res1, res2));

        manager.afterPropertiesSet();

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(manager.entities().size()).isEqualTo(2));
    }

    @Test
    public void shouldDeployProtectedResourceViaDeployEvent() {
        ProtectedResource res = new ProtectedResource();
        res.setId("deploy_res");
        when(repository.findById("deploy_res")).thenReturn(Maybe.just(res));
        when(payload.getId()).thenReturn("deploy_res");
        when(event.type()).thenReturn(ProtectedResourceEvent.DEPLOY);

        manager.onEvent(event);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(manager.entities().size()).isEqualTo(3));
    }

    @Test
    public void shouldUndeployProtectedResource() {
        when(payload.getId()).thenReturn("resource1");
        when(event.type()).thenReturn(ProtectedResourceEvent.UNDEPLOY);

        assertThat(manager.get("resource1")).isNotNull();

        manager.onEvent(event);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(manager.entities().size()).isEqualTo(1);
                    assertThat(manager.get("resource1")).isNull();
                });
    }

    @Test
    public void shouldIgnoreEventForDifferentDomain() {
        when(payload.getReferenceId()).thenReturn("other_domain");
        when(payload.getId()).thenReturn("new_res");
        when(domain.isMaster()).thenReturn(false);

        ProtectedResource res = new ProtectedResource();
        res.setId("new_res");

        int initialSize = manager.entities().size();
        manager.onEvent(event);

        await().pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(manager.entities().size()).isEqualTo(initialSize));
    }

    @Test
    public void shouldProcessEventForMasterDomain() {
        when(domain.isMaster()).thenReturn(true);
        when(payload.getId()).thenReturn("master_res");

        ProtectedResource res = new ProtectedResource();
        res.setId("master_res");
        when(repository.findById("master_res")).thenReturn(Maybe.just(res));

        manager.onEvent(event);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(manager.entities().size()).isEqualTo(3));
    }

    @Test
    public void shouldGetProtectedResourceById() {
        ProtectedResource res = manager.get("resource1");
        assertThat(res).isNotNull();
        assertThat(res.getId()).isEqualTo("resource1");
    }

    @Test
    public void shouldReturnNullForNonExistentResource() {
        ProtectedResource res = manager.get("non_existent");
        assertThat(res).isNull();
    }

    @Test
    public void shouldReturnNullForNullResourceId() {
        ProtectedResource res = manager.get(null);
        assertThat(res).isNull();
    }

    @Test
    public void shouldReturnAllEntities() {
        assertThat(manager.entities()).hasSize(2);
        assertThat(manager.entities()).extracting(ProtectedResource::getId)
                .containsExactlyInAnyOrder("resource1", "resource2");
    }

    @Test
    public void shouldManuallyDeployResource() {
        ProtectedResource res = new ProtectedResource();
        res.setId("manual_res");

        manager.deploy(res);

        assertThat(manager.entities().size()).isEqualTo(3);
        assertThat(manager.get("manual_res")).isNotNull();
    }

    @Test
    public void shouldManuallyUndeployResource() {
        assertThat(manager.get("resource1")).isNotNull();

        manager.undeploy("resource1");

        assertThat(manager.get("resource1")).isNull();
        assertThat(manager.entities().size()).isEqualTo(1);
    }

    @Test
    public void shouldHandleUndeployOfNonExistentResource() {
        int initialSize = manager.entities().size();

        manager.undeploy("non_existent");

        assertThat(manager.entities().size()).isEqualTo(initialSize);
    }

    @Test
    public void shouldUpdateExistingResource() {
        ProtectedResource updatedRes = new ProtectedResource();
        updatedRes.setId("resource1");
        updatedRes.setName("Updated Resource");

        when(repository.findById("resource1")).thenReturn(Maybe.just(updatedRes));
        when(payload.getId()).thenReturn("resource1");
        when(event.type()).thenReturn(ProtectedResourceEvent.UPDATE);

        manager.onEvent(event);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    ProtectedResource res = manager.get("resource1");
                    assertThat(res).isNotNull();
                    assertThat(res.getName()).isEqualTo("Updated Resource");
                });
    }

    @Test
    public void shouldHandleRepositoryErrorDuringDeploy() {
        when(repository.findById("error_res")).thenReturn(Maybe.error(new RuntimeException("Database error")));
        when(payload.getId()).thenReturn("error_res");

        int initialSize = manager.entities().size();
        manager.onEvent(event);

        await().pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(manager.entities().size()).isEqualTo(initialSize));
    }

    @Test
    public void shouldHandleEmptyRepositoryResponse() {
        when(repository.findById("empty_res")).thenReturn(Maybe.empty());
        when(payload.getId()).thenReturn("empty_res");

        int initialSize = manager.entities().size();
        manager.onEvent(event);

        await().pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(manager.entities().size()).isEqualTo(initialSize));
    }

    @Test
    public void shouldIgnoreEventForNonDomainReferenceType() {
        when(payload.getReferenceType()).thenReturn(ReferenceType.ORGANIZATION);
        when(payload.getId()).thenReturn("org_res");

        int initialSize = manager.entities().size();
        manager.onEvent(event);

        await().pollDelay(100, TimeUnit.MILLISECONDS)
                .atMost(1, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(manager.entities().size()).isEqualTo(initialSize));
    }
}
