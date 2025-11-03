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
import io.gravitee.am.model.Membership;
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
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.exception.ClientAlreadyExistsException;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.exception.InvalidProtectedResourceException;
import io.gravitee.am.service.exception.ProtectedResourceNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewProtectedResource;
import io.gravitee.am.service.model.PatchProtectedResource;
import io.gravitee.am.service.model.UpdateMcpTool;
import io.gravitee.am.service.model.UpdateProtectedResource;
import io.gravitee.am.service.spring.application.ApplicationSecretConfig;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

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
        Mockito.when(repository.existsByResourceIdentifiersExcludingId(Mockito.eq("domainId"), Mockito.anyList(), Mockito.eq("resource-id"))).thenReturn(Single.just(false));
        Mockito.when(scopeService.validateScope(Mockito.eq("domainId"), Mockito.anyList())).thenReturn(Single.just(true));
        Mockito.when(repository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        Mockito.when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        // Execute update
        service.update(domain, "resource-id", updateRequest, user)
                .test()
                .assertComplete()
                .assertValue(v -> v.name().equals("New Name") && 
                             v.description().equals("New Description") &&
                             v.resourceIdentifiers().contains("https://new.example.com"));

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

    @Test
    public void shouldDeleteProtectedResource() {
        // existing resource
        ProtectedResource resource = new ProtectedResource();
        resource.setId("res-1");
        resource.setDomainId("domainId");

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        Mockito.when(repository.findById("res-1")).thenReturn(Maybe.just(resource));
        Mockito.when(repository.delete("res-1")).thenReturn(Completable.complete());
        Mockito.when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        Mockito.when(membershipService.findByReference(eq("res-1"), eq(ReferenceType.APPLICATION)))
                .thenReturn(Flowable.empty());

        service.delete(domain, "res-1", null, user)
                .test()
                .assertComplete()
                .assertNoErrors();

        Mockito.verify(repository, Mockito.times(1)).delete("res-1");
        Mockito.verify(eventService, Mockito.times(1)).create(any(), eq(domain));
        Mockito.verify(auditService, Mockito.times(1)).report(any());
    }

    @Test
    public void shouldDeleteProtectedResource_cleansMemberships() {
        ProtectedResource resource = new ProtectedResource();
        resource.setId("res-2");
        resource.setDomainId("domainId");

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        Mockito.when(repository.findById("res-2")).thenReturn(Maybe.just(resource));
        Mockito.when(repository.delete("res-2")).thenReturn(Completable.complete());
        Mockito.when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        Mockito.when(membershipService.findByReference(eq("res-2"), eq(ReferenceType.APPLICATION)))
                .thenReturn(Flowable.just(new Membership()));
        Mockito.when(membershipService.delete(any())).thenReturn(Completable.complete());

        service.delete(domain, "res-2", null, user)
                .test()
                .assertComplete()
                .assertNoErrors();

        Mockito.verify(membershipService, Mockito.atLeastOnce()).delete(any());
    }

    @Test
    public void shouldNotDeleteProtectedResourceWhenNotFound() {
        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        Mockito.when(repository.findById("missing")).thenReturn(Maybe.empty());

        service.delete(domain, "missing", null, user)
                .test()
                .assertError(throwable -> throwable instanceof ProtectedResourceNotFoundException);

        Mockito.verify(repository, Mockito.never()).delete(any());
    }

    @Test
    public void shouldFindByDomain() {
        // given
        String domainId = "domain-id";
        ProtectedResource resource1 = new ProtectedResource();
        resource1.setId("res-1");
        resource1.setDomainId(domainId);
        resource1.setName("Resource 1");

        ProtectedResource resource2 = new ProtectedResource();
        resource2.setId("res-2");
        resource2.setDomainId(domainId);
        resource2.setName("Resource 2");

        Mockito.when(repository.findByDomain(domainId))
                .thenReturn(Flowable.just(resource1, resource2));

        // when
        TestSubscriber<ProtectedResource> observer = service.findByDomain(domainId).test();

        // then
        observer.assertComplete();
        observer.assertValueCount(2);
        observer.assertValueAt(0, resource -> resource.getId().equals("res-1") && resource.getDomainId().equals(domainId));
        observer.assertValueAt(1, resource -> resource.getId().equals("res-2") && resource.getDomainId().equals(domainId));
        Mockito.verify(repository, Mockito.times(1)).findByDomain(domainId);
    }

    @Test
    public void shouldHandleErrorOnFindByDomain() {
        // given
        String domainId = "domain-id";
        RuntimeException repositoryError = new RuntimeException("Database error");

        Mockito.when(repository.findByDomain(domainId))
                .thenReturn(Flowable.error(repositoryError));

        // when
        TestSubscriber<ProtectedResource> observer = service.findByDomain(domainId).test();

        // then
        observer.assertError(TechnicalManagementException.class);
        observer.assertError(throwable -> 
            throwable.getMessage().contains("An error occurs while trying to find protected resources by domain " + domainId));
        Mockito.verify(repository, Mockito.times(1)).findByDomain(domainId);
    }

    @Test
    public void shouldPatchProtectedResource() {
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

        // Setup patch request - only update name and description
        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("New Name"));
        patchRequest.setDescription(Optional.of("New Description"));

        // Mock dependencies
        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));
        // scopeService.validateScope is called because existing resource has features with scopes
        Mockito.when(scopeService.validateScope(eq("domainId"), any())).thenReturn(Single.just(true));
        Mockito.when(repository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        Mockito.when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        // Execute patch
        service.patch(domain, "resource-id", patchRequest, user)
                .test()
                .assertComplete()
                .assertValue(v -> v.name().equals("New Name") && 
                             v.description().equals("New Description") &&
                             v.resourceIdentifiers().contains("https://old.example.com")); // Resource identifiers not updated

        // Verify interactions
        Mockito.verify(repository, Mockito.times(1)).findById("resource-id");
        Mockito.verify(scopeService, Mockito.times(1)).validateScope(eq("domainId"), any());
        Mockito.verify(repository, Mockito.times(1)).update(any());
        Mockito.verify(auditService, Mockito.times(1)).report(any());
        Mockito.verify(eventService, Mockito.times(1)).create(
                argThat(event -> event.getType() == Type.PROTECTED_RESOURCE &&
                        event.getPayload().getAction() == Action.UPDATE),
                argThat(d -> d.equals(domain))
        );
    }

    @Test
    public void shouldNotPatchProtectedResourceWhenNotFound() {
        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("New Name"));

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.empty());

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.patch(domain, "resource-id", patchRequest, user)
                .test()
                .assertError(throwable -> throwable instanceof ProtectedResourceNotFoundException);

        // Verify no side effects occurred
        Mockito.verify(repository, Mockito.times(1)).findById("resource-id");
        Mockito.verify(repository, Mockito.never()).update(any());
        Mockito.verify(auditService, Mockito.never()).report(any());
        Mockito.verify(eventService, Mockito.never()).create(any(), any());
    }

    @Test
    public void shouldNotPatchProtectedResourceWhenDomainMismatch() {
        ProtectedResource existingResource = new ProtectedResource();
        existingResource.setId("resource-id");
        existingResource.setDomainId("differentDomainId");
        existingResource.setFeatures(new ArrayList<>());

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("New Name"));

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.patch(domain, "resource-id", patchRequest, user)
                .test()
                .assertError(throwable -> throwable instanceof ProtectedResourceNotFoundException);

        // Verify no side effects occurred
        Mockito.verify(repository, Mockito.times(1)).findById("resource-id");
        Mockito.verify(repository, Mockito.never()).update(any());
        Mockito.verify(auditService, Mockito.never()).report(any());
        Mockito.verify(eventService, Mockito.never()).create(any(), any());
    }

    @Test
    public void shouldNotPatchProtectedResourceWhenDuplicateFeatureKeys() {
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

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("Name"));
        patchRequest.setResourceIdentifiers(Optional.of(List.of("https://example.com")));
        patchRequest.setFeatures(Optional.of(List.of(tool1, tool2)));

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));
        Mockito.when(scopeService.validateScope(any(), any())).thenReturn(Single.just(true));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.patch(domain, "resource-id", patchRequest, user)
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException);

        // Verify validation was called but no update occurred
        Mockito.verify(repository, Mockito.times(1)).findById("resource-id");
        Mockito.verify(repository, Mockito.never()).update(any());
        Mockito.verify(auditService, Mockito.never()).report(any());
        Mockito.verify(eventService, Mockito.never()).create(any(), any());
    }

    @Test
    public void shouldNotPatchProtectedResourceWhenInvalidScopes() {
        ProtectedResource existingResource = new ProtectedResource();
        existingResource.setId("resource-id");
        existingResource.setDomainId("domainId");
        existingResource.setResourceIdentifiers(List.of("https://example.com"));
        existingResource.setFeatures(new ArrayList<>());

        UpdateMcpTool tool = new UpdateMcpTool();
        tool.setKey("tool1");
        tool.setDescription("Tool 1");
        tool.setScopes(List.of("invalid_scope"));

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("Name"));
        patchRequest.setResourceIdentifiers(Optional.of(List.of("https://example.com")));
        patchRequest.setFeatures(Optional.of(List.of(tool)));

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));
        Mockito.when(scopeService.validateScope(any(), any())).thenReturn(Single.just(false));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.patch(domain, "resource-id", patchRequest, user)
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException);

        // Verify validation was called but no update occurred
        Mockito.verify(repository, Mockito.times(1)).findById("resource-id");
        Mockito.verify(scopeService, Mockito.times(1)).validateScope(any(), any());
        Mockito.verify(repository, Mockito.never()).update(any());
        Mockito.verify(auditService, Mockito.never()).report(any());
        Mockito.verify(eventService, Mockito.never()).create(any(), any());
    }

    @Test
    public void shouldConvertInvalidClientMetadataExceptionToInvalidProtectedResourceExceptionOnPatch() {
        // This test verifies that when scopeService.validateScope() throws InvalidClientMetadataException,
        // it gets converted to InvalidProtectedResourceException (which returns 400 instead of potentially 500)
        ProtectedResource existingResource = new ProtectedResource();
        existingResource.setId("resource-id");
        existingResource.setDomainId("domainId");
        existingResource.setResourceIdentifiers(List.of("https://example.com"));
        existingResource.setFeatures(new ArrayList<>());

        UpdateMcpTool tool = new UpdateMcpTool();
        tool.setKey("tool1");
        tool.setDescription("Tool 1");
        tool.setScopes(List.of("invalid_scope"));

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("Name"));
        patchRequest.setResourceIdentifiers(Optional.of(List.of("https://example.com")));
        patchRequest.setFeatures(Optional.of(List.of(tool)));

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));
        // Mock validateScope to throw InvalidClientMetadataException (actual behavior in production)
        Mockito.when(scopeService.validateScope(any(), any())).thenReturn(
                Single.error(new InvalidClientMetadataException("scope invalid_scope is not valid.")));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.patch(domain, "resource-id", patchRequest, user)
                .test()
                .assertError(throwable -> {
                    // Verify it was converted to InvalidProtectedResourceException
                    return throwable instanceof InvalidProtectedResourceException &&
                           throwable.getMessage().equals("scope invalid_scope is not valid.");
                });

        // Verify validation was called but no update occurred
        Mockito.verify(repository, Mockito.times(1)).findById("resource-id");
        Mockito.verify(scopeService, Mockito.times(1)).validateScope(any(), any());
        Mockito.verify(repository, Mockito.never()).update(any());
        Mockito.verify(auditService, Mockito.never()).report(any());
        Mockito.verify(eventService, Mockito.never()).create(any(), any());
    }

    @Test
    public void shouldConvertInvalidClientMetadataExceptionToInvalidProtectedResourceExceptionOnUpdate() {
        // This test verifies that when scopeService.validateScope() throws InvalidClientMetadataException,
        // it gets converted to InvalidProtectedResourceException (which returns 400 instead of potentially 500)
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
        // Mock validateScope to throw InvalidClientMetadataException (actual behavior in production)
        Mockito.when(scopeService.validateScope(any(), any())).thenReturn(
                Single.error(new InvalidClientMetadataException("scope invalid_scope is not valid.")));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.update(domain, "resource-id", updateRequest, user)
                .test()
                .assertError(throwable -> {
                    // Verify it was converted to InvalidProtectedResourceException
                    return throwable instanceof InvalidProtectedResourceException &&
                           throwable.getMessage().equals("scope invalid_scope is not valid.");
                });

        // Verify validation was called but no update occurred
        Mockito.verify(repository, Mockito.times(1)).findById("resource-id");
        Mockito.verify(scopeService, Mockito.times(1)).validateScope(any(), any());
        Mockito.verify(repository, Mockito.never()).update(any());
        Mockito.verify(auditService, Mockito.never()).report(any());
        Mockito.verify(eventService, Mockito.never()).create(any(), any());
    }

    @Test
    public void shouldPatchProtectedResourceWithPartialFields() {
        // Setup existing resource
        ProtectedResource existingResource = new ProtectedResource();
        existingResource.setId("resource-id");
        existingResource.setDomainId("domainId");
        existingResource.setName("Old Name");
        existingResource.setDescription("Old Description");
        existingResource.setResourceIdentifiers(List.of("https://old.example.com"));

        // Setup patch request - only update name, leave description unchanged
        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("New Name"));
        // Description not provided, should remain unchanged

        // Mock dependencies
        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));
        // scopeService.validateScope is not called because no features exist
        // repository.existsByResourceIdentifiers is not called because resourceIdentifiers don't change
        Mockito.when(repository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        Mockito.when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        // Execute patch
        service.patch(domain, "resource-id", patchRequest, user)
                .test()
                .assertComplete()
                .assertValue(v -> v.name().equals("New Name") && 
                             v.description().equals("Old Description")); // Description unchanged

        // Verify interactions
        Mockito.verify(repository, Mockito.times(1)).findById("resource-id");
        Mockito.verify(repository, Mockito.times(1)).update(any());
        Mockito.verify(auditService, Mockito.times(1)).report(any());
        Mockito.verify(eventService, Mockito.times(1)).create(any(), any());
    }

    @Test
    public void shouldNotUpdateProtectedResourceWhenIdentifierTakenByAnotherResource() {
        // Test the race condition fix: when a resource identifier that was previously owned
        // by this resource has been taken by another resource in the meantime
        ProtectedResource existingResource = new ProtectedResource();
        existingResource.setId("resource-id");
        existingResource.setDomainId("domainId");
        existingResource.setResourceIdentifiers(List.of("https://example.com", "https://example2.com"));
        existingResource.setFeatures(new ArrayList<>());

        // Update request that keeps "https://example.com" (which was in old list)
        // but this identifier has been taken by another resource in the meantime
        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("New Name");
        updateRequest.setResourceIdentifiers(List.of("https://example.com", "https://example3.com"));
        updateRequest.setFeatures(new ArrayList<>());

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));
        // The identifier "https://example.com" exists in another resource (not this one)
        Mockito.when(repository.existsByResourceIdentifiersExcludingId(Mockito.eq("domainId"), Mockito.anyList(), Mockito.eq("resource-id")))
                .thenReturn(Single.just(true));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.update(domain, "resource-id", updateRequest, user)
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException &&
                        throwable.getMessage().equals("Resource identifier already exists"));

        // Verify the new method was called (not the old one)
        Mockito.verify(repository, Mockito.times(1)).existsByResourceIdentifiersExcludingId(
                Mockito.eq("domainId"),
                Mockito.argThat(identifiers -> identifiers.contains("https://example.com") && identifiers.contains("https://example3.com")),
                Mockito.eq("resource-id"));
        Mockito.verify(repository, Mockito.never()).existsByResourceIdentifiers(Mockito.anyString(), Mockito.anyList());
        Mockito.verify(repository, Mockito.never()).update(Mockito.any());
    }

    @Test
    public void shouldNotPatchProtectedResourceWhenIdentifierTakenByAnotherResource() {
        // Test the race condition fix for patch operation
        ProtectedResource existingResource = new ProtectedResource();
        existingResource.setId("resource-id");
        existingResource.setDomainId("domainId");
        existingResource.setResourceIdentifiers(List.of("https://example.com", "https://example2.com"));
        existingResource.setFeatures(new ArrayList<>());

        // Patch request that keeps "https://example.com" (which was in old list)
        // but this identifier has been taken by another resource in the meantime
        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setResourceIdentifiers(Optional.of(List.of("https://example.com", "https://example3.com")));

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));
        // The identifier "https://example.com" exists in another resource (not this one)
        Mockito.when(repository.existsByResourceIdentifiersExcludingId(Mockito.eq("domainId"), Mockito.anyList(), Mockito.eq("resource-id")))
                .thenReturn(Single.just(true));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.patch(domain, "resource-id", patchRequest, user)
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException &&
                        throwable.getMessage().equals("Resource identifier already exists"));

        // Verify the new method was called (not the old one)
        Mockito.verify(repository, Mockito.times(1)).existsByResourceIdentifiersExcludingId(
                Mockito.eq("domainId"),
                Mockito.argThat(identifiers -> identifiers.contains("https://example.com") && identifiers.contains("https://example3.com")),
                Mockito.eq("resource-id"));
        Mockito.verify(repository, Mockito.never()).existsByResourceIdentifiers(Mockito.anyString(), Mockito.anyList());
        Mockito.verify(repository, Mockito.never()).update(Mockito.any());
    }

    @Test
    public void shouldNotPatchProtectedResourceWhenResourceIdentifiersIsNull() {
        // Test null-safety: patching with Optional.empty() resourceIdentifiers should fail
        // SetterUtils.safeSet sets field to null when Optional.empty() is provided
        ProtectedResource existingResource = new ProtectedResource();
        existingResource.setId("resource-id");
        existingResource.setDomainId("domainId");
        existingResource.setResourceIdentifiers(List.of("https://example.com"));
        existingResource.setFeatures(new ArrayList<>());

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setResourceIdentifiers(Optional.empty()); // This will set resourceIdentifiers to null after patching

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        // Create a patch that results in null resourceIdentifiers
        // When Optional.empty() is used, SetterUtils.safeSet sets the field to null
        service.patch(domain, "resource-id", patchRequest, user)
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException &&
                        throwable.getMessage().equals("Field [resourceIdentifiers] must not be empty"));

        // Verify validation failed before any update
        Mockito.verify(repository, Mockito.times(1)).findById("resource-id");
        Mockito.verify(repository, Mockito.never()).update(any());
        Mockito.verify(repository, Mockito.never()).existsByResourceIdentifiersExcludingId(any(), any(), any());
    }

    @Test
    public void shouldNotPatchProtectedResourceWhenResourceIdentifiersIsEmpty() {
        // Test validation: patching with empty resourceIdentifiers list should fail
        ProtectedResource existingResource = new ProtectedResource();
        existingResource.setId("resource-id");
        existingResource.setDomainId("domainId");
        existingResource.setResourceIdentifiers(List.of("https://example.com"));
        existingResource.setFeatures(new ArrayList<>());

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setResourceIdentifiers(Optional.of(new ArrayList<>())); // Empty list

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.patch(domain, "resource-id", patchRequest, user)
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException &&
                        throwable.getMessage().equals("Field [resourceIdentifiers] must not be empty"));

        // Verify validation failed before any update
        Mockito.verify(repository, Mockito.times(1)).findById("resource-id");
        Mockito.verify(repository, Mockito.never()).update(any());
        Mockito.verify(repository, Mockito.never()).existsByResourceIdentifiersExcludingId(any(), any(), any());
    }

    @Test
    public void shouldNotUpdateProtectedResourceWhenResourceIdentifiersIsNull() {
        // Test null-safety: updating with null resourceIdentifiers should fail
        ProtectedResource existingResource = new ProtectedResource();
        existingResource.setId("resource-id");
        existingResource.setDomainId("domainId");
        existingResource.setResourceIdentifiers(List.of("https://example.com"));
        existingResource.setFeatures(new ArrayList<>());

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("New Name");
        updateRequest.setResourceIdentifiers(null); // Null list
        updateRequest.setFeatures(new ArrayList<>());

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.update(domain, "resource-id", updateRequest, user)
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException &&
                        throwable.getMessage().equals("Field [resourceIdentifiers] must not be empty"));

        // Verify validation failed before any update
        Mockito.verify(repository, Mockito.times(1)).findById("resource-id");
        Mockito.verify(repository, Mockito.never()).update(any());
        Mockito.verify(repository, Mockito.never()).existsByResourceIdentifiersExcludingId(any(), any(), any());
    }

    @Test
    public void shouldNotUpdateProtectedResourceWhenResourceIdentifiersIsEmpty() {
        // Test validation: updating with empty resourceIdentifiers list should fail
        ProtectedResource existingResource = new ProtectedResource();
        existingResource.setId("resource-id");
        existingResource.setDomainId("domainId");
        existingResource.setResourceIdentifiers(List.of("https://example.com"));
        existingResource.setFeatures(new ArrayList<>());

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("New Name");
        updateRequest.setResourceIdentifiers(new ArrayList<>()); // Empty list
        updateRequest.setFeatures(new ArrayList<>());

        Mockito.when(repository.findById("resource-id")).thenReturn(Maybe.just(existingResource));

        Domain domain = new Domain();
        domain.setId("domainId");

        User user = new DefaultUser();

        service.update(domain, "resource-id", updateRequest, user)
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException &&
                        throwable.getMessage().equals("Field [resourceIdentifiers] must not be empty"));

        // Verify validation failed before any update
        Mockito.verify(repository, Mockito.times(1)).findById("resource-id");
        Mockito.verify(repository, Mockito.never()).update(any());
        Mockito.verify(repository, Mockito.never()).existsByResourceIdentifiersExcludingId(any(), any(), any());
    }
}