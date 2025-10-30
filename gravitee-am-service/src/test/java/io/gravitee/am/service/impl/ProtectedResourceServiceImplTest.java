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
package io.gravitee.am.service.impl;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.ProtectedResourceRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.MembershipService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.model.McpTool;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ProtectedResourceFeature;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.exception.ClientAlreadyExistsException;
import io.gravitee.am.service.exception.InvalidProtectedResourceException;
import io.gravitee.am.service.exception.ProtectedResourceNotFoundException;
import io.gravitee.am.service.model.NewProtectedResource;
import io.gravitee.am.service.model.UpdateMcpTool;
import io.gravitee.am.service.model.UpdateProtectedResource;
import io.gravitee.am.service.spring.application.ApplicationSecretConfig;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class ProtectedResourceServiceImplTest {

    @Mock
    private ProtectedResourceRepository repository;

    @Mock
    private ApplicationSecretConfig applicationSecretConfig;

    @Mock
    private SecretService secretService;

    @Mock
    private RoleService roleService;

    @Mock
    private MembershipService membershipService;

    @Mock
    private OAuthClientUniquenessValidator oAuthClientUniquenessValidator;

    @Mock
    private AuditService auditService;

    @Mock
    private EventService eventService;

    @Mock
    private ScopeService scopeService;

    @InjectMocks
    private ProtectedResourceServiceImpl service;


    @Test
    public void shouldNotCreateProtectedResourceWhenClientIdAlreadyExists() {
        Mockito.when(applicationSecretConfig.toSecretSettings()).thenReturn(new ApplicationSecretSettings());
        Mockito.when(repository.create(any())).thenReturn(Single.never());
        Mockito.when(oAuthClientUniquenessValidator.checkClientIdUniqueness("domainId", "clientId"))
                .thenReturn(Completable.error(new ClientAlreadyExistsException("","")));
        Mockito.when(repository.existsByResourceIdentifiers(any(), any())).thenReturn(Single.just(false));
        Mockito.when(secretService.generateClientSecret(any(), any(), any(), any(), any())).thenReturn(new ClientSecret());

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        NewProtectedResource newProtectedResource = new NewProtectedResource();
        newProtectedResource.setClientId("clientId");
        newProtectedResource.setType("MCP_SERVER");
        newProtectedResource.setResourceIdentifiers(List.of("https://onet.pl"));
        service.create(domain, user, newProtectedResource)
                .test()
                .assertError(throwable -> throwable instanceof ClientAlreadyExistsException);

    }

    @Test
    public void shouldNotCreateProtectedResourceWhenResourceIdAlreadyExists() {
        Mockito.when(applicationSecretConfig.toSecretSettings()).thenReturn(new ApplicationSecretSettings());
        Mockito.when(repository.create(any())).thenReturn(Single.never());
        Mockito.when(oAuthClientUniquenessValidator.checkClientIdUniqueness("domainId", "clientId"))
                .thenReturn(Completable.complete());
        Mockito.when(repository.existsByResourceIdentifiers(any(), any())).thenReturn(Single.just(true));
        Mockito.when(secretService.generateClientSecret(any(), any(), any(), any(), any())).thenReturn(new ClientSecret());

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        NewProtectedResource newProtectedResource = new NewProtectedResource();
        newProtectedResource.setClientId("clientId");
        newProtectedResource.setType("MCP_SERVER");
        newProtectedResource.setResourceIdentifiers(List.of("https://onet.pl"));
        service.create(domain, user, newProtectedResource)
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException);

        Mockito.verify(auditService, Mockito.times(0)).report(any());

    }

    @Test
    public void shouldCreateProtectedResourceWhenClientIdDoesntExist() {
        Mockito.when(applicationSecretConfig.toSecretSettings()).thenReturn(new ApplicationSecretSettings());
        Mockito.when(repository.create(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        Mockito.when(oAuthClientUniquenessValidator.checkClientIdUniqueness("domainId", "clientId"))
                .thenReturn(Completable.complete());
        Mockito.when(repository.existsByResourceIdentifiers(any(), any())).thenReturn(Single.just(false));
        Mockito.when(secretService.generateClientSecret(any(), any(), any(), any(), any())).thenReturn(new ClientSecret());
        Mockito.when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        NewProtectedResource newProtectedResource = new NewProtectedResource();
        newProtectedResource.setClientId("clientId");
        newProtectedResource.setType("MCP_SERVER");
        newProtectedResource.setResourceIdentifiers(List.of("https://onet.pl"));
        var result = service.create(domain, user, newProtectedResource)
                .test()
                .assertComplete()
                .assertValue(v -> v.getClientId().equals("clientId"));
        Mockito.verify(auditService, Mockito.times(1)).report(any());

        // Verify eventService.create() was called with correct arguments using argThat
        String resourceId = result.values().getFirst().getId();
        Mockito.verify(eventService, Mockito.times(1)).create(
                argThat(event -> event.getType() == Type.PROTECTED_RESOURCE &&
                        event.getPayload().getId().equals(resourceId) &&
                        event.getPayload().getReferenceType() == ReferenceType.DOMAIN &&
                        event.getPayload().getReferenceId().equals(domain.getId()) &&
                        event.getPayload().getAction() == Action.CREATE),
                argThat(d -> d.equals(domain))
        );
    }

    @Test
    public void shouldUpdateProtectedResource() {
        // Setup existing resource
        ProtectedResource existingResource = new ProtectedResource();
        existingResource.setId("resource-id");
        existingResource.setDomainId("domainId");
        existingResource.setName("Old Name");
        existingResource.setResourceIdentifiers(List.of("https://old.example.com"));
        existingResource.setDescription("Old Description");
        
        McpTool existingTool = new McpTool();
        existingTool.setKey("tool1");
        existingTool.setDescription("Tool 1");
        existingTool.setScopes(List.of("scope1"));
        existingTool.setCreatedAt(new Date());
        existingResource.setFeatures(List.of(existingTool));

        // Setup update request
        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("New Name");
        updateRequest.setResourceIdentifiers(List.of("https://new.example.com"));
        updateRequest.setDescription("New Description");
        
        UpdateMcpTool updatedTool = new UpdateMcpTool();
        updatedTool.setKey("tool1");
        updatedTool.setDescription("Updated Tool 1");
        updatedTool.setScopes(List.of("scope1", "scope2"));
        updateRequest.setFeatures(List.of(updatedTool));

        // Mock dependencies
        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));
        Mockito.when(repository.existsByResourceIdentifiers(eq("domainId"), any())).thenReturn(Single.just(false));
        Mockito.when(scopeService.validateScope(eq("domainId"), any())).thenReturn(Single.just(true));
        Mockito.when(repository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        Mockito.when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        // Execute update
        var result = service.update(domain, "resource-id", updateRequest, user)
                .test()
                .assertComplete()
                .assertValue(v -> v.getName().equals("New Name") && 
                             v.getDescription().equals("New Description") &&
                             v.getResourceIdentifiers().contains("https://new.example.com"));

        // Verify audit and event were called
        Mockito.verify(auditService, Mockito.times(1)).report(any());
        Mockito.verify(eventService, Mockito.times(1)).create(
                argThat(event -> event.getType() == Type.PROTECTED_RESOURCE &&
                        event.getPayload().getAction() == Action.UPDATE),
                argThat(d -> d.equals(domain))
        );
    }

    @Test
    public void shouldNotUpdateProtectedResourceWhenNotFound() {
        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("New Name");
        updateRequest.setResourceIdentifiers(List.of("https://example.com"));
        updateRequest.setFeatures(new ArrayList<>());

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.empty());

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.update(domain, "resource-id", updateRequest, user)
                .test()
                .assertError(throwable -> throwable instanceof ProtectedResourceNotFoundException);

        Mockito.verify(repository, Mockito.never()).update(any());
    }

    @Test
    public void shouldNotUpdateProtectedResourceWhenDomainMismatch() {
        ProtectedResource existingResource = new ProtectedResource();
        existingResource.setId("resource-id");
        existingResource.setDomainId("differentDomainId");
        existingResource.setFeatures(new ArrayList<>());

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("New Name");
        updateRequest.setResourceIdentifiers(List.of("https://example.com"));
        updateRequest.setFeatures(new ArrayList<>());

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.update(domain, "resource-id", updateRequest, user)
                .test()
                .assertError(throwable -> throwable instanceof ProtectedResourceNotFoundException);

        Mockito.verify(repository, Mockito.never()).update(any());
    }

    @Test
    public void shouldNotUpdateProtectedResourceWhenDuplicateFeatureKeys() {
        ProtectedResource existingResource = new ProtectedResource();
        existingResource.setId("resource-id");
        existingResource.setDomainId("domainId");
        existingResource.setResourceIdentifiers(List.of("https://example.com"));
        existingResource.setFeatures(new ArrayList<>());

        UpdateMcpTool tool1 = new UpdateMcpTool();
        tool1.setKey("tool1");
        tool1.setDescription("Tool 1");
        tool1.setScopes(List.of("scope1"));

        UpdateMcpTool tool2 = new UpdateMcpTool();
        tool2.setKey("tool1");  // Duplicate key
        tool2.setDescription("Tool 2");
        tool2.setScopes(List.of("scope2"));

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("Name");
        updateRequest.setResourceIdentifiers(List.of("https://example.com"));
        updateRequest.setFeatures(List.of(tool1, tool2));

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));
        Mockito.when(scopeService.validateScope(any(), any())).thenReturn(Single.just(true));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.update(domain, "resource-id", updateRequest, user)
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException);

        Mockito.verify(repository, Mockito.never()).update(any());
    }

    @Test
    public void shouldNotUpdateProtectedResourceWhenInvalidScopes() {
        ProtectedResource existingResource = new ProtectedResource();
        existingResource.setId("resource-id");
        existingResource.setDomainId("domainId");
        existingResource.setResourceIdentifiers(List.of("https://example.com"));
        existingResource.setFeatures(new ArrayList<>());

        UpdateMcpTool tool = new UpdateMcpTool();
        tool.setKey("tool1");
        tool.setDescription("Tool 1");
        tool.setScopes(List.of("invalid_scope"));

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("Name");
        updateRequest.setResourceIdentifiers(List.of("https://example.com"));
        updateRequest.setFeatures(List.of(tool));

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));
        Mockito.when(scopeService.validateScope(any(), any())).thenReturn(Single.just(false));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.update(domain, "resource-id", updateRequest, user)
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException);

        Mockito.verify(repository, Mockito.never()).update(any());
    }
}