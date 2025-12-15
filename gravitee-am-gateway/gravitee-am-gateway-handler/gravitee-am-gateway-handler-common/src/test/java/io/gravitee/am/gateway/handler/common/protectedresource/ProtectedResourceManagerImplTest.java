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
import io.gravitee.am.model.McpTool;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ProtectedResourceFeature;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.monitoring.DomainReadinessService;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.gravitee.am.common.event.Type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Stuart Clark (stuart.clark at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ProtectedResourceManagerImplTest {
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
    @Mock
    private DomainReadinessService domainReadinessService;

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
        res1.setDomainId("domain_id");
        manager.deploy(res1);

        ProtectedResource res2 = new ProtectedResource();
        res2.setId("resource2");
        res2.setDomainId("domain_id");
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
        verify(domainReadinessService).initPluginSync(eq("domain_id"), eq("res_id"), eq(Type.PROTECTED_RESOURCE.name()));
        verify(domainReadinessService).pluginLoaded(eq("domain_id"), eq("res_id"));
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
        
        verify(domainReadinessService).initPluginSync(eq("domain_id"), eq("res_id"), eq(Type.PROTECTED_RESOURCE.name()));
        verify(domainReadinessService).pluginLoaded(eq("domain_id"), eq("res_id"));
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
        
        verify(domainReadinessService).initPluginSync(eq("domain_id"), eq("deploy_res"), eq(Type.PROTECTED_RESOURCE.name()));
        verify(domainReadinessService).pluginLoaded(eq("domain_id"), eq("deploy_res"));
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
        verify(domainReadinessService).pluginUnloaded(eq("domain_id"), eq("resource1"));
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
        
        verify(domainReadinessService).initPluginSync(eq("domain_id"), eq("error_res"), eq(Type.PROTECTED_RESOURCE.name()));
        verify(domainReadinessService).pluginFailed(eq("domain_id"), eq("error_res"), eq("Database error"));
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

    @Test
    public void getScopesForResources_shouldReturnEmptySetWhenNoResourcesProvided() {
        Set<String> scopes = manager.getScopesForResources(Collections.emptySet());
        assertThat(scopes).isEmpty();
    }

    @Test
    public void getScopesForResources_shouldReturnEmptySetWhenResourcesAreNull() {
        Set<String> scopes = manager.getScopesForResources(null);
        assertThat(scopes).isEmpty();
    }

    @Test
    public void getScopesForResources_shouldReturnScopesForMatchingResource() {
        // Setup protected resource with matching resource identifiers
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId("domain_id");
        resource.setResourceIdentifiers(Arrays.asList("resource://api1", "resource://api2"));

        // Create MCP tool with scopes
        McpTool tool = new McpTool();
        tool.setType(ProtectedResourceFeature.Type.MCP_TOOL);
        tool.setScopes(Arrays.asList("scope1", "scope2", "scope3"));

        resource.setFeatures(Collections.singletonList(tool));

        manager.deploy(resource);

        // Request scopes for matching resource
        Set<String> requestedResources = new HashSet<>(Arrays.asList("resource://api1"));
        Set<String> scopes = manager.getScopesForResources(requestedResources);

        assertThat(scopes).containsExactlyInAnyOrder("scope1", "scope2", "scope3");
    }

    @Test
    public void getScopesForResources_shouldReturnScopesFromMultipleMatchingResources() {
        // Setup first protected resource
        ProtectedResource resource1 = new ProtectedResource();
        resource1.setId("resource1");
        resource1.setDomainId("domain_id");
        resource1.setResourceIdentifiers(Arrays.asList("resource://api1"));

        McpTool tool1 = new McpTool();
        tool1.setType(ProtectedResourceFeature.Type.MCP_TOOL);
        tool1.setScopes(Arrays.asList("scope1", "scope2"));
        resource1.setFeatures(Collections.singletonList(tool1));

        // Setup second protected resource
        ProtectedResource resource2 = new ProtectedResource();
        resource2.setId("resource2");
        resource2.setDomainId("domain_id");
        resource2.setResourceIdentifiers(Arrays.asList("resource://api2"));

        McpTool tool2 = new McpTool();
        tool2.setType(ProtectedResourceFeature.Type.MCP_TOOL);
        tool2.setScopes(Arrays.asList("scope3", "scope4"));
        resource2.setFeatures(Collections.singletonList(tool2));

        manager.deploy(resource1);
        manager.deploy(resource2);

        // Request scopes for both resources
        Set<String> requestedResources = new HashSet<>(Arrays.asList("resource://api1", "resource://api2"));
        Set<String> scopes = manager.getScopesForResources(requestedResources);

        assertThat(scopes).containsExactlyInAnyOrder("scope1", "scope2", "scope3", "scope4");
    }

    @Test
    public void getScopesForResources_shouldReturnEmptySetWhenNoMatchingResources() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId("domain_id");
        resource.setResourceIdentifiers(Arrays.asList("resource://api1"));

        McpTool tool = new McpTool();
        tool.setType(ProtectedResourceFeature.Type.MCP_TOOL);
        tool.setScopes(Arrays.asList("scope1", "scope2"));
        resource.setFeatures(Collections.singletonList(tool));

        manager.deploy(resource);

        // Request scopes for non-matching resource
        Set<String> requestedResources = new HashSet<>(Arrays.asList("resource://api99"));
        Set<String> scopes = manager.getScopesForResources(requestedResources);

        assertThat(scopes).isEmpty();
    }

    @Test
    public void getScopesForResources_shouldFilterByDomainId() {
        // Setup resource in correct domain
        ProtectedResource resource1 = new ProtectedResource();
        resource1.setId("resource1");
        resource1.setDomainId("domain_id");
        resource1.setResourceIdentifiers(Arrays.asList("resource://api1"));

        McpTool tool1 = new McpTool();
        tool1.setType(ProtectedResourceFeature.Type.MCP_TOOL);
        tool1.setScopes(Arrays.asList("scope1", "scope2"));
        resource1.setFeatures(Collections.singletonList(tool1));

        // Setup resource in different domain (should be filtered out)
        ProtectedResource resource2 = new ProtectedResource();
        resource2.setId("resource2");
        resource2.setDomainId("other_domain");
        resource2.setResourceIdentifiers(Arrays.asList("resource://api1"));

        McpTool tool2 = new McpTool();
        tool2.setType(ProtectedResourceFeature.Type.MCP_TOOL);
        tool2.setScopes(Arrays.asList("scope3", "scope4"));
        resource2.setFeatures(Collections.singletonList(tool2));

        manager.deploy(resource1);
        manager.deploy(resource2);

        // Request scopes - should only return scopes from domain_id
        Set<String> requestedResources = new HashSet<>(Arrays.asList("resource://api1"));
        Set<String> scopes = manager.getScopesForResources(requestedResources);

        assertThat(scopes).containsExactlyInAnyOrder("scope1", "scope2");
    }

    @Test
    public void getScopesForResources_shouldHandleResourceWithMultipleFeatures() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId("domain_id");
        resource.setResourceIdentifiers(Arrays.asList("resource://api1"));

        McpTool tool1 = new McpTool();
        tool1.setType(ProtectedResourceFeature.Type.MCP_TOOL);
        tool1.setScopes(Arrays.asList("scope1", "scope2"));

        McpTool tool2 = new McpTool();
        tool2.setType(ProtectedResourceFeature.Type.MCP_TOOL);
        tool2.setScopes(Arrays.asList("scope3", "scope4"));

        resource.setFeatures(Arrays.asList(tool1, tool2));

        manager.deploy(resource);

        Set<String> requestedResources = new HashSet<>(Arrays.asList("resource://api1"));
        Set<String> scopes = manager.getScopesForResources(requestedResources);

        assertThat(scopes).containsExactlyInAnyOrder("scope1", "scope2", "scope3", "scope4");
    }

    @Test
    public void getScopesForResources_shouldHandleNullFeatures() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId("domain_id");
        resource.setResourceIdentifiers(Arrays.asList("resource://api1"));
        resource.setFeatures(null);

        manager.deploy(resource);

        Set<String> requestedResources = new HashSet<>(Arrays.asList("resource://api1"));
        Set<String> scopes = manager.getScopesForResources(requestedResources);

        assertThat(scopes).isEmpty();
    }

    @Test
    public void getScopesForResources_shouldHandleEmptyFeatures() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId("domain_id");
        resource.setResourceIdentifiers(Arrays.asList("resource://api1"));
        resource.setFeatures(Collections.emptyList());

        manager.deploy(resource);

        Set<String> requestedResources = new HashSet<>(Arrays.asList("resource://api1"));
        Set<String> scopes = manager.getScopesForResources(requestedResources);

        assertThat(scopes).isEmpty();
    }

    @Test
    public void getScopesForResources_shouldHandleNullScopes() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId("domain_id");
        resource.setResourceIdentifiers(Arrays.asList("resource://api1"));

        McpTool tool = new McpTool();
        tool.setType(ProtectedResourceFeature.Type.MCP_TOOL);
        tool.setScopes(null);

        resource.setFeatures(Collections.singletonList(tool));

        manager.deploy(resource);

        Set<String> requestedResources = new HashSet<>(Arrays.asList("resource://api1"));
        Set<String> scopes = manager.getScopesForResources(requestedResources);

        assertThat(scopes).isEmpty();
    }

    @Test
    public void getScopesForResources_shouldHandleNullResourceIdentifiers() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId("domain_id");
        resource.setResourceIdentifiers(null);

        McpTool tool = new McpTool();
        tool.setType(ProtectedResourceFeature.Type.MCP_TOOL);
        tool.setScopes(Arrays.asList("scope1", "scope2"));

        resource.setFeatures(Collections.singletonList(tool));

        manager.deploy(resource);

        Set<String> requestedResources = new HashSet<>(Arrays.asList("resource://api1"));
        Set<String> scopes = manager.getScopesForResources(requestedResources);

        assertThat(scopes).isEmpty();
    }

    @Test
    public void getScopesForResources_shouldDeduplicateScopes() {
        ProtectedResource resource1 = new ProtectedResource();
        resource1.setId("resource1");
        resource1.setDomainId("domain_id");
        resource1.setResourceIdentifiers(Arrays.asList("resource://api1"));

        McpTool tool1 = new McpTool();
        tool1.setType(ProtectedResourceFeature.Type.MCP_TOOL);
        tool1.setScopes(Arrays.asList("scope1", "scope2"));
        resource1.setFeatures(Collections.singletonList(tool1));

        ProtectedResource resource2 = new ProtectedResource();
        resource2.setId("resource2");
        resource2.setDomainId("domain_id");
        resource2.setResourceIdentifiers(Arrays.asList("resource://api2"));

        McpTool tool2 = new McpTool();
        tool2.setType(ProtectedResourceFeature.Type.MCP_TOOL);
        tool2.setScopes(Arrays.asList("scope2", "scope3")); // scope2 is duplicate

        resource2.setFeatures(Collections.singletonList(tool2));

        manager.deploy(resource1);
        manager.deploy(resource2);

        Set<String> requestedResources = new HashSet<>(Arrays.asList("resource://api1", "resource://api2"));
        Set<String> scopes = manager.getScopesForResources(requestedResources);

        assertThat(scopes).containsExactlyInAnyOrder("scope1", "scope2", "scope3");
    }

    @Test
    public void getScopesForResources_shouldOnlyIncludeMcpToolFeatures() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId("domain_id");
        resource.setResourceIdentifiers(Arrays.asList("resource://api1"));

        // Create an MCP_TOOL feature
        McpTool mcpTool = new McpTool();
        mcpTool.setType(ProtectedResourceFeature.Type.MCP_TOOL);
        mcpTool.setScopes(Arrays.asList("scope1", "scope2"));

        // Create a non-MCP_TOOL feature (using base class)
        ProtectedResourceFeature otherFeature = new ProtectedResourceFeature();
        otherFeature.setType(null); // Different type

        resource.setFeatures(Arrays.asList(mcpTool, otherFeature));

        manager.deploy(resource);

        Set<String> requestedResources = new HashSet<>(Arrays.asList("resource://api1"));
        Set<String> scopes = manager.getScopesForResources(requestedResources);

        // Should only return scopes from MCP_TOOL feature
        assertThat(scopes).containsExactlyInAnyOrder("scope1", "scope2");
    }

    @Test
    public void getScopesForResources_shouldMatchPartialResourceIdentifiers() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId("domain_id");
        resource.setResourceIdentifiers(Arrays.asList("resource://api1", "resource://api2", "resource://api3"));

        McpTool tool = new McpTool();
        tool.setType(ProtectedResourceFeature.Type.MCP_TOOL);
        tool.setScopes(Arrays.asList("scope1", "scope2"));
        resource.setFeatures(Collections.singletonList(tool));

        manager.deploy(resource);

        // Request with only one matching identifier
        Set<String> requestedResources = new HashSet<>(Arrays.asList("resource://api2", "resource://non-existent"));
        Set<String> scopes = manager.getScopesForResources(requestedResources);

        assertThat(scopes).containsExactlyInAnyOrder("scope1", "scope2");
    }

    @Test
    public void getByIdentifier_shouldReturnEmptySetWhenIdentifierIsNull() {
        Set<ProtectedResource> resources = manager.getByIdentifier(null);
        assertThat(resources).isEmpty();
    }

    @Test
    public void getByIdentifier_shouldReturnMatchingResource() {
        // Setup protected resource with matching resource identifiers
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId("domain_id");
        resource.setResourceIdentifiers(Arrays.asList("resource://api1", "resource://api2"));

        manager.deploy(resource);

        // Request resources for matching identifier
        Set<ProtectedResource> result = manager.getByIdentifier("resource://api1");

        assertThat(result).hasSize(1);
        assertThat(result).extracting(ProtectedResource::getId).containsExactly("resource1");
    }

    @Test
    public void getByIdentifier_shouldReturnMultipleResourcesMatchingSameIdentifier() {
        // Setup first protected resource
        ProtectedResource resource1 = new ProtectedResource();
        resource1.setId("resource1");
        resource1.setDomainId("domain_id");
        resource1.setResourceIdentifiers(Arrays.asList("resource://api1", "resource://api2"));

        // Setup second protected resource (also has resource://api1)
        ProtectedResource resource2 = new ProtectedResource();
        resource2.setId("resource2");
        resource2.setDomainId("domain_id");
        resource2.setResourceIdentifiers(Arrays.asList("resource://api1", "resource://api3"));

        manager.deploy(resource1);
        manager.deploy(resource2);

        // Request resources for identifier that matches both
        Set<ProtectedResource> result = manager.getByIdentifier("resource://api1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProtectedResource::getId)
                .containsExactlyInAnyOrder("resource1", "resource2");
    }

    @Test
    public void getByIdentifier_shouldReturnEmptySetWhenNoMatchingResources() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId("domain_id");
        resource.setResourceIdentifiers(Arrays.asList("resource://api1"));

        manager.deploy(resource);

        // Request resources for non-matching identifier
        Set<ProtectedResource> result = manager.getByIdentifier("resource://api99");

        assertThat(result).isEmpty();
    }

    @Test
    public void getByIdentifier_shouldHandleResourceWithNullResourceIdentifiers() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId("domain_id");
        resource.setResourceIdentifiers(null);

        manager.deploy(resource);

        Set<ProtectedResource> result = manager.getByIdentifier("resource://api1");

        assertThat(result).isEmpty();
    }

    @Test
    public void getByIdentifier_shouldMatchResourceWithMultipleIdentifiers() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId("domain_id");
        resource.setResourceIdentifiers(Arrays.asList("resource://api1", "resource://api2", "resource://api3"));

        manager.deploy(resource);

        // Request with one of the identifiers
        Set<ProtectedResource> result = manager.getByIdentifier("resource://api2");

        assertThat(result).hasSize(1);
        assertThat(result).extracting(ProtectedResource::getId).containsExactly("resource1");
    }

    @Test
    public void getByIdentifier_shouldFilterByDomainId() {
        // Setup resource in correct domain
        ProtectedResource resource1 = new ProtectedResource();
        resource1.setId("resource1");
        resource1.setDomainId("domain_id");
        resource1.setResourceIdentifiers(Arrays.asList("resource://api1"));

        // Setup resource in different domain (should be filtered out)
        ProtectedResource resource2 = new ProtectedResource();
        resource2.setId("resource2");
        resource2.setDomainId("other_domain");
        resource2.setResourceIdentifiers(Arrays.asList("resource://api1"));

        manager.deploy(resource1);
        manager.deploy(resource2);

        // Request resources - should only return resource from domain_id
        Set<ProtectedResource> result = manager.getByIdentifier("resource://api1");

        assertThat(result).hasSize(1);
        assertThat(result).extracting(ProtectedResource::getId).containsExactly("resource1");
    }

    @Test
    public void getByIdentifier_shouldHandleEmptyResourceIdentifiers() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId("domain_id");
        resource.setResourceIdentifiers(Collections.emptyList());

        manager.deploy(resource);

        Set<ProtectedResource> result = manager.getByIdentifier("resource://api1");

        assertThat(result).isEmpty();
    }

    @Test
    public void getByIdentifier_shouldHandleResourceWithNullDomainId() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("resource1");
        resource.setDomainId(null);
        resource.setResourceIdentifiers(Arrays.asList("resource://api1"));

        manager.deploy(resource);

        // Should be filtered out because domainId doesn't match
        Set<ProtectedResource> result = manager.getByIdentifier("resource://api1");

        assertThat(result).isEmpty();
    }
}
