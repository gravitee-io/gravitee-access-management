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

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.*;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.PageSortRequest;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.permissions.SystemRole;
import io.gravitee.am.repository.management.api.ProtectedResourceRepository;
import io.gravitee.am.service.*;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.ClientSecretInvalidException;
import io.gravitee.am.service.exception.ClientSecretNotFoundException;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.exception.InvalidProtectedResourceException;
import io.gravitee.am.service.exception.InvalidRoleException;
import io.gravitee.am.service.exception.ProtectedResourceNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.TooManyClientSecretsException;
import io.gravitee.am.service.model.NewMcpTool;
import io.gravitee.am.service.model.NewProtectedResource;
import io.gravitee.am.service.model.PatchProtectedResource;
import io.gravitee.am.service.model.UpdateMcpTool;
import io.gravitee.am.service.model.UpdateProtectedResource;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ProtectedResourceAuditBuilder;
import io.gravitee.am.service.spring.application.ApplicationSecretConfig;
import io.reactivex.rxjava3.core.Completable;
import io.gravitee.am.service.exception.ClientSecretDeleteException;
import org.apache.commons.lang3.StringUtils;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.gravitee.am.model.ProtectedResource.Type.valueOf;
import static java.lang.String.format;
import static org.springframework.util.StringUtils.hasLength;
import static org.springframework.util.StringUtils.hasText;

@Component
public class ProtectedResourceServiceImpl implements ProtectedResourceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtectedResourceServiceImpl.class);

    @Value("${applications.secretsMax:10}")
    private int secretsMax;

    @Autowired
    @Lazy
    private ProtectedResourceRepository repository;

    @Autowired
    private ApplicationSecretConfig applicationSecretConfig;

    @Autowired
    private SecretService secretService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private OAuthClientUniquenessValidator oAuthClientUniquenessValidator;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EventService eventService;

    @Autowired
    private ScopeService scopeService;
    @Autowired
    private CertificateService certificateService;

    @Override
    public Maybe<ProtectedResource> findById(String id) {
        LOGGER.debug("Find protected resources by id={}",  id);
        return repository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find protected resource by and id={}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            format("An error occurs while trying to find protected resources by  and id=%s", id), ex));
                });
    }

    @Override
    public Completable delete(Domain domain, String id, ProtectedResource.Type expectedType, User principal) {
        LOGGER.debug("Delete protected resource {} with domain/type validation", id);
        return repository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Maybe.error(new ProtectedResourceNotFoundException(id)))
                .flatMapCompletable(resource -> {
                    if (expectedType != null && resource.getType() != expectedType) {
                        return Completable.error(new ProtectedResourceNotFoundException(id));
                    }
                    Event event = new Event(Type.PROTECTED_RESOURCE, new Payload(resource.getId(), ReferenceType.DOMAIN, resource.getDomainId(), Action.DELETE));
                    // Delete dependencies first to avoid orphaned references if resource deletion fails
                    return membershipService.findByReference(resource.getId(), ReferenceType.PROTECTED_RESOURCE)
                            .flatMapCompletable(membership -> membershipService.delete(membership.getId()))
                            .andThen(repository.delete(id))
                            .andThen(Completable.fromSingle(eventService.create(event, domain)))
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(ProtectedResourceAuditBuilder.class).principal(principal).type(EventType.PROTECTED_RESOURCE_DELETED).protectedResource(resource)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(ProtectedResourceAuditBuilder.class).protectedResource(resource).principal(principal).type(EventType.PROTECTED_RESOURCE_DELETED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete protected resource: {}", id, ex);
                    return Completable.error(new TechnicalManagementException(
                            format("An error occurs while trying to delete protected resource: %s", id), ex));
                });
    }

    @Override
    public Single<ProtectedResourceSecret> create(Domain domain, User principal, NewProtectedResource newProtectedResource) {
        LOGGER.debug("Create ProtectedResource {}", newProtectedResource);
        ProtectedResource toCreate = new ProtectedResource();
        toCreate.setCreatedAt(new Date());
        toCreate.setUpdatedAt(toCreate.getCreatedAt());
        toCreate.setDomainId(domain.getId());
        toCreate.setType(valueOf(newProtectedResource.getType()));

        var secretSettings = this.applicationSecretConfig.toSecretSettings();
        var rawSecret = hasLength(newProtectedResource.getClientSecret()) ? newProtectedResource.getClientSecret() : SecureRandomString.generate();

        toCreate.setId(RandomString.generate());
        toCreate.setName(StringUtils.trimToNull(newProtectedResource.getName()));
        toCreate.setDescription(StringUtils.trimToNull(newProtectedResource.getDescription()));
        toCreate.setResourceIdentifiers(newProtectedResource.getResourceIdentifiers().stream().map(String::trim).map(String::toLowerCase).toList());
        toCreate.setClientId(hasLength(newProtectedResource.getClientId()) ? newProtectedResource.getClientId() : SecureRandomString.generate());

        toCreate.setSecretSettings(new ArrayList<>(List.of(secretSettings)));
        toCreate.setClientSecrets(new ArrayList<>(List.of(buildClientSecret(domain, secretSettings, rawSecret))));
        toCreate.setFeatures(newProtectedResource.getFeatures().stream().map(f -> {
            ProtectedResourceFeature feature = f.asFeature();
            feature.setCreatedAt(toCreate.getCreatedAt());
            return switch (f) {
                case NewMcpTool tool -> new McpTool(feature, tool.getScopes());
                default -> feature;
            };
        }).toList());

        return oAuthClientUniquenessValidator.checkClientIdUniqueness(domain.getId(), toCreate.getClientId())
                .andThen(checkResourceIdentifierUniqueness(domain.getId(), toCreate.getResourceIdentifiers()))
                .andThen(doCreate(toCreate, principal, domain))
                .map(res -> ProtectedResourceSecret.from(res, rawSecret));
    }

    private Completable checkResourceIdentifierUniqueness(String domainId, List<String> resourceIdentifiers) {
        return repository.existsByResourceIdentifiers(domainId, resourceIdentifiers)
                .flatMapCompletable(exists -> {
                    if(exists) {
                        return Completable.error(new InvalidProtectedResourceException("Resource identifier already exists"));
                    } else {
                        return Completable.complete();
                    }
                });
    }

    @Override
    public Single<ProtectedResourcePrimaryData> update(Domain domain, String id, UpdateProtectedResource updateProtectedResource, User principal) {
        LOGGER.debug("Update ProtectedResource {} for domain {}", id, domain.getId());

        return repository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Maybe.error(new ProtectedResourceNotFoundException(id)))
                .toSingle()
                .flatMap(oldProtectedResource -> {
                    // Validate input before building resource (defensive check - @NotEmpty should catch this at API layer)
                    if (updateProtectedResource.getResourceIdentifiers() == null || updateProtectedResource.getResourceIdentifiers().isEmpty()) {
                        return Single.error(new InvalidProtectedResourceException("Field [resourceIdentifiers] must not be empty"));
                    }

                    // Build the updated resource
                    ProtectedResource toUpdate = new ProtectedResource(oldProtectedResource);
                    toUpdate.setName(StringUtils.trimToNull(updateProtectedResource.getName()));
                    toUpdate.setDescription(StringUtils.trimToNull(updateProtectedResource.getDescription()));
                    toUpdate.setResourceIdentifiers(updateProtectedResource.getResourceIdentifiers());

                    // Map features (update has special handling for UpdateMcpTool)
                    // Note: Feature timestamps will be preserved by innerUpdate()
                    toUpdate.setFeatures(updateProtectedResource.getFeatures().stream().map(f -> {
                        ProtectedResourceFeature feature = f.asFeature();
                        return switch (f) {
                            case UpdateMcpTool tool -> new McpTool(feature, tool.getScopes());
                            default -> feature;
                        };
                    }).toList());

                    // Use common innerUpdate method for normalization, timestamp preservation, validation, and update
                    return innerUpdate(domain, id, oldProtectedResource, toUpdate, principal);
                })
                .onErrorResumeNext(ex -> handleUpdateError(id, ex));
    }

    @Override
    public Single<ProtectedResourcePrimaryData> patch(Domain domain, String id, PatchProtectedResource patchProtectedResource, User principal) {
        LOGGER.debug("Patch ProtectedResource {} for domain {}", id, domain.getId());

        return repository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Maybe.error(new ProtectedResourceNotFoundException(id)))
                .toSingle()
                .flatMap(oldProtectedResource -> {
                    // Apply patch
                    ProtectedResource toPatch = patchProtectedResource.patch(oldProtectedResource);

                    // Use common innerUpdate method for normalization, timestamp preservation, validation, and update
                    return innerUpdate(domain, id, oldProtectedResource, toPatch, principal);
                })
                .onErrorResumeNext(ex -> handleUpdateError(id, ex));
    }

    /**
     * Handles error cases for update and patch operations.
     * Preserves business exceptions and wraps technical exceptions.
     *
     * @param id the resource ID (for error messages)
     * @param ex the exception to handle
     * @return Single error with appropriate exception type
     */
    private Single<ProtectedResourcePrimaryData> handleUpdateError(String id, Throwable ex) {
        if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
            return Single.error(ex);
        }
        LOGGER.error("An error occurs while trying to update protected resource {}", id, ex);
        return Single.error(new TechnicalManagementException(
                format("An error occurs while trying to update protected resource %s", id), ex));
    }

    /**
     * Common update logic for both update and patch operations.
     * Handles normalization, timestamp preservation, validation, and persistence.
     *
     * @param domain the domain
     * @param id the resource ID (for error messages)
     * @param oldResource the original resource before update
     * @param resourceToUpdate the prepared resource to update
     * @param principal the user performing the operation
     * @return the updated resource primary data
     */
    private Single<ProtectedResourcePrimaryData> innerUpdate(Domain domain, String id, ProtectedResource oldResource, ProtectedResource resourceToUpdate, User principal) {
        // Set updated timestamp
        resourceToUpdate.setUpdatedAt(new Date());

        // Normalize resource identifiers
        normalizeResourceIdentifiers(resourceToUpdate);

        // Preserve feature timestamps
        preserveFeatureTimestamps(resourceToUpdate, oldResource);

        // Validate and update (processUpdate handles validation and calls doUpdate)
        return processUpdate(domain, id, resourceToUpdate, oldResource, principal);
    }

    /**
     * Normalizes resource identifiers by trimming whitespace and converting to lowercase.
     * This ensures consistent storage format across all resource identifiers.
     *
     * @param resource the resource to normalize
     */
    private void normalizeResourceIdentifiers(ProtectedResource resource) {
        if (resource.getResourceIdentifiers() != null) {
            resource.setResourceIdentifiers(resource.getResourceIdentifiers().stream()
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .toList());
        }
    }

    /**
     * Preserves feature creation timestamps when features are updated.
     * If a feature with the same key exists in the old resource, its original createdAt is preserved.
     * New features get the current update timestamp as their createdAt.
     *
     * @param resource the resource with updated features
     * @param oldResource the original resource to preserve timestamps from
     */
    private void preserveFeatureTimestamps(ProtectedResource resource, ProtectedResource oldResource) {
        if (resource.getFeatures() != null && oldResource.getFeatures() != null) {
            Map<String, Date> oldFeatureCreationDates = oldResource.getFeatures().stream()
                    .collect(Collectors.toMap(ProtectedResourceFeature::getKey, ProtectedResourceFeature::getCreatedAt));
            resource.setFeatures(resource.getFeatures().stream().map(feature -> {
                // Keep original createdAt if feature key matches, otherwise set new date
                Date createdAt = oldFeatureCreationDates.getOrDefault(feature.getKey(), resource.getUpdatedAt());
                feature.setCreatedAt(createdAt);
                feature.setUpdatedAt(resource.getUpdatedAt());
                return feature;
            }).toList());
        }
    }

    /**
     * Common processing method for both update and patch operations.
     * Performs validation and executes the update operation.
     *
     * @param domain the domain
     * @param id the resource ID
     * @param resource the resource to update (already normalized and prepared)
     * @param oldResource the original resource for comparison
     * @param principal the user performing the operation
     * @return the updated resource primary data
     */
    private Single<ProtectedResourcePrimaryData> processUpdate(Domain domain, String id, ProtectedResource resource, ProtectedResource oldResource, User principal) {
        return checkFeatureKeyUniqueness(resource)
                .andThen(validateResourceIdentifiersUniqueness(domain.getId(), id, oldResource.getResourceIdentifiers(), resource.getResourceIdentifiers()))
                .andThen(validateFeatureScopes(domain.getId(), resource))
                .andThen(validateCertificate(resource))
                .andThen(Single.defer(() -> doUpdate(resource, oldResource, principal, domain)))
                .map(ProtectedResourcePrimaryData::of);
    }

    private Completable checkFeatureKeyUniqueness(ProtectedResource resource) {
        if (resource.getFeatures() == null || resource.getFeatures().isEmpty()) {
            return Completable.complete();
        }
        List<String> featureKeys = resource.getFeatures().stream()
                .map(ProtectedResourceFeature::getKey)
                .map(String::trim)
                .toList();
        long uniqueCount = featureKeys.stream().distinct().count();
        if (uniqueCount < featureKeys.size()) {
            return Completable.error(new InvalidProtectedResourceException("Feature key names must be unique"));
        }
        return Completable.complete();
    }

    private Completable validateResourceIdentifiersUniqueness(String domainId, String resourceId, 
                                                               List<String> oldIdentifiers, 
                                                               List<String> newIdentifiers) {
        // A protected resource must have at least one identifier (enforced on create/update)
        if (newIdentifiers == null || newIdentifiers.isEmpty()) {
            return Completable.error(new InvalidProtectedResourceException("Field [resourceIdentifiers] must not be empty"));
        }
        
        // Check if identifiers changed (order-insensitive comparison)
        if (new HashSet<>(oldIdentifiers).equals(new HashSet<>(newIdentifiers))) {
            return Completable.complete();
        }
        
        // Check all new identifiers for uniqueness against other resources (excluding current resource)
        // This ensures we catch cases where an identifier that was previously owned by this resource
        // has been taken by another resource in the meantime
        return repository.existsByResourceIdentifiersExcludingId(domainId, newIdentifiers, resourceId)
                .flatMapCompletable(exists -> {
                    if(exists) {
                        return Completable.error(new InvalidProtectedResourceException("Resource identifier already exists"));
                    } else {
                        return Completable.complete();
                    }
                });
    }

    private Completable validateFeatureScopes(String domainId, ProtectedResource resource) {
        if (resource.getFeatures() == null || resource.getFeatures().isEmpty()) {
            return Completable.complete();
        }
        // Collect all scopes from all features
        List<String> allScopes = resource.getFeatures().stream()
                .filter(f -> f instanceof McpTool)
                .map(f -> (McpTool) f)
                .flatMap(tool -> tool.getScopes() != null ? tool.getScopes().stream() : java.util.stream.Stream.empty())
                .distinct()
                .toList();

        if (allScopes.isEmpty()) {
            return Completable.complete();
        }

        // Validate scopes exist in domain
        // Note: scopeService.validateScope() returns Single.error(InvalidClientMetadataException) when invalid
        // We need to catch it and convert to InvalidProtectedResourceException for consistency
        return scopeService.validateScope(domainId, allScopes)
                .flatMapCompletable(valid -> {
                    if (!valid) {
                        return Completable.error(new InvalidProtectedResourceException("One or more scopes are not valid"));
                    }
                    return Completable.complete();
                })
                .onErrorResumeNext(ex -> {
                    // Convert InvalidClientMetadataException from scope validation to InvalidProtectedResourceException
                    if (ex instanceof InvalidClientMetadataException) {
                        return Completable.error(new InvalidProtectedResourceException(ex.getMessage()));
                    }
                    return Completable.error(ex);
                });
    }

    private Completable validateCertificate(ProtectedResource resource) {
        if (!hasText(resource.getCertificate())) {
            return Completable.complete();
        }

        return certificateService.findById(resource.getCertificate())
                .filter(cert -> cert.getDomain().equals(resource.getDomainId()))
                .switchIfEmpty(Maybe.error(() -> {
                    final var msg = format("Certificate %s not found", resource.getCertificate());
                    LOGGER.error(msg);
                    return new InvalidProtectedResourceException(msg);
                }))
                .ignoreElement();
    }

    private Single<ProtectedResource> doUpdate(ProtectedResource toUpdate, ProtectedResource oldResource, User principal, Domain domain) {
        return repository.update(toUpdate)
                .doOnSuccess(updated -> auditService.report(AuditBuilder.builder(ProtectedResourceAuditBuilder.class)
                        .protectedResource(updated)
                        .principal(principal)
                        .type(EventType.PROTECTED_RESOURCE_UPDATED)
                        .oldValue(oldResource)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ProtectedResourceAuditBuilder.class)
                        .protectedResource(toUpdate)
                        .principal(principal)
                        .type(EventType.PROTECTED_RESOURCE_UPDATED)
                        .throwable(throwable)))
                .flatMap(protectedResource -> {
                    Event event = new Event(Type.PROTECTED_RESOURCE, new Payload(protectedResource.getId(), ReferenceType.DOMAIN, protectedResource.getDomainId(), Action.UPDATE));
                    return eventService.create(event, domain).flatMap(e -> Single.just(protectedResource));
                });
    }

    @Override
    public Single<Page<ProtectedResourcePrimaryData>> findByDomainAndType(String domain, ProtectedResource.Type type, PageSortRequest pageSortRequest) {
        LOGGER.debug("Find protected resources by domainId={}, type={}", domain, type);
        return repository.findByDomainAndType(domain, type, pageSortRequest)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find protected resources by domain {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            format("An error occurs while trying to find protected resources by domain %s", domain), ex));
                });
    }

    @Override
    public Single<Page<ProtectedResourcePrimaryData>> findByDomainAndTypeAndIds(String domain, ProtectedResource.Type type, List<String> ids, PageSortRequest pageSortRequest) {
        LOGGER.debug("Find protected resources by domainId={}, type={}, ids={}", domain, type, ids);
        return repository.findByDomainAndTypeAndIds(domain, type, ids, pageSortRequest)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find protected resources by domainId={} and type={}", domain, type, ex);
                    return Single.error(new TechnicalManagementException(
                            format("An error occurs while trying to find protected resources by domain %s", domain), ex));
                });
    }

    @Override
    public Flowable<ProtectedResource> findAll() {
        LOGGER.debug("Find all protected resources");
        return repository.findAll()
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find all protected resources", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find all protected resources", ex));
                });
    }

    @Override
    public Flowable<ProtectedResource> findByDomain(String domain) {
        LOGGER.debug("Find protected resources by domainId={}", domain);
        return repository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find protected resources by domain {}", domain, ex);
                    return Flowable.error(new TechnicalManagementException(
                            format("An error occurs while trying to find protected resources by domain %s", domain), ex));
                });
    }

    @Override
    public Single<ClientSecret> createSecret(Domain domain, String id, String name, User principal) {
        return repository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Maybe.error(new ProtectedResourceNotFoundException(id)))
                .toSingle()
                .flatMap(protectedResource -> {
                    List<ClientSecret> secrets = protectedResource.getClientSecrets() != null ? protectedResource.getClientSecrets() : new ArrayList<>();
                    String newSecretName = name != null ? name.trim() : name;
                    if (newSecretName != null && secrets.stream().map(ClientSecret::getName).anyMatch(newSecretName::equals)) {
                        return Single.error(() -> new ClientSecretInvalidException(format("Secret with description %s already exists", newSecretName)));
                    }
                    if (secrets.size() >= secretsMax) {
                        return Single.error(() -> new TooManyClientSecretsException(secretsMax));
                    }
                    final var rawSecret = SecureRandomString.generate();
                    final var secretSettings = this.applicationSecretConfig.toSecretSettings();
                    
                    // Add secret settings if not present
                    if (protectedResource.getSecretSettings() == null) {
                        protectedResource.setSecretSettings(new ArrayList<>());
                    }
                    if (!doesResourceReferenceSecretSettings(protectedResource, secretSettings)) {
                        protectedResource.getSecretSettings().add(secretSettings);
                    }

                    ClientSecret clientSecret = this.secretService.generateClientSecret(newSecretName, rawSecret, secretSettings, domain.getSecretExpirationSettings(), getExpirationSettings(protectedResource));
                    secrets.add(clientSecret);
                    protectedResource.setClientSecrets(secrets);

                    return repository.update(protectedResource)
                        .doOnSuccess(updatedResource -> auditService.report(AuditBuilder.builder(ProtectedResourceAuditBuilder.class).principal(principal).type(EventType.PROTECTED_RESOURCE_UPDATED).protectedResource(updatedResource)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(ProtectedResourceAuditBuilder.class).principal(principal).reference(Reference.domain(domain.getId())).type(EventType.PROTECTED_RESOURCE_UPDATED).throwable(throwable)))
                        .flatMap(resource -> {
                            Event event = new Event(Type.PROTECTED_RESOURCE, new Payload(resource.getId(), ReferenceType.DOMAIN, resource.getDomainId(), Action.UPDATE));
                            Event secretEvent = new Event(Type.PROTECTED_RESOURCE_SECRET, new Payload(clientSecret.getId(), ReferenceType.PROTECTED_RESOURCE, resource.getId(), Action.CREATE));
                            return eventService.create(event, domain)
                                    .flatMap(e -> eventService.create(secretEvent, domain))
                                    .flatMap(e -> Single.just(resource));
                        })
                        .map(__ -> {
                            clientSecret.setSecret(rawSecret);
                            return clientSecret;
                        });
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create secret for protected resource {}", id, ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create secret for protected resource", ex));
                });
    }

    private SecretExpirationSettings getExpirationSettings(ProtectedResource protectedResource){
        if(protectedResource.getSettings() != null && protectedResource.getSettings().getSecretExpirationSettings() != null){
            return protectedResource.getSettings().getSecretExpirationSettings();
        }

        return null;
    }

    @Override
    public Single<ClientSecret> renewSecret(Domain domain, String id, String secretId, User principal) {
        return repository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Maybe.error(new ProtectedResourceNotFoundException(id)))
                .toSingle()
                .flatMap(protectedResource -> {
                    Optional<ClientSecret> clientSecretOptional = Optional.ofNullable(protectedResource.getClientSecrets()).orElse(java.util.Collections.emptyList()).stream().filter(clientSecret -> clientSecret.getId().equals(secretId)).findFirst();
                    if (clientSecretOptional.isEmpty()) {
                        return Single.error(new ClientSecretNotFoundException(secretId));
                    }
                    var clientSecret = clientSecretOptional.get();

                    final var secretSettings = this.applicationSecretConfig.toSecretSettings();
                    if (protectedResource.getSecretSettings() == null) {
                        protectedResource.setSecretSettings(new ArrayList<>());
                    }
                    if (!doesResourceReferenceSecretSettings(protectedResource, secretSettings)) {
                        protectedResource.getSecretSettings().add(secretSettings);
                    }

                    final var rawSecret = SecureRandomString.generate();
                    clientSecret.setSecret(secretService.getOrCreatePasswordEncoder(secretSettings).encode(rawSecret));
                    clientSecret.setSettingsId(secretSettings.getId());
                    // Protected resources don't have separate secret expiration settings currently, relying on domain settings or defaults inside service
                    clientSecret.setExpiresAt(secretService.determinateExpireDate(domain.getSecretExpirationSettings(), getExpirationSettings(protectedResource)));

                    return repository.update(protectedResource)
                        .doOnSuccess(updatedResource -> auditService.report(AuditBuilder.builder(ProtectedResourceAuditBuilder.class).principal(principal).type(EventType.PROTECTED_RESOURCE_UPDATED).protectedResource(updatedResource)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(ProtectedResourceAuditBuilder.class).principal(principal).reference(Reference.domain(domain.getId())).type(EventType.PROTECTED_RESOURCE_UPDATED).throwable(throwable)))
                        .flatMap(resource -> {
                            Event event = new Event(Type.PROTECTED_RESOURCE, new Payload(resource.getId(), ReferenceType.DOMAIN, resource.getDomainId(), Action.UPDATE));
                            Event secretEvent = new Event(Type.PROTECTED_RESOURCE_SECRET, new Payload(clientSecret.getId(), ReferenceType.PROTECTED_RESOURCE, resource.getId(), Action.UPDATE));
                            return eventService.create(event, domain)
                                    .flatMap(e -> eventService.create(secretEvent, domain))
                                    .flatMap(e -> Single.just(resource));
                        })
                        .map(__ -> {
                            var secret = Optional.ofNullable(protectedResource.getClientSecrets()).orElse(java.util.Collections.emptyList()).stream().filter(s -> s.getId().equals(secretId)).findFirst().orElse(new ClientSecret());
                            secret.setSecret(rawSecret);
                            return secret;
                        });
                })
                .onErrorResumeNext(ex -> {
                     if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to renew secret for protected resource {}", id, ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to renew secret for protected resource", ex));
                });
    }

    @Override
    public Completable deleteSecret(Domain domain, String id, String secretId, User principal) {
        return repository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Maybe.error(new ProtectedResourceNotFoundException(id)))
                .toSingle()
                .flatMapCompletable(protectedResource -> {
                    if (protectedResource.getClientSecrets().size() <= 1) {
                        return Completable.error(new ClientSecretDeleteException("Cannot remove last secret"));
                    }
                    var secretToRemoveOptional = protectedResource.getClientSecrets().stream().filter(sc -> sc.getId().equals(secretId)).findFirst();
                    if (secretToRemoveOptional.isEmpty()) {
                        return Completable.error(new ClientSecretNotFoundException(secretId));
                    }
                    
                    var secretToRemove = secretToRemoveOptional.get();
                    String secretSettingsId = secretToRemove.getSettingsId();

                    protectedResource.getClientSecrets().removeIf(cs -> cs.getId().equals(secretId));

                    boolean isSecretSettingsStillUsed = protectedResource.getClientSecrets().stream()
                            .anyMatch(cs -> cs.getSettingsId().equals(secretSettingsId));
                    
                    if (!isSecretSettingsStillUsed && protectedResource.getSecretSettings() != null) {
                         protectedResource.getSecretSettings().removeIf(ss -> ss.getId().equals(secretSettingsId));
                    }

                    return repository.update(protectedResource)
                         .doOnSuccess(updatedResource -> auditService.report(AuditBuilder.builder(ProtectedResourceAuditBuilder.class).principal(principal).type(EventType.PROTECTED_RESOURCE_UPDATED).protectedResource(updatedResource)))
                         .doOnError(throwable -> auditService.report(AuditBuilder.builder(ProtectedResourceAuditBuilder.class).principal(principal).reference(Reference.domain(domain.getId())).type(EventType.PROTECTED_RESOURCE_UPDATED).throwable(throwable)))
                         .flatMap(resource -> {
                            Event event = new Event(Type.PROTECTED_RESOURCE, new Payload(resource.getId(), ReferenceType.DOMAIN, resource.getDomainId(), Action.UPDATE));
                            Event secretEvent = new Event(Type.PROTECTED_RESOURCE_SECRET, new Payload(secretToRemove.getId(), ReferenceType.PROTECTED_RESOURCE, resource.getId(), Action.DELETE));
                            return eventService.create(event, domain)
                                    .flatMap(e -> eventService.create(secretEvent, domain))
                                    .flatMap(e -> Single.just(resource));
                         })
                         .ignoreElement();
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete secret for protected resource {}", id, ex);
                    return Completable.error(new TechnicalManagementException("An error occurs while trying to delete secret for protected resource", ex));
                });
    }

    private static boolean doesResourceReferenceSecretSettings(ProtectedResource resource, ApplicationSecretSettings secretSettings) {
        return Optional.ofNullable(resource.getSecretSettings())
                .map(settings -> settings
                        .stream()
                        .anyMatch(conf -> conf.getId() != null && conf.getId().equals(secretSettings.getId()))
                ).orElse(false);
    }

    private Single<ProtectedResource> doCreate(ProtectedResource toCreate, User principal, Domain domain) {
        return repository.create(toCreate)
                .doOnSuccess(created -> auditService.report(AuditBuilder.builder(ProtectedResourceAuditBuilder.class).protectedResource(created).principal(principal).type(EventType.PROTECTED_RESOURCE_CREATED)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(ProtectedResourceAuditBuilder.class).protectedResource(toCreate).principal(principal).type(EventType.PROTECTED_RESOURCE_CREATED).throwable(throwable)))
                .flatMap(protectedResource -> {
                    if (principal == null || principal.getAdditionalInformation() == null || !hasText((String) principal.getAdditionalInformation().get(Claims.ORGANIZATION))) {
                        // There is no principal or we can not find the organization the user is attached to. Can't assign role.
                        return Single.just(protectedResource);
                    }

                    return roleService.findSystemRole(SystemRole.PROTECTED_RESOURCE_PRIMARY_OWNER, ReferenceType.PROTECTED_RESOURCE)
                            .switchIfEmpty(Single.error(new InvalidRoleException("Cannot assign owner to the protected resource, owner role does not exist")))
                            .flatMap(role -> {
                                Membership membership = new Membership();
                                membership.setDomain(protectedResource.getDomainId());
                                membership.setMemberId(principal.getId());
                                membership.setMemberType(MemberType.USER);
                                membership.setReferenceId(protectedResource.getId());
                                membership.setReferenceType(ReferenceType.PROTECTED_RESOURCE);
                                membership.setRoleId(role.getId());
                                return membershipService.addOrUpdate((String) principal.getAdditionalInformation().get(Claims.ORGANIZATION), membership)
                                        .map(updatedMembership -> protectedResource);
                            });
                })
                .flatMap(protectedResource -> {
                    Event event = new Event(Type.PROTECTED_RESOURCE, new Payload(protectedResource.getId(), ReferenceType.DOMAIN, protectedResource.getDomainId(), Action.CREATE));
                    return eventService.create(event, domain).flatMap(e -> Single.just(protectedResource));
                });
    }

    private ClientSecret buildClientSecret(Domain domain, ApplicationSecretSettings secretSettings, String rawSecret) {
        return this.secretService.generateClientSecret("Default", rawSecret, secretSettings, domain.getSecretExpirationSettings(), null);
    }
}
