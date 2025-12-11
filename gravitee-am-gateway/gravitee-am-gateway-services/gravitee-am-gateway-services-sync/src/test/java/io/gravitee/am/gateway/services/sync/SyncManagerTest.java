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
import io.gravitee.am.gateway.reactor.impl.DefaultReactor;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.repository.Scope;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.EventRepository;
import io.gravitee.common.event.EventManager;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static io.gravitee.am.dataplane.api.DataPlaneDescription.DEFAULT_DATA_PLANE_ID;
import static io.gravitee.node.api.Node.META_ENVIRONMENTS;
import static io.gravitee.node.api.Node.META_ORGANIZATIONS;
import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class SyncManagerTest {

    private static final String SHARDING_TAGS_SYSTEM_PROPERTY = "tags";
    private static final String ORGANIZATIONS_SYSTEM_PROPERTY = "organizations";

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
    private Node node;
    @Mock
    private GatewayMetricProvider gatewayMetricProvider;

    @Mock
    private DefaultReactor defaultReactor;

    @Mock
    private DomainReadinessService domainReadinessService;

    @Before
    public void before() throws Exception {
        lenient().when(node.metadata()).thenReturn(Map.of(META_ORGANIZATIONS, new HashSet<>(), META_ENVIRONMENTS, new HashSet<>()));
        lenient().when(environment.getProperty(Scope.GATEWAY.getRepositoryPropertyKey()+".dataPlane.id", String.class, DEFAULT_DATA_PLANE_ID)).thenReturn(DEFAULT_DATA_PLANE_ID);
        syncManager.afterPropertiesSet();
        when(defaultReactor.isStarted()).thenReturn(true);
    }

    @Test
    public void init_test_ignore_sync_if_reactor_not_ready() {
        reset(defaultReactor);
        when(defaultReactor.isStarted()).thenReturn(false);

        syncManager.refresh();

        verify(domainRepository, never()).findAll();
        verify(securityDomainManager, never()).deploy(any(Domain.class));
        verify(securityDomainManager, never()).update(any(Domain.class));
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void init_test_empty_domains() {
        when(domainRepository.findAll()).thenReturn(Flowable.empty());

        syncManager.refresh();

        verify(securityDomainManager, never()).deploy(any(Domain.class));
        verify(securityDomainManager, never()).update(any(Domain.class));
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void init_test_one_domain() {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        when(domainRepository.findAll()).thenReturn(Flowable.just(domain));

        syncManager.refresh();

        verify(securityDomainManager, times(1)).deploy(any(Domain.class));
        verify(securityDomainManager, never()).update(any(Domain.class));
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void init_test_two_domains() {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        final Domain domain2 = new Domain();
        domain2.setId("domain-2");
        domain2.setReferenceId("env-2");
        domain2.setEnabled(true);
        domain2.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        when(domainRepository.findAll()).thenReturn(Flowable.just(domain, domain2));

        syncManager.refresh();

        verify(securityDomainManager, times(2)).deploy(any(Domain.class));
        verify(securityDomainManager, never()).update(any(Domain.class));
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void init_test_multiple_domains_oneDisabled() {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        final Domain domain2 = new Domain();
        domain2.setId("domain-2");
        domain2.setReferenceId("env-2");
        domain2.setEnabled(true);
        domain2.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        final Domain domain3 = new Domain();
        domain3.setId("domain-3");
        domain3.setReferenceId("env-3");
        domain3.setEnabled(false);
        domain3.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        when(domainRepository.findAll()).thenReturn(Flowable.just(domain, domain2, domain3));

        syncManager.refresh();

        verify(securityDomainManager, times(2)).deploy(any(Domain.class));
        verify(securityDomainManager, never()).update(any(Domain.class));
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void init_test_multiple_domains_oneAnotherDataPlane() {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        final Domain domain2 = new Domain();
        domain2.setId("domain-2");
        domain2.setReferenceId("env-2");
        domain2.setEnabled(true);
        domain2.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        final Domain domain3 = new Domain();
        domain3.setId("domain-3");
        domain3.setReferenceId("env-3");
        domain3.setEnabled(true);
        domain3.setDataPlaneId(UUID.randomUUID().toString());
        when(domainRepository.findAll()).thenReturn(Flowable.just(domain, domain2, domain3));

        syncManager.refresh();

        verify(securityDomainManager, times(2)).deploy(any(Domain.class));
        verify(securityDomainManager, never()).update(any(Domain.class));
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void test_twiceWithTwoDomains_domainToRemove() throws Exception {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        when(domainRepository.findAll()).thenReturn(Flowable.just(domain));

        syncManager.refresh();

        Event event = new Event();
        event.setType(Type.DOMAIN);
        event.setPayload(new Payload("domain-1", ReferenceType.DOMAIN, "domain-1", Action.DELETE));

        when(eventRepository.findByTimeFrameAndDataPlaneId(any(Long.class), any(Long.class), anyString())).thenReturn(Flowable.just(event));

        syncManager.refresh();

        verify(securityDomainManager, times(1)).deploy(any());
        verify(securityDomainManager, never()).update(any());
        verify(securityDomainManager, times(1)).undeploy(domain.getId());
    }

    @Test
    public void test_twiceWithTwoDomains_domainToUpdate() throws Exception {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setUpdatedAt(new Date(System.currentTimeMillis() - 60 * 1000));
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        when(domainRepository.findAll()).thenReturn(Flowable.just(domain));

        syncManager.refresh();

        Event event = new Event();
        event.setType(Type.DOMAIN);
        event.setPayload(new Payload("domain-1", ReferenceType.DOMAIN, "domain-1", Action.UPDATE));

        final Domain domainToUpdate = new Domain();
        domainToUpdate.setId("domain-1");
        domainToUpdate.setReferenceId("env-1");
        domainToUpdate.setEnabled(true);
        domainToUpdate.setUpdatedAt(new Date());
        domainToUpdate.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        when(eventRepository.findByTimeFrameAndDataPlaneId(any(Long.class), any(Long.class), anyString())).thenReturn(Flowable.just(event));
        when(domainRepository.findById(domainToUpdate.getId())).thenReturn(Maybe.just(domainToUpdate));
        when(securityDomainManager.get(domainToUpdate.getId())).thenReturn(domain);

        syncManager.refresh();

        verify(securityDomainManager, times(1)).deploy(any());
        verify(securityDomainManager, times(1)).update(any());
        verify(securityDomainManager, never()).undeploy(domain.getId());
    }

    @Test
    public void shouldPropagateEventsWithoutDuplication() {
        when(domainRepository.findAll()).thenReturn(Flowable.empty());
        syncManager.refresh();

        Event event = new Event();
        event.setId(UUID.randomUUID().toString());
        event.setType(Type.IDENTITY_PROVIDER);
        event.setCreatedAt(new Date());
        event.setPayload(new Payload("idp-1", ReferenceType.DOMAIN, "domain-1", Action.UPDATE));

        when(eventRepository.findByTimeFrameAndDataPlaneId(any(Long.class), any(Long.class),anyString())).thenReturn(Flowable.just(event, event));

        syncManager.refresh();

        verify(eventManager, times(1)).publishEvent(any(), any());
        verify(securityDomainManager, never()).deploy(any(Domain.class));
        verify(securityDomainManager, never()).update(any(Domain.class));
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void shouldSyncEventsOnlyForDataPlane(){
        when(domainRepository.findAll()).thenReturn(Flowable.empty());
        syncManager.refresh();

        Event event = new Event();
        event.setId(UUID.randomUUID().toString());
        event.setType(Type.IDENTITY_PROVIDER);
        event.setCreatedAt(new Date());
        event.setPayload(new Payload("idp-1", ReferenceType.DOMAIN, "domain-1", Action.UPDATE));
        event.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        when(eventRepository.findByTimeFrameAndDataPlaneId(any(Long.class), any(Long.class), anyString())).thenReturn(Flowable.just(event));

        syncManager.refresh();

        verify(eventManager, times(1)).publishEvent(any(), any());
        verify(securityDomainManager, never()).deploy(any(Domain.class));
        verify(securityDomainManager, never()).update(any(Domain.class));
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void shouldNotSyncEventsThatDataPlaneIdNotMatch(){
        when(domainRepository.findAll()).thenReturn(Flowable.empty());
        syncManager.refresh();

        when(eventRepository.findByTimeFrameAndDataPlaneId(any(Long.class), any(Long.class), anyString())).thenReturn(Flowable.empty());

        syncManager.refresh();

        verify(eventManager, never()).publishEvent(any(), any());
        verify(securityDomainManager, never()).deploy(any(Domain.class));
        verify(securityDomainManager, never()).update(any(Domain.class));
        verify(securityDomainManager, never()).undeploy(any(String.class));
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

    @Test
    public void init_test_one_env_one_domain() throws Exception {
        io.gravitee.am.model.Environment env = new io.gravitee.am.model.Environment();
        env.setId("env-1");
        env.setHrids(asList("dev"));

        Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        when(node.metadata()).thenReturn(Map.of(META_ENVIRONMENTS, new HashSet<>(asList("env-1"))));
        when(domainRepository.findAll()).thenReturn(Flowable.just(domain));

        syncManager.afterPropertiesSet();
        syncManager.refresh();

        verify(securityDomainManager).deploy(any());
        verify(securityDomainManager, never()).update(any());
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void init_test_one_env_two_domains() throws Exception {
        io.gravitee.am.model.Environment env = new io.gravitee.am.model.Environment();
        env.setId("env-1");
        env.setHrids(asList("dev"));

        Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        Domain domain2 = new Domain();
        domain2.setId("domain-2");
        domain2.setReferenceId("env-2");
        domain2.setEnabled(true);
        domain2.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        when(node.metadata()).thenReturn(Map.of(META_ENVIRONMENTS, new HashSet<>(asList("env-1"))));
        when(domainRepository.findAll()).thenReturn(Flowable.just(domain, domain2));

        syncManager.afterPropertiesSet();
        syncManager.refresh();

        verify(securityDomainManager).deploy(any());
        verify(securityDomainManager, never()).update(any());
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void init_test_two_env() throws Exception {
        io.gravitee.am.model.Environment env = new io.gravitee.am.model.Environment();
        env.setId("env-1");
        env.setHrids(asList("dev"));
        io.gravitee.am.model.Environment env2 = new io.gravitee.am.model.Environment();
        env2.setId("env-2");
        env2.setHrids(asList("prod"));

        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        final Domain domain2 = new Domain();
        domain2.setId("domain-2");
        domain2.setReferenceId("env-2");
        domain2.setEnabled(true);
        domain2.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        when(node.metadata()).thenReturn(Map.of(META_ENVIRONMENTS, new HashSet<>(asList("env-1", "env-2"))));
        when(domainRepository.findAll()).thenReturn(Flowable.just(domain, domain2));

        syncManager.afterPropertiesSet();
        syncManager.refresh();

        verify(securityDomainManager, times(2)).deploy(any());
        verify(securityDomainManager, never()).update(any());
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void init_test_two_env_only_one_deployed() throws Exception {
        io.gravitee.am.model.Environment env = new io.gravitee.am.model.Environment();
        env.setId("env-1");
        env.setHrids(asList("dev"));
        io.gravitee.am.model.Environment env2 = new io.gravitee.am.model.Environment();
        env2.setId("env-2");
        env2.setHrids(asList("prod"));

        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        final Domain domain2 = new Domain();
        domain2.setId("domain-2");
        domain2.setReferenceId("env-2");
        domain2.setEnabled(true);
        domain2.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        when(node.metadata()).thenReturn(Map.of(META_ENVIRONMENTS, new HashSet<>(asList("env-1"))));
        when(domainRepository.findAll()).thenReturn(Flowable.just(domain, domain2));

        syncManager.afterPropertiesSet();
        syncManager.refresh();

        verify(securityDomainManager).deploy(any());
        verify(securityDomainManager, never()).update(any());
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void init_test_one_org() throws Exception {
        Organization organization = new Organization();
        organization.setId("org-1");
        organization.setHrids(asList("gravitee"));

        io.gravitee.am.model.Environment env = new io.gravitee.am.model.Environment();
        env.setId("env-1");
        env.setOrganizationId("org-1");
        io.gravitee.am.model.Environment env2 = new io.gravitee.am.model.Environment();
        env2.setId("env-2");
        env2.setOrganizationId("org-1");

        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        final Domain domain2 = new Domain();
        domain2.setId("domain-2");
        domain2.setReferenceId("env-2");
        domain2.setEnabled(true);
        domain2.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        when(node.metadata()).thenReturn(Map.of(META_ORGANIZATIONS, new HashSet<>(asList("org-1")),
                META_ENVIRONMENTS, new HashSet<>(asList("env-1", "env-2"))));

        when(environment.getProperty(ORGANIZATIONS_SYSTEM_PROPERTY)).thenReturn("gravitee");
        when(domainRepository.findAll()).thenReturn(Flowable.just(domain, domain2));

        syncManager.afterPropertiesSet();
        syncManager.refresh();

        verify(securityDomainManager, times(2)).deploy(any());
        verify(securityDomainManager, never()).update(any());
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }


    @Test
    public void init_test_two_org() throws Exception {
        Organization organization = new Organization();
        organization.setId("org-1");
        organization.setHrids(asList("gravitee"));

        Organization organization2 = new Organization();
        organization2.setId("org-2");
        organization2.setHrids(asList("gravitee2"));

        io.gravitee.am.model.Environment env = new io.gravitee.am.model.Environment();
        env.setId("env-1");
        env.setOrganizationId("org-1");
        io.gravitee.am.model.Environment env2 = new io.gravitee.am.model.Environment();
        env2.setId("env-2");
        env2.setOrganizationId("org-1");

        io.gravitee.am.model.Environment env3 = new io.gravitee.am.model.Environment();
        env3.setId("env-3");
        env3.setOrganizationId("org-2");
        io.gravitee.am.model.Environment env4 = new io.gravitee.am.model.Environment();
        env4.setId("env-4");
        env4.setOrganizationId("org-2");

        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        final Domain domain2 = new Domain();
        domain2.setId("domain-2");
        domain2.setReferenceId("env-2");
        domain2.setEnabled(true);
        domain2.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        final Domain domain3 = new Domain();
        domain3.setId("domain-3");
        domain3.setReferenceId("env-3");
        domain3.setEnabled(true);
        domain3.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        final Domain domain4 = new Domain();
        domain4.setId("domain-4");
        domain4.setReferenceId("env-4");
        domain4.setEnabled(true);
        domain4.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        when(node.metadata()).thenReturn(Map.of(META_ORGANIZATIONS, new HashSet<>(asList("org-1", "org-2")),
                META_ENVIRONMENTS, new HashSet<>(asList("env-1", "env-2", "env-3", "env-4"))));

        when(environment.getProperty(ORGANIZATIONS_SYSTEM_PROPERTY)).thenReturn("gravitee,gravitee2");
        when(domainRepository.findAll()).thenReturn(Flowable.just(domain, domain2, domain3, domain4));

        syncManager.afterPropertiesSet();
        syncManager.refresh();

        verify(securityDomainManager, times(4)).deploy(any());
        verify(securityDomainManager, never()).update(any());
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void init_test_two_org_and_env() throws Exception {
        Organization organization = new Organization();
        organization.setId("org-1");
        organization.setHrids(asList("gravitee"));

        Organization organization2 = new Organization();
        organization2.setId("org-2");
        organization2.setHrids(asList("gravitee2"));

        io.gravitee.am.model.Environment env = new io.gravitee.am.model.Environment();
        env.setId("env-1");
        env.setHrids(asList("dev"));
        env.setOrganizationId("org-1");
        io.gravitee.am.model.Environment env2 = new io.gravitee.am.model.Environment();
        env2.setId("env-2");
        env2.setHrids(asList("prod"));
        env2.setOrganizationId("org-1");

        io.gravitee.am.model.Environment env3 = new io.gravitee.am.model.Environment();
        env3.setId("env-3");
        env3.setHrids(asList("prod"));
        env3.setOrganizationId("org-2");
        io.gravitee.am.model.Environment env4 = new io.gravitee.am.model.Environment();
        env4.setId("env-4");
        env4.setHrids(asList("dev"));
        env4.setOrganizationId("org-2");

        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        final Domain domain2 = new Domain();
        domain2.setId("domain-2");
        domain2.setReferenceId("env-2");
        domain2.setEnabled(true);
        domain2.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        final Domain domain3 = new Domain();
        domain3.setId("domain-3");
        domain3.setReferenceId("env-3");
        domain3.setEnabled(true);
        domain3.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        final Domain domain4 = new Domain();
        domain4.setId("domain-4");
        domain4.setReferenceId("env-4");
        domain4.setEnabled(true);
        domain4.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        when(node.metadata()).thenReturn(Map.of(META_ORGANIZATIONS, new HashSet<>(asList("org-1", "org-2")),
                META_ENVIRONMENTS, new HashSet<>(asList("env-1", "env-4"))));
        when(domainRepository.findAll()).thenReturn(Flowable.just(domain, domain2, domain3, domain4));

        syncManager.afterPropertiesSet();
        syncManager.refresh();

        verify(securityDomainManager, times(2)).deploy(any());
        verify(securityDomainManager, never()).update(any());
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    @Test
    public void init_test_two_org_and_env_and_sharding_tags() throws Exception {
        Organization organization = new Organization();
        organization.setId("org-1");
        organization.setHrids(asList("gravitee"));

        Organization organization2 = new Organization();
        organization2.setId("org-2");
        organization2.setHrids(asList("gravitee2"));

        io.gravitee.am.model.Environment env = new io.gravitee.am.model.Environment();
        env.setId("env-1");
        env.setHrids(asList("dev"));
        env.setOrganizationId("org-1");
        io.gravitee.am.model.Environment env2 = new io.gravitee.am.model.Environment();
        env2.setId("env-2");
        env2.setHrids(asList("prod"));
        env2.setOrganizationId("org-1");

        io.gravitee.am.model.Environment env3 = new io.gravitee.am.model.Environment();
        env3.setId("env-3");
        env3.setHrids(asList("prod"));
        env3.setOrganizationId("org-2");
        io.gravitee.am.model.Environment env4 = new io.gravitee.am.model.Environment();
        env4.setId("env-4");
        env4.setHrids(asList("dev"));
        env4.setOrganizationId("org-2");

        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        final Domain domain2 = new Domain();
        domain2.setId("domain-2");
        domain2.setReferenceId("env-2");
        domain2.setEnabled(true);
        domain2.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        final Domain domain3 = new Domain();
        domain3.setId("domain-3");
        domain3.setReferenceId("env-3");
        domain3.setEnabled(true);
        domain3.setDataPlaneId(DEFAULT_DATA_PLANE_ID);
        final Domain domain4 = new Domain();
        domain4.setId("domain-4");
        domain4.setTags(Collections.singleton("private"));
        domain4.setReferenceId("env-4");
        domain4.setEnabled(true);
        domain4.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        when(node.metadata()).thenReturn(Map.of(META_ORGANIZATIONS, new HashSet<>(asList("org-1", "org-2")),
                META_ENVIRONMENTS, new HashSet<>(asList("env-1", "env-2", "env-3", "env-4"))));
        when(environment.getProperty(SHARDING_TAGS_SYSTEM_PROPERTY)).thenReturn("private");
        when(domainRepository.findAll()).thenReturn(Flowable.just(domain, domain2, domain3, domain4));

        syncManager.afterPropertiesSet();
        syncManager.refresh();

        verify(securityDomainManager, times(1)).deploy(any());
        verify(securityDomainManager, never()).update(any());
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }

    private void shouldDeployDomainWithTags(final String tags, final String[] domainTags) throws Exception {
        when(environment.getProperty(SHARDING_TAGS_SYSTEM_PROPERTY)).thenReturn(tags);
        syncManager.afterPropertiesSet();

        Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setReferenceId("env-1");
        domain.setEnabled(true);
        domain.setTags(new HashSet<>(asList(domainTags)));
        domain.setDataPlaneId(DEFAULT_DATA_PLANE_ID);

        when(domainRepository.findAll()).thenReturn(Flowable.just(domain));

        syncManager.refresh();

        verify(securityDomainManager).deploy(any());
        verify(securityDomainManager, never()).update(any());
        verify(securityDomainManager, never()).undeploy(any(String.class));
    }
}
