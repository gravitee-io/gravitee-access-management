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
import io.gravitee.am.model.SecretExpirationSettings;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.ProtectedResourceRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CertificateService;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import io.gravitee.am.service.exception.ClientSecretDeleteException;
import io.gravitee.am.service.exception.ClientSecretInvalidException;
import io.gravitee.am.service.exception.ClientSecretNotFoundException;
import io.gravitee.am.service.exception.TooManyClientSecretsException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProtectedResourceServiceImplTest {

    // Test constants
    private static final String DOMAIN_ID = "domainId";
    private static final String RESOURCE_ID = "resource-id";
    private static final String CLIENT_ID = "clientId";
    private static final String RESOURCE_URI = "https://example.com";

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

    @Mock
    private CertificateService certificateService;

    @InjectMocks
    private ProtectedResourceServiceImpl service;

    @BeforeEach
    public void setup() {
        ReflectionTestUtils.setField(service, "secretsMax", 10);
    }

    // Helper methods
    private Domain createDomain() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        return domain;
    }

    private User createUser() {
        return new DefaultUser();
    }

    private ProtectedResource createProtectedResource(String id, String domainId) {
        ProtectedResource resource = new ProtectedResource();
        resource.setId(id);
        resource.setDomainId(domainId);
        resource.setResourceIdentifiers(List.of(RESOURCE_URI));
        resource.setFeatures(new ArrayList<>());
        return resource;
    }

    private NewProtectedResource createNewProtectedResource() {
        NewProtectedResource resource = new NewProtectedResource();
        resource.setClientId(CLIENT_ID);
        resource.setType("MCP_SERVER");
        resource.setResourceIdentifiers(List.of("https://onet.pl"));
        return resource;
    }

    private UpdateMcpTool createUpdateMcpTool(String key, String description, List<String> scopes) {
        UpdateMcpTool tool = new UpdateMcpTool();
        tool.setKey(key);
        tool.setDescription(description);
        tool.setScopes(scopes);
        return tool;
    }

    private McpTool createMcpTool(String key, String description, List<String> scopes) {
        McpTool tool = new McpTool();
        tool.setKey(key);
        tool.setDescription(description);
        tool.setScopes(scopes);
        tool.setCreatedAt(new Date());
        return tool;
    }

    // ========== CREATE Tests ==========

    @Test
    public void shouldNotCreateProtectedResourceWhenClientIdAlreadyExists() {
        when(applicationSecretConfig.toSecretSettings()).thenReturn(new ApplicationSecretSettings());
        when(repository.create(any())).thenReturn(Single.never());
        when(oAuthClientUniquenessValidator.checkClientIdUniqueness(DOMAIN_ID, CLIENT_ID))
                .thenReturn(Completable.error(new ClientAlreadyExistsException("", "")));
        when(repository.existsByResourceIdentifiers(any(), any())).thenReturn(Single.just(false));
        when(secretService.generateClientSecret(any(), any(), any(), any(), any())).thenReturn(new ClientSecret());

        service.create(createDomain(), createUser(), createNewProtectedResource())
                .test()
                .assertError(ClientAlreadyExistsException.class::isInstance);
    }

    @Test
    public void shouldNotCreateProtectedResourceWhenResourceIdAlreadyExists() {
        when(applicationSecretConfig.toSecretSettings()).thenReturn(new ApplicationSecretSettings());
        when(repository.create(any())).thenReturn(Single.never());
        when(oAuthClientUniquenessValidator.checkClientIdUniqueness(DOMAIN_ID, CLIENT_ID))
                .thenReturn(Completable.complete());
        when(repository.existsByResourceIdentifiers(any(), any())).thenReturn(Single.just(true));
        when(secretService.generateClientSecret(any(), any(), any(), any(), any())).thenReturn(new ClientSecret());

        service.create(createDomain(), createUser(), createNewProtectedResource())
                .test()
                .assertError(InvalidProtectedResourceException.class::isInstance);

        verify(auditService, never()).report(any());
    }

    @Test
    public void shouldCreateProtectedResourceWhenClientIdDoesntExist() {
        when(applicationSecretConfig.toSecretSettings()).thenReturn(new ApplicationSecretSettings());
        when(repository.create(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(oAuthClientUniquenessValidator.checkClientIdUniqueness(DOMAIN_ID, CLIENT_ID))
                .thenReturn(Completable.complete());
        when(repository.existsByResourceIdentifiers(any(), any())).thenReturn(Single.just(false));
        when(secretService.generateClientSecret(any(), any(), any(), any(), any())).thenReturn(new ClientSecret());
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));

        Domain domain = createDomain();
        var result = service.create(domain, createUser(), createNewProtectedResource())
                .test()
                .assertComplete()
                .assertValue(v -> v.getClientId().equals(CLIENT_ID));

        verify(auditService, times(1)).report(any());
        String resourceId = result.values().getFirst().getId();
        verify(eventService, times(1)).create(
                argThat(event -> event.getType() == Type.PROTECTED_RESOURCE &&
                        event.getPayload().getId().equals(resourceId) &&
                        event.getPayload().getReferenceType() == ReferenceType.DOMAIN &&
                        event.getPayload().getReferenceId().equals(domain.getId()) &&
                        event.getPayload().getAction() == Action.CREATE),
                argThat(d -> d.equals(domain))
        );
    }

    // ========== UPDATE Tests ==========

    @Test
    public void shouldUpdateProtectedResource() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);
        existingResource.setName("Old Name");
        existingResource.setResourceIdentifiers(List.of("https://old.example.com"));
        existingResource.setDescription("Old Description");
        existingResource.setFeatures(List.of(createMcpTool("tool1", "Tool 1", List.of("scope1"))));

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("New Name");
        updateRequest.setResourceIdentifiers(List.of("https://new.example.com"));
        updateRequest.setDescription("New Description");
        updateRequest.setFeatures(List.of(createUpdateMcpTool("tool1", "Updated Tool 1", List.of("scope1", "scope2"))));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(repository.existsByResourceIdentifiersExcludingId(eq(DOMAIN_ID), any(), eq(RESOURCE_ID)))
                .thenReturn(Single.just(false));
        when(scopeService.validateScope(eq(DOMAIN_ID), any())).thenReturn(Single.just(true));
        when(repository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));

        Domain domain = createDomain();
        service.update(domain, RESOURCE_ID, updateRequest, createUser())
                .test()
                .assertComplete()
                .assertValue(v -> v.name().equals("New Name") &&
                        v.description().equals("New Description") &&
                        v.resourceIdentifiers().contains("https://new.example.com"));

        verify(auditService, times(1)).report(any());
        verify(eventService, times(1)).create(
                argThat(event -> event.getType() == Type.PROTECTED_RESOURCE &&
                        event.getPayload().getAction() == Action.UPDATE),
                argThat(d -> d.equals(domain))
        );
    }

    @Test
    public void shouldUpdateProtectedResourceWithSettings() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("New Name");
        updateRequest.setResourceIdentifiers(List.of(RESOURCE_URI));
        updateRequest.setFeatures(new ArrayList<>());
        
        ApplicationSettings settings = new ApplicationSettings();
        io.gravitee.am.model.application.ApplicationOAuthSettings oauth = new io.gravitee.am.model.application.ApplicationOAuthSettings();
        oauth.setScopes(List.of("scope1", "scope2"));
        settings.setOauth(oauth);
        updateRequest.setSettings(settings);

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(repository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));

        Domain domain = createDomain();
        service.update(domain, RESOURCE_ID, updateRequest, createUser())
                .test()
                .assertComplete()
                .assertValue(v -> v.settings() != null && 
                                  v.settings().getOauth() != null && 
                                  v.settings().getOauth().getScopes().contains("scope1"));

        verify(auditService, times(1)).report(any());
        verify(eventService, times(1)).create(
                argThat(event -> event.getType() == Type.PROTECTED_RESOURCE &&
                        event.getPayload().getAction() == Action.UPDATE),
                argThat(d -> d.equals(domain))
        );
    }

    @Test
    public void shouldNotUpdateProtectedResourceWhenNotFound() {
        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("New Name");
        updateRequest.setResourceIdentifiers(List.of(RESOURCE_URI));
        updateRequest.setFeatures(new ArrayList<>());

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.empty());

        service.update(createDomain(), RESOURCE_ID, updateRequest, createUser())
                .test()
                .assertError(ProtectedResourceNotFoundException.class::isInstance);

        verify(repository, never()).update(any());
    }

    @Test
    public void shouldNotUpdateProtectedResourceWhenDuplicateFeatureKeys() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);

        UpdateMcpTool tool1 = createUpdateMcpTool("tool1", "Tool 1", List.of("scope1"));
        UpdateMcpTool tool2 = createUpdateMcpTool("tool1", "Tool 2", List.of("scope2")); // Duplicate key

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("Name");
        updateRequest.setResourceIdentifiers(List.of(RESOURCE_URI));
        updateRequest.setFeatures(List.of(tool1, tool2));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(scopeService.validateScope(any(), any())).thenReturn(Single.just(true));

        service.update(createDomain(), RESOURCE_ID, updateRequest, createUser())
                .test()
                .assertError(InvalidProtectedResourceException.class::isInstance);

        verify(repository, never()).update(any());
    }

    @Test
    public void shouldNotUpdateProtectedResourceWhenInvalidScopes() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("Name");
        updateRequest.setResourceIdentifiers(List.of(RESOURCE_URI));
        updateRequest.setFeatures(List.of(createUpdateMcpTool("tool1", "Tool 1", List.of("invalid_scope"))));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(scopeService.validateScope(any(), any())).thenReturn(Single.just(false));

        service.update(createDomain(), RESOURCE_ID, updateRequest, createUser())
                .test()
                .assertError(InvalidProtectedResourceException.class::isInstance);

        verify(repository, never()).update(any());
    }

    // ========== DELETE Tests ==========

    @Test
    public void shouldDeleteProtectedResource() {
        ProtectedResource resource = createProtectedResource("res-1", DOMAIN_ID);

        when(repository.findByDomainAndId(DOMAIN_ID, "res-1")).thenReturn(Maybe.just(resource));
        when(repository.delete("res-1")).thenReturn(Completable.complete());
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        when(membershipService.findByReference(eq("res-1"), eq(ReferenceType.PROTECTED_RESOURCE)))
                .thenReturn(Flowable.empty());

        Domain domain = createDomain();
        service.delete(domain, "res-1", null, createUser())
                .test()
                .assertComplete()
                .assertNoErrors();

        verify(repository, times(1)).delete("res-1");
        verify(eventService, times(1)).create(any(), eq(domain));
        verify(auditService, times(1)).report(any());
    }

    @Test
    public void shouldDeleteProtectedResource_cleansMemberships() {
        ProtectedResource resource = createProtectedResource("res-2", DOMAIN_ID);

        when(repository.findByDomainAndId(DOMAIN_ID, "res-2")).thenReturn(Maybe.just(resource));
        when(repository.delete("res-2")).thenReturn(Completable.complete());
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        when(membershipService.findByReference(eq("res-2"), eq(ReferenceType.PROTECTED_RESOURCE)))
                .thenReturn(Flowable.just(new Membership()));
        when(membershipService.delete(any())).thenReturn(Completable.complete());

        service.delete(createDomain(), "res-2", null, createUser())
                .test()
                .assertComplete()
                .assertNoErrors();

        verify(membershipService, atLeastOnce()).delete(any());
    }

    @Test
    public void shouldNotDeleteProtectedResourceWhenNotFound() {
        when(repository.findByDomainAndId(DOMAIN_ID, "missing")).thenReturn(Maybe.empty());

        service.delete(createDomain(), "missing", null, createUser())
                .test()
                .assertError(ProtectedResourceNotFoundException.class::isInstance);

        verify(repository, never()).delete(any());
    }

    // ========== FIND Tests ==========

    @Test
    public void shouldFindByDomain() {
        String domainId = "domain-id";
        ProtectedResource resource1 = createProtectedResource("res-1", domainId);
        resource1.setName("Resource 1");
        ProtectedResource resource2 = createProtectedResource("res-2", domainId);
        resource2.setName("Resource 2");

        when(repository.findByDomain(domainId)).thenReturn(Flowable.just(resource1, resource2));

        TestSubscriber<ProtectedResource> observer = service.findByDomain(domainId).test();

        observer.assertComplete();
        observer.assertValueCount(2);
        observer.assertValueAt(0, resource -> resource.getId().equals("res-1") && resource.getDomainId().equals(domainId));
        observer.assertValueAt(1, resource -> resource.getId().equals("res-2") && resource.getDomainId().equals(domainId));
        verify(repository, times(1)).findByDomain(domainId);
    }

    @Test
    public void shouldHandleErrorOnFindByDomain() {
        String domainId = "domain-id";
        RuntimeException repositoryError = new RuntimeException("Database error");

        when(repository.findByDomain(domainId)).thenReturn(Flowable.error(repositoryError));

        TestSubscriber<ProtectedResource> observer = service.findByDomain(domainId).test();

        observer.assertError(TechnicalManagementException.class);
        observer.assertError(throwable ->
                throwable.getMessage().contains("An error occurs while trying to find protected resources by domain " + domainId));
        verify(repository, times(1)).findByDomain(domainId);
    }

    // ========== PATCH Tests ==========

    @Test
    public void shouldPatchProtectedResource() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);
        existingResource.setName("Old Name");
        existingResource.setResourceIdentifiers(List.of("https://old.example.com"));
        existingResource.setDescription("Old Description");
        existingResource.setFeatures(List.of(createMcpTool("tool1", "Tool 1", List.of("scope1"))));

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("New Name"));
        patchRequest.setDescription(Optional.of("New Description"));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(scopeService.validateScope(eq(DOMAIN_ID), any())).thenReturn(Single.just(true));
        when(repository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));

        Domain domain = createDomain();
        service.patch(domain, RESOURCE_ID, patchRequest, createUser())
                .test()
                .assertComplete()
                .assertValue(v -> v.name().equals("New Name") &&
                        v.description().equals("New Description") &&
                        v.resourceIdentifiers().contains("https://old.example.com"));

        verify(repository, times(1)).findByDomainAndId(DOMAIN_ID, RESOURCE_ID);
        verify(scopeService, times(1)).validateScope(eq(DOMAIN_ID), any());
        verify(repository, times(1)).update(any());
        verify(auditService, times(1)).report(any());
        verify(eventService, times(1)).create(
                argThat(event -> event.getType() == Type.PROTECTED_RESOURCE &&
                        event.getPayload().getAction() == Action.UPDATE),
                argThat(d -> d.equals(domain))
        );
    }

    @Test
    public void shouldNotPatchProtectedResourceWhenNotFound() {
        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("New Name"));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.empty());

        service.patch(createDomain(), RESOURCE_ID, patchRequest, createUser())
                .test()
                .assertError(ProtectedResourceNotFoundException.class::isInstance);

        verify(repository, times(1)).findByDomainAndId(DOMAIN_ID, RESOURCE_ID);
        verify(repository, never()).update(any());
        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any(), any());
    }

    @Test
    public void shouldNotPatchProtectedResourceWhenDuplicateFeatureKeys() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);

        UpdateMcpTool tool1 = createUpdateMcpTool("tool1", "Tool 1", List.of("scope1"));
        UpdateMcpTool tool2 = createUpdateMcpTool("tool1", "Tool 2", List.of("scope2")); // Duplicate key

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("Name"));
        patchRequest.setResourceIdentifiers(Optional.of(List.of(RESOURCE_URI)));
        patchRequest.setFeatures(Optional.of(List.of(tool1, tool2)));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(scopeService.validateScope(any(), any())).thenReturn(Single.just(true));

        service.patch(createDomain(), RESOURCE_ID, patchRequest, createUser())
                .test()
                .assertError(InvalidProtectedResourceException.class::isInstance);

        verify(repository, times(1)).findByDomainAndId(DOMAIN_ID, RESOURCE_ID);
        verify(repository, never()).update(any());
        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any(), any());
    }

    @Test
    public void shouldNotPatchProtectedResourceWhenInvalidScopes() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("Name"));
        patchRequest.setResourceIdentifiers(Optional.of(List.of(RESOURCE_URI)));
        patchRequest.setFeatures(Optional.of(List.of(createUpdateMcpTool("tool1", "Tool 1", List.of("invalid_scope")))));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(scopeService.validateScope(any(), any())).thenReturn(Single.just(false));

        service.patch(createDomain(), RESOURCE_ID, patchRequest, createUser())
                .test()
                .assertError(InvalidProtectedResourceException.class::isInstance);

        verify(repository, times(1)).findByDomainAndId(DOMAIN_ID, RESOURCE_ID);
        verify(scopeService, times(1)).validateScope(any(), any());
        verify(repository, never()).update(any());
        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any(), any());
    }

    @Test
    public void shouldNotPatchProtectedResourceWhenInvalidCertificate() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("Name"));
        patchRequest.setResourceIdentifiers(Optional.of(List.of(RESOURCE_URI)));
        patchRequest.setFeatures(Optional.of(List.of(createUpdateMcpTool("tool1", "Tool 1", List.of("scope1")))));
        patchRequest.setCertificate(Optional.of("invalid_cert"));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(scopeService.validateScope(any(), any())).thenReturn(Single.just(true));
        when(certificateService.findById(eq("invalid_cert"))).thenReturn(Maybe.empty());


        service.patch(createDomain(), RESOURCE_ID, patchRequest, createUser())
                .test()
                .assertError(InvalidProtectedResourceException.class::isInstance);

        verify(repository, times(1)).findByDomainAndId(DOMAIN_ID, RESOURCE_ID);
        verify(scopeService, times(1)).validateScope(any(), any());
        verify(certificateService, times(1)).findById(any());
        verify(repository, never()).update(any());
        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any(), any());
    }

    @Test
    public void shouldConvertInvalidClientMetadataExceptionToInvalidProtectedResourceExceptionOnPatch() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("Name"));
        patchRequest.setResourceIdentifiers(Optional.of(List.of(RESOURCE_URI)));
        patchRequest.setFeatures(Optional.of(List.of(createUpdateMcpTool("tool1", "Tool 1", List.of("invalid_scope")))));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(scopeService.validateScope(any(), any())).thenReturn(
                Single.error(new InvalidClientMetadataException("scope invalid_scope is not valid.")));

        service.patch(createDomain(), RESOURCE_ID, patchRequest, createUser())
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException &&
                        throwable.getMessage().equals("scope invalid_scope is not valid."));

        verify(repository, times(1)).findByDomainAndId(DOMAIN_ID, RESOURCE_ID);
        verify(scopeService, times(1)).validateScope(any(), any());
        verify(repository, never()).update(any());
        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any(), any());
    }

    @Test
    public void shouldConvertInvalidClientMetadataExceptionToInvalidProtectedResourceExceptionOnUpdate() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("Name");
        updateRequest.setResourceIdentifiers(List.of(RESOURCE_URI));
        updateRequest.setFeatures(List.of(createUpdateMcpTool("tool1", "Tool 1", List.of("invalid_scope"))));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(scopeService.validateScope(any(), any())).thenReturn(
                Single.error(new InvalidClientMetadataException("scope invalid_scope is not valid.")));

        service.update(createDomain(), RESOURCE_ID, updateRequest, createUser())
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException &&
                        throwable.getMessage().equals("scope invalid_scope is not valid."));

        verify(repository, times(1)).findByDomainAndId(DOMAIN_ID, RESOURCE_ID);
        verify(scopeService, times(1)).validateScope(any(), any());
        verify(repository, never()).update(any());
        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any(), any());
    }

    @Test
    public void shouldPreserveOAuth2ExceptionWhenBubblingUpFromUpdate() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("Name");
        updateRequest.setResourceIdentifiers(List.of(RESOURCE_URI));
        updateRequest.setFeatures(List.of(createUpdateMcpTool("tool1", "Tool 1", List.of("scope1"))));

        io.gravitee.am.common.exception.oauth2.InvalidRequestException oauthException =
                new io.gravitee.am.common.exception.oauth2.InvalidRequestException("Invalid OAuth2 request");

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(scopeService.validateScope(any(), any())).thenReturn(Single.error(oauthException));

        service.update(createDomain(), RESOURCE_ID, updateRequest, createUser())
                .test()
                .assertError(throwable -> throwable instanceof io.gravitee.am.common.exception.oauth2.OAuth2Exception &&
                        !(throwable instanceof TechnicalManagementException) &&
                        throwable.getMessage().equals("Invalid OAuth2 request"));

        verify(repository, times(1)).findByDomainAndId(DOMAIN_ID, RESOURCE_ID);
        verify(scopeService, times(1)).validateScope(any(), any());
        verify(repository, never()).update(any());
        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any(), any());
    }

    @Test
    public void shouldPreserveOAuth2ExceptionWhenBubblingUpFromPatch() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("Name"));
        patchRequest.setResourceIdentifiers(Optional.of(List.of(RESOURCE_URI)));
        patchRequest.setFeatures(Optional.of(List.of(createUpdateMcpTool("tool1", "Tool 1", List.of("scope1")))));

        io.gravitee.am.common.exception.oauth2.InvalidRequestException oauthException =
                new io.gravitee.am.common.exception.oauth2.InvalidRequestException("Invalid OAuth2 request");

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(scopeService.validateScope(any(), any())).thenReturn(Single.error(oauthException));

        service.patch(createDomain(), RESOURCE_ID, patchRequest, createUser())
                .test()
                .assertError(throwable -> throwable instanceof io.gravitee.am.common.exception.oauth2.OAuth2Exception &&
                        !(throwable instanceof TechnicalManagementException) &&
                        throwable.getMessage().equals("Invalid OAuth2 request"));

        verify(repository, times(1)).findByDomainAndId(DOMAIN_ID, RESOURCE_ID);
        verify(scopeService, times(1)).validateScope(any(), any());
        verify(repository, never()).update(any());
        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any(), any());
    }

    @Test
    public void shouldPatchProtectedResourceWithPartialFields() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);
        existingResource.setName("Old Name");
        existingResource.setDescription("Old Description");
        existingResource.setResourceIdentifiers(List.of("https://old.example.com"));

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setName(Optional.of("New Name"));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(repository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));

        service.patch(createDomain(), RESOURCE_ID, patchRequest, createUser())
                .test()
                .assertComplete()
                .assertValue(v -> v.name().equals("New Name") &&
                        v.description().equals("Old Description"));

        verify(repository, times(1)).findByDomainAndId(DOMAIN_ID, RESOURCE_ID);
        verify(repository, times(1)).update(any());
        verify(auditService, times(1)).report(any());
        verify(eventService, times(1)).create(any(), any());
    }

    @Test
    public void shouldNotUpdateProtectedResourceWhenIdentifierTakenByAnotherResource() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);
        existingResource.setResourceIdentifiers(List.of(RESOURCE_URI, "https://example2.com"));

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("New Name");
        updateRequest.setResourceIdentifiers(List.of(RESOURCE_URI, "https://example3.com"));
        updateRequest.setFeatures(new ArrayList<>());

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(repository.existsByResourceIdentifiersExcludingId(eq(DOMAIN_ID), any(), eq(RESOURCE_ID)))
                .thenReturn(Single.just(true));

        service.update(createDomain(), RESOURCE_ID, updateRequest, createUser())
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException &&
                        throwable.getMessage().equals("Resource identifier already exists"));

        verify(repository, times(1)).existsByResourceIdentifiersExcludingId(
                eq(DOMAIN_ID),
                argThat(identifiers -> identifiers.contains(RESOURCE_URI) && identifiers.contains("https://example3.com")),
                eq(RESOURCE_ID));
        verify(repository, never()).existsByResourceIdentifiers(any(), any());
        verify(repository, never()).update(any());
    }

    @Test
    public void shouldNotPatchProtectedResourceWhenIdentifierTakenByAnotherResource() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);
        existingResource.setResourceIdentifiers(List.of(RESOURCE_URI, "https://example2.com"));

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setResourceIdentifiers(Optional.of(List.of(RESOURCE_URI, "https://example3.com")));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(repository.existsByResourceIdentifiersExcludingId(eq(DOMAIN_ID), any(), eq(RESOURCE_ID)))
                .thenReturn(Single.just(true));

        service.patch(createDomain(), RESOURCE_ID, patchRequest, createUser())
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException &&
                        throwable.getMessage().equals("Resource identifier already exists"));

        verify(repository, times(1)).existsByResourceIdentifiersExcludingId(
                eq(DOMAIN_ID),
                argThat(identifiers -> identifiers.contains(RESOURCE_URI) && identifiers.contains("https://example3.com")),
                eq(RESOURCE_ID));
        verify(repository, never()).existsByResourceIdentifiers(any(), any());
        verify(repository, never()).update(any());
    }

    @Test
    public void shouldNotPatchProtectedResourceWhenResourceIdentifiersIsNull() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setResourceIdentifiers(Optional.empty());

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));

        service.patch(createDomain(), RESOURCE_ID, patchRequest, createUser())
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException &&
                        throwable.getMessage().equals("Field [resourceIdentifiers] must not be empty"));

        verify(repository, times(1)).findByDomainAndId(DOMAIN_ID, RESOURCE_ID);
        verify(repository, never()).update(any());
        verify(repository, never()).existsByResourceIdentifiersExcludingId(any(), any(), any());
    }

    @Test
    public void shouldNotPatchProtectedResourceWhenResourceIdentifiersIsEmpty() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);

        PatchProtectedResource patchRequest = new PatchProtectedResource();
        patchRequest.setResourceIdentifiers(Optional.of(new ArrayList<>()));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));

        service.patch(createDomain(), RESOURCE_ID, patchRequest, createUser())
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException &&
                        throwable.getMessage().equals("Field [resourceIdentifiers] must not be empty"));

        verify(repository, times(1)).findByDomainAndId(DOMAIN_ID, RESOURCE_ID);
        verify(repository, never()).update(any());
        verify(repository, never()).existsByResourceIdentifiersExcludingId(any(), any(), any());
    }

    @Test
    public void shouldNotUpdateProtectedResourceWhenResourceIdentifiersIsNull() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("New Name");
        updateRequest.setResourceIdentifiers(null);
        updateRequest.setFeatures(new ArrayList<>());

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));

        service.update(createDomain(), RESOURCE_ID, updateRequest, createUser())
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException &&
                        throwable.getMessage().equals("Field [resourceIdentifiers] must not be empty"));

        verify(repository, times(1)).findByDomainAndId(DOMAIN_ID, RESOURCE_ID);
        verify(repository, never()).update(any());
        verify(repository, never()).existsByResourceIdentifiersExcludingId(any(), any(), any());
    }

    @Test
    public void shouldNotUpdateProtectedResourceWhenResourceIdentifiersIsEmpty() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);

        UpdateProtectedResource updateRequest = new UpdateProtectedResource();
        updateRequest.setName("New Name");
        updateRequest.setResourceIdentifiers(new ArrayList<>());
        updateRequest.setFeatures(new ArrayList<>());

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));

        service.update(createDomain(), RESOURCE_ID, updateRequest, createUser())
                .test()
                .assertError(throwable -> throwable instanceof InvalidProtectedResourceException &&
                        throwable.getMessage().equals("Field [resourceIdentifiers] must not be empty"));

        verify(repository, times(1)).findByDomainAndId(DOMAIN_ID, RESOURCE_ID);
        verify(repository, never()).update(any());
        verify(repository, never()).existsByResourceIdentifiersExcludingId(any(), any(), any());
    }

    // ========== SECRET Tests ==========

    @Test
    public void shouldCreateSecret() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);
        existingResource.setClientSecrets(new ArrayList<>());
        
        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(applicationSecretConfig.toSecretSettings()).thenReturn(new ApplicationSecretSettings());
        when(secretService.generateClientSecret(any(), any(), any(), any(), any())).thenReturn(new ClientSecret());
        when(repository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));

        service.createSecret(createDomain(), RESOURCE_ID, "My New Secret", createUser())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(repository, times(1)).update(argThat(res -> res.getClientSecrets().size() == 1));
        verify(auditService, times(1)).report(any());
    }

    @Test
    public void shouldNotCreateSecretWhenLimitReached() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);
        List<ClientSecret> secrets = new ArrayList<>();
        for(int i=0; i<10; i++) { 
            ClientSecret s = new ClientSecret();
            s.setName("secret-" + i);
            secrets.add(s); 
        }
        existingResource.setClientSecrets(secrets);

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));

        service.createSecret(createDomain(), RESOURCE_ID, "New Secret", createUser())
                .test()
                .assertError(TooManyClientSecretsException.class);
    }

    @Test
    public void shouldNotCreateSecretWhenDuplicateName() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);
        ClientSecret existing = new ClientSecret(); existing.setName("Taken Name");
        existingResource.setClientSecrets(new ArrayList<>(List.of(existing)));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));

        service.createSecret(createDomain(), RESOURCE_ID, "Taken Name", createUser())
                .test()
                .assertError(ClientSecretInvalidException.class);
    }

    @Test
    public void shouldRenewSecret() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);
        ClientSecret secret = new ClientSecret();
        secret.setId("secret-1");
        existingResource.setClientSecrets(new ArrayList<>(List.of(secret)));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(applicationSecretConfig.toSecretSettings()).thenReturn(new ApplicationSecretSettings());
        when(repository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));
        // Mock password encoder for renew
        io.gravitee.am.service.authentication.crypto.password.PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(io.gravitee.am.service.authentication.crypto.password.PasswordEncoder.class);
        when(secretService.getOrCreatePasswordEncoder(any())).thenReturn(passwordEncoder);
        when(passwordEncoder.encode(any())).thenReturn("hashed-secret");

        service.renewSecret(createDomain(), RESOURCE_ID, "secret-1", createUser())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(repository, times(1)).update(any());
        verify(auditService, times(1)).report(any());
    }

    @Test
    public void shouldNotRenewSecretWhenNotFound() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);
        existingResource.setClientSecrets(new ArrayList<>());

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));

        service.renewSecret(createDomain(), RESOURCE_ID, "missing-secret", createUser())
                .test()
                .assertError(ClientSecretNotFoundException.class);
    }

    @Test
    public void shouldDeleteSecret() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);
        ClientSecret secret1 = new ClientSecret(); secret1.setId("secret-1"); secret1.setSettingsId("set-1");
        ClientSecret secret2 = new ClientSecret(); secret2.setId("secret-2"); secret2.setSettingsId("set-1");
        existingResource.setClientSecrets(new ArrayList<>(List.of(secret1, secret2)));
        existingResource.setSecretSettings(new ArrayList<>(List.of(new ApplicationSecretSettings())));
        existingResource.getSecretSettings().get(0).setId("set-1");

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(repository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));

        service.deleteSecret(createDomain(), RESOURCE_ID, "secret-1", createUser())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(repository, times(1)).update(argThat(res -> res.getClientSecrets().size() == 1));
        verify(auditService, times(1)).report(any());
    }

    @Test
    public void shouldNotDeleteLastSecret() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);
        ClientSecret secret1 = new ClientSecret(); secret1.setId("secret-1");
        existingResource.setClientSecrets(new ArrayList<>(List.of(secret1)));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));

        service.deleteSecret(createDomain(), RESOURCE_ID, "secret-1", createUser())
                .test()
                .assertError(ClientSecretDeleteException.class);
    }

    @Test
    public void shouldPatchSecretSettings() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);
        existingResource.setSecretSettings(new ArrayList<>());

        ApplicationSecretSettings newSettings = new ApplicationSecretSettings();
        newSettings.setAlgorithm("test");

        PatchProtectedResource patch = new PatchProtectedResource();
        patch.setSecretSettings(Optional.of(List.of(newSettings)));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(repository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));

        service.patch(createDomain(), RESOURCE_ID, patch, createUser())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(repository).update(argThat(res -> {
            return res.getSecretSettings() != null
                && res.getSecretSettings().get(0).getAlgorithm().equals("test");
        }));
    }


    @Test
    public void shouldPatchSecretExpirationSettings() {
        ProtectedResource existingResource = createProtectedResource(RESOURCE_ID, DOMAIN_ID);
        existingResource.setSecretSettings(new ArrayList<>());

        ApplicationSettings newSettings = new ApplicationSettings();
        var secretSettings = new SecretExpirationSettings();
        secretSettings.setEnabled(true);
        secretSettings.setExpiryTimeSeconds(3600L);

        newSettings.setSecretExpirationSettings(secretSettings);

        PatchProtectedResource patch = new PatchProtectedResource();
        patch.setSettings(Optional.of(newSettings));

        when(repository.findByDomainAndId(DOMAIN_ID, RESOURCE_ID)).thenReturn(Maybe.just(existingResource));
        when(repository.update(any())).thenAnswer(a -> Single.just(a.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new Event()));

        service.patch(createDomain(), RESOURCE_ID, patch, createUser())
                .test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        verify(repository).update(argThat(res -> {
            return res.getSettings().getSecretExpirationSettings() != null
                    && res.getSettings().getSecretExpirationSettings().getExpiryTimeSeconds().equals(3600L)
                    && res.getSettings().getSecretExpirationSettings().getEnabled().equals(true);
        }));
    }
}