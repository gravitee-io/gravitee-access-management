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
package io.gravitee.am.gateway.services.sync;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.gateway.reactor.SecurityDomainManager;
import io.gravitee.am.gateway.reactor.impl.DefaultClientManager;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.ClientRepository;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.EventRepository;
import io.gravitee.common.event.EventManager;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class SyncManagerTest {

    private static final String SHARDING_TAGS_SYSTEM_PROPERTY = "tags";

    @InjectMocks
    private SyncManager syncManager = new SyncManager();

    @Mock
    private EventManager eventManager;

    @Mock
    private SecurityDomainManager securityDomainManager;

    @Mock
    private Environment environment;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private DefaultClientManager clientManager;

    @Before
    public void before() throws Exception {
        syncManager.afterPropertiesSet();
    }

    @Test
    public void init_test_empty_domains() {
        when(domainRepository.findAll()).thenReturn(Single.just(Collections.emptySet()));

        syncManager.refresh();

        verify(securityDomainManager, never()).deploy(any(Domain.class));
        verify(securityDomainManager, never()).update(any(Domain.class));
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void init_test_one_domain() {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setEnabled(true);
        when(domainRepository.findAll()).thenReturn(Single.just(Collections.singleton(domain)));

        syncManager.refresh();

        verify(securityDomainManager, times(1)).deploy(any(Domain.class));
        verify(securityDomainManager, never()).update(any(Domain.class));
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void init_test_two_domains() {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setEnabled(true);
        final Domain domain2 = new Domain();
        domain2.setId("domain-2");
        domain2.setEnabled(true);
        when(domainRepository.findAll()).thenReturn(Single.just(new HashSet<>(Arrays.asList(domain, domain2))));

        syncManager.refresh();

        verify(securityDomainManager, times(2)).deploy(any(Domain.class));
        verify(securityDomainManager, never()).update(any(Domain.class));
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void init_test_multiple_domains_oneDisabled() {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setEnabled(true);
        final Domain domain2 = new Domain();
        domain2.setId("domain-2");
        domain2.setEnabled(true);
        final Domain domain3 = new Domain();
        domain3.setId("domain-3");
        domain3.setEnabled(false);
        when(domainRepository.findAll()).thenReturn(Single.just(new HashSet<>(Arrays.asList(domain, domain2, domain3))));

        syncManager.refresh();

        verify(securityDomainManager, times(2)).deploy(any(Domain.class));
        verify(securityDomainManager, never()).update(any(Domain.class));
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_twiceWithTwoDomains_domainToRemove() throws Exception {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setEnabled(true);
        when(domainRepository.findAll()).thenReturn(Single.just(new HashSet<>(Arrays.asList(domain))));
        when(clientRepository.findAll()).thenReturn(Single.just(Collections.emptySet()));
        doNothing().when(clientManager).init(anyCollection());

        syncManager.refresh();

        Event event = new Event();
        event.setType(Type.DOMAIN);
        event.setPayload(new Payload("domain-1", "domain-1", Action.DELETE));

        when(eventRepository.findByTimeFrame(any(Long.class), any(Long.class))).thenReturn(Single.just(Collections.singletonList(event)));

        syncManager.refresh();

        verify(securityDomainManager, times(1)).deploy(any());
        verify(securityDomainManager, never()).update(any());
        verify(securityDomainManager, times(1)).undeploy(domain.getId());
    }

    @Test
    public void test_twiceWithTwoDomains_domainToUpdate() throws Exception {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setEnabled(true);
        domain.setUpdatedAt(new Date(System.currentTimeMillis() - 60 * 1000));
        when(domainRepository.findAll()).thenReturn(Single.just(new HashSet<>(Arrays.asList(domain))));
        when(clientRepository.findAll()).thenReturn(Single.just(Collections.emptySet()));
        doNothing().when(clientManager).init(anyCollection());

        syncManager.refresh();

        Event event = new Event();
        event.setType(Type.DOMAIN);
        event.setPayload(new Payload("domain-1", "domain-1", Action.UPDATE));

        final Domain domainToUpdate = new Domain();
        domainToUpdate.setId("domain-1");
        domainToUpdate.setEnabled(true);
        domainToUpdate.setUpdatedAt(new Date());

        when(eventRepository.findByTimeFrame(any(Long.class), any(Long.class))).thenReturn(Single.just(Collections.singletonList(event)));
        when(domainRepository.findById(domainToUpdate.getId())).thenReturn(Maybe.just(domainToUpdate));
        when(securityDomainManager.get(domainToUpdate.getId())).thenReturn(domain);

        syncManager.refresh();

        verify(securityDomainManager, times(1)).deploy(any());
        verify(securityDomainManager, times(1)).update(any());
        verify(securityDomainManager, never()).undeploy(domain.getId());
    }

    @Test
    public void shouldPropagateEvents() {
        when(domainRepository.findAll()).thenReturn(Single.just(Collections.emptySet()));
        when(clientRepository.findAll()).thenReturn(Single.just(Collections.emptySet()));
        doNothing().when(clientManager).init(anyCollection());
        syncManager.refresh();

        Event event = new Event();
        event.setType(Type.IDENTITY_PROVIDER);
        event.setPayload(new Payload("idp-1", "domain-1", Action.UPDATE));

        when(eventRepository.findByTimeFrame(any(Long.class), any(Long.class))).thenReturn(Single.just(Collections.singletonList(event)));

        syncManager.refresh();

        verify(eventManager, times(1)).publishEvent(any(), any());
        verify(securityDomainManager, never()).deploy(any(Domain.class));
        verify(securityDomainManager, never()).update(any(Domain.class));
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void shouldDeployClient() {
        when(domainRepository.findAll()).thenReturn(Single.just(Collections.emptySet()));
        when(clientRepository.findAll()).thenReturn(Single.just(Collections.emptySet()));
        when(clientRepository.findById("client-1")).thenReturn(Maybe.just(new Client()));
        doNothing().when(clientManager).init(anyCollection());
        syncManager.refresh();

        Event event = new Event();
        event.setType(Type.CLIENT);
        event.setPayload(new Payload("client-1", "domain-1", Action.CREATE));

        when(eventRepository.findByTimeFrame(any(Long.class), any(Long.class))).thenReturn(Single.just(Collections.singletonList(event)));

        syncManager.refresh();

        verify(clientManager, times(1)).deploy(any(Client.class));
        verify(clientManager, never()).update(any(Client.class));
        verify(clientManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_deployDomainWithTag() throws Exception {
        shouldDeployDomainWithTags("test,toto", new String[]{"test"});
    }

    @Test
    public void test_deployDomainWithUpperCasedTag() throws Exception {
        shouldDeployDomainWithTags("test,toto", new String[]{"Test"});
    }

    @Test
    public void test_deployDomainWithAccentTag() throws Exception {
        shouldDeployDomainWithTags("test,toto", new String[]{"tést"});
    }

    @Test
    public void test_deployDomainWithUpperCasedAndAccentTag() throws Exception {
        shouldDeployDomainWithTags("test", new String[]{"Tést"});
    }

    @Test
    public void test_deployDomainWithTagExclusion() throws Exception {
        shouldDeployDomainWithTags("test,!toto", new String[]{"test"});
    }

    @Test
    public void test_deployDomainWithSpaceAfterComma() throws Exception {
        shouldDeployDomainWithTags("test, !toto", new String[]{"test"});
    }

    @Test
    public void test_deployDomainWithSpaceBeforeComma() throws Exception {
        shouldDeployDomainWithTags("test ,!toto", new String[]{"test"});
    }

    @Test
    public void test_deployDomainWithSpaceBeforeTag() throws Exception {
        shouldDeployDomainWithTags(" test,!toto", new String[]{"test"});
    }

    public void shouldDeployDomainWithTags(final String tags, final String[] domainTags) throws Exception {
        when(environment.getProperty(SHARDING_TAGS_SYSTEM_PROPERTY)).thenReturn(tags);
        syncManager.afterPropertiesSet();

        Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setEnabled(true);
        domain.setTags(new HashSet<>(Arrays.asList(domainTags)));

        when(domainRepository.findAll()).thenReturn(Single.just(Collections.singleton(domain)));

        syncManager.refresh();

        verify(securityDomainManager).deploy(any());
        verify(securityDomainManager, never()).update(any());
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }
}
