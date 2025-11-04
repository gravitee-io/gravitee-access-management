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
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.gravitee.am.service.exception.InvalidProtectedResourceException;
import io.gravitee.am.service.exception.InvalidRoleException;
import io.gravitee.am.service.exception.ProtectedResourceNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewMcpTool;
import io.gravitee.am.service.model.NewProtectedResource;
import io.gravitee.am.service.model.PatchProtectedResource;
import io.gravitee.am.service.model.UpdateMcpTool;
import io.gravitee.am.service.model.UpdateProtectedResource;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.ProtectedResourceAuditBuilder;
import io.gravitee.am.service.spring.application.ApplicationSecretConfig;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.gravitee.am.model.ProtectedResource.Type.valueOf;
import static org.springframework.util.StringUtils.hasLength;
import static org.springframework.util.StringUtils.hasText;

@Component
public class ProtectedResourceServiceImpl implements ProtectedResourceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtectedResourceServiceImpl.class);

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

    @Override
    public Maybe<ProtectedResource> findById(String id) {
        LOGGER.debug("Find protected resources by id={}",  id);
        return repository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find protected resource by and id={}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find protected resources by  and id=%s", id), ex));
                });
    }

    @Override
    public Completable delete(Domain domain, String id, ProtectedResource.Type expectedType, User principal) {
        LOGGER.debug("Delete protected resource {} with domain/type validation", id);
        return repository.findById(id)
                .switchIfEmpty(Maybe.error(new ProtectedResourceNotFoundException(id)))
                .flatMapCompletable(resource -> {
                    if (!resource.getDomainId().equals(domain.getId()) || (expectedType != null && resource.getType() != expectedType)) {
                        return Completable.error(new ProtectedResourceNotFoundException(id));
                    }
                    Event event = new Event(Type.PROTECTED_RESOURCE, new Payload(resource.getId(), ReferenceType.DOMAIN, resource.getDomainId(), Action.DELETE));
                    // Delete dependencies first to avoid orphaned references if resource deletion fails
                    return membershipService.findByReference(resource.getId(), ReferenceType.APPLICATION)
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
                            String.format("An error occurs while trying to delete protected resource: %s", id), ex));
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
        toCreate.setName(newProtectedResource.getName());
        toCreate.setDescription(newProtectedResource.getDescription());
        toCreate.setResourceIdentifiers(newProtectedResource.getResourceIdentifiers().stream().map(String::trim).map(String::toLowerCase).toList());
        toCreate.setClientId(hasLength(newProtectedResource.getClientId()) ? newProtectedResource.getClientId() : SecureRandomString.generate());

        toCreate.setSecretSettings(List.of(secretSettings));
        toCreate.setClientSecrets(List.of(buildClientSecret(domain, secretSettings, rawSecret)));
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

        return repository.findById(id)
                .switchIfEmpty(Maybe.error(new ProtectedResourceNotFoundException(id)))
                .toSingle()
                .flatMap(oldProtectedResource -> {
                    // Verify resource belongs to the domain
                    if (!oldProtectedResource.getDomainId().equals(domain.getId())) {
                        return Single.error(new ProtectedResourceNotFoundException(id));
                    }

                    // Validate input before building resource (defensive check - @NotEmpty should catch this at API layer)
                    if (updateProtectedResource.getResourceIdentifiers() == null || updateProtectedResource.getResourceIdentifiers().isEmpty()) {
                        return Single.error(new InvalidProtectedResourceException("Field [resourceIdentifiers] must not be empty"));
                    }

                    // Build the updated resource
                    ProtectedResource toUpdate = new ProtectedResource(oldProtectedResource);
                    toUpdate.setName(updateProtectedResource.getName());
                    toUpdate.setDescription(updateProtectedResource.getDescription());
                    toUpdate.setResourceIdentifiers(updateProtectedResource.getResourceIdentifiers().stream()
                            .map(String::trim)
                            .map(String::toLowerCase)
                            .toList());
                    toUpdate.setUpdatedAt(new Date());

                    // Map features

                    Map<String, Date> oldFeatureCreationDates = oldProtectedResource.getFeatures().stream()
                            .collect(Collectors.toMap(ProtectedResourceFeature::getKey, ProtectedResourceFeature::getCreatedAt));
                    toUpdate.setFeatures(updateProtectedResource.getFeatures().stream().map(f -> {
                        ProtectedResourceFeature feature = f.asFeature();
                        // Keep original createdAt if feature key matches, otherwise set new date
                        Date createdAt = oldFeatureCreationDates.getOrDefault(feature.getKey(), toUpdate.getUpdatedAt());
                        feature.setCreatedAt(createdAt);
                        feature.setUpdatedAt(toUpdate.getUpdatedAt());
                        return switch (f) {
                            case UpdateMcpTool tool -> new McpTool(feature, tool.getScopes());
                            default -> feature;
                        };
                    }).toList());

                    // Validations
                    return checkFeatureKeyUniqueness(toUpdate)
                            .andThen(validateResourceIdentifiersUniqueness(domain.getId(), id, oldProtectedResource.getResourceIdentifiers(), toUpdate.getResourceIdentifiers()))
                            .andThen(validateFeatureScopes(domain.getId(), toUpdate))
                            .andThen(Single.defer(() -> doUpdate(toUpdate, oldProtectedResource, principal, domain)))
                            .map(ProtectedResourcePrimaryData::of);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update protected resource {}", id, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to update protected resource %s", id), ex));
                });
    }

    @Override
    public Single<ProtectedResourcePrimaryData> patch(Domain domain, String id, PatchProtectedResource patchProtectedResource, User principal) {
        LOGGER.debug("Patch ProtectedResource {} for domain {}", id, domain.getId());

        return repository.findById(id)
                .switchIfEmpty(Maybe.error(new ProtectedResourceNotFoundException(id)))
                .toSingle()
                .flatMap(oldProtectedResource -> {
                    // Verify resource belongs to the domain
                    if (!oldProtectedResource.getDomainId().equals(domain.getId())) {
                        return Single.error(new ProtectedResourceNotFoundException(id));
                    }

                    // Apply patch
                    ProtectedResource toPatch = patchProtectedResource.patch(oldProtectedResource);
                    toPatch.setUpdatedAt(new Date());

                    // Normalize resourceIdentifiers if they were updated (trim and lowercase)
                    if (toPatch.getResourceIdentifiers() != null) {
                        toPatch.setResourceIdentifiers(toPatch.getResourceIdentifiers().stream()
                                .map(String::trim)
                                .map(String::toLowerCase)
                                .toList());
                    }

                    // Handle features with createdAt preservation if features were updated
                    if (toPatch.getFeatures() != null && oldProtectedResource.getFeatures() != null) {
                        Map<String, Date> oldFeatureCreationDates = oldProtectedResource.getFeatures().stream()
                                .collect(Collectors.toMap(ProtectedResourceFeature::getKey, ProtectedResourceFeature::getCreatedAt));
                        toPatch.setFeatures(toPatch.getFeatures().stream().map(feature -> {
                            // Keep original createdAt if feature key matches, otherwise set new date
                            Date createdAt = oldFeatureCreationDates.getOrDefault(feature.getKey(), toPatch.getUpdatedAt());
                            feature.setCreatedAt(createdAt);
                            feature.setUpdatedAt(toPatch.getUpdatedAt());
                            return feature;
                        }).toList());
                    }

                    // Validations
                    return checkFeatureKeyUniqueness(toPatch)
                            .andThen(validateResourceIdentifiersUniqueness(domain.getId(), id, oldProtectedResource.getResourceIdentifiers(), toPatch.getResourceIdentifiers()))
                            .andThen(validateFeatureScopes(domain.getId(), toPatch))
                            .andThen(Single.defer(() -> doUpdate(toPatch, oldProtectedResource, principal, domain)))
                            .map(ProtectedResourcePrimaryData::of);
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof OAuth2Exception) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to patch protected resource {}", id, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to patch protected resource %s", id), ex));
                });
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
                            String.format("An error occurs while trying to find protected resources by domain %s", domain), ex));
                });
    }

    @Override
    public Single<Page<ProtectedResourcePrimaryData>> findByDomainAndTypeAndIds(String domain, ProtectedResource.Type type, List<String> ids, PageSortRequest pageSortRequest) {
        LOGGER.debug("Find protected resources by domainId={}, type={}, ids={}", domain, type, ids);
        return repository.findByDomainAndTypeAndIds(domain, type, ids, pageSortRequest)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find protected resources by domainId={} and type={}", domain, type, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find protected resources by domain %s", domain), ex));
                });
    }

    @Override
    public Flowable<ProtectedResource> findByDomain(String domain) {
        LOGGER.debug("Find protected resources by domainId={}", domain);
        return repository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find protected resources by domain {}", domain, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find protected resources by domain %s", domain), ex));
                });
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

                    return roleService.findSystemRole(SystemRole.APPLICATION_PRIMARY_OWNER, ReferenceType.APPLICATION)
                            .switchIfEmpty(Single.error(new InvalidRoleException("Cannot assign owner to the application, owner role does not exist")))
                            .flatMap(role -> {
                                Membership membership = new Membership();
                                membership.setDomain(protectedResource.getDomainId());
                                membership.setMemberId(principal.getId());
                                membership.setMemberType(MemberType.USER);
                                membership.setReferenceId(protectedResource.getId());
                                membership.setReferenceType(ReferenceType.APPLICATION);
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
