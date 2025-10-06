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

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

import static io.gravitee.am.identityprovider.api.common.IdentityProviderConfigurationUtils.sanitizeClientAuthCertificate;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.Type;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.PluginConfigurationValidationService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import io.gravitee.am.service.exception.IdentityProviderWithApplicationsException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.AssignPasswordPolicy;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.IdentityProviderAuditBuilder;
import io.gravitee.am.service.validators.idp.DatasourceValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
@Primary
public class IdentityProviderServiceImpl implements IdentityProviderService {

    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(IdentityProviderServiceImpl.class);

    private final IdentityProviderRepository identityProviderRepository;
    private final ApplicationService applicationService;
    private final EventService eventService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final PluginConfigurationValidationService validationService;
    private final DatasourceValidator datasourceValidator;

    public IdentityProviderServiceImpl(@Lazy IdentityProviderRepository identityProviderRepository,
                                       ApplicationService applicationService,
                                       EventService eventService,
                                       AuditService auditService,
                                       ObjectMapper objectMapper,
                                       PluginConfigurationValidationService validationService,
                                       DatasourceValidator datasourceValidator) {
        this.identityProviderRepository = identityProviderRepository;
        this.applicationService = applicationService;
        this.eventService = eventService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.validationService = validationService;
        this.datasourceValidator = datasourceValidator;
    }

    @Override
    public Flowable<IdentityProvider> findAll() {
        LOGGER.debug("Find all identity providers");
        return identityProviderRepository.findAll()
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find all identity providers", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find all identity providers", ex));
                });
    }

    @Override
    public Single<IdentityProvider> findById(ReferenceType referenceType, String referenceId, String id) {
        LOGGER.debug("Find identity provider by ID: {}", id);
        return identityProviderRepository.findById(referenceType, referenceId, id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an identity provider using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an identity provider using its ID: %s", id), ex));
                })
                .switchIfEmpty(Single.error(new IdentityProviderNotFoundException(id)));
    }

    @Override
    public Maybe<IdentityProvider> findById(String id) {
        LOGGER.debug("Find identity provider by ID: {}", id);
        return identityProviderRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find an identity provider using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find an identity provider using its ID: %s", id), ex));
                });
    }

    @Override
    public Flowable<IdentityProvider> findAll(ReferenceType referenceType, String referenceId) {
        LOGGER.debug("Find identity providers by {}: {}", referenceType, referenceId);
        return identityProviderRepository.findAll(referenceType, referenceId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find identity providers by domain", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find identity providers by " + referenceType.name(), ex));
                });
    }

    @Override
    public Flowable<IdentityProvider> findAll(ReferenceType referenceType) {
        LOGGER.debug("Find identity providers by type {}", referenceType);
        return identityProviderRepository.findAll(referenceType);
    }

    @Override
    public Flowable<IdentityProvider> findByDomain(String domain) {
        return findAll(ReferenceType.DOMAIN, domain);
    }

    @Override
    public Single<IdentityProvider> create(ReferenceType referenceType, String referenceId, NewIdentityProvider newIdentityProvider, User principal, boolean system) {
        LOGGER.debug("Create a new identity provider {} for {} {}", newIdentityProvider, referenceType, referenceId);
        return innerCreate(prepareIdp(newIdentityProvider, referenceType, referenceId, system));
    }

    @Override
    public Single<IdentityProvider> create(Domain domain, NewIdentityProvider newIdentityProvider, User principal, boolean system) {
        LOGGER.debug("Create a new identity provider {} for domain {}", newIdentityProvider, domain.getId());

        var identityProvider = prepareIdp(newIdentityProvider, ReferenceType.DOMAIN, domain.getId(), system);
        identityProvider.setDataPlaneId(domain.getDataPlaneId());

        return innerCreate(identityProvider);
    }

    private Single<IdentityProvider> innerCreate(IdentityProvider identityProvider) {
        return datasourceValidator.validate(identityProvider.getConfiguration())
                .andThen(identityProviderRepository.create(identityProvider))
                .flatMap(identityProvider1 -> {
                    // create event for sync process
                    Event event = new Event(Type.IDENTITY_PROVIDER, new Payload(identityProvider1.getId(), identityProvider1.getReferenceType(), identityProvider1.getReferenceId(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(identityProvider1));
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to create an identity provider", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create an identity provider", ex));
                });
    }

    private static IdentityProvider prepareIdp(NewIdentityProvider newIdentityProvider, ReferenceType domain, String domain1, boolean system) {
        var identityProvider = new IdentityProvider();
        identityProvider.setId(newIdentityProvider.getId() == null ? RandomString.generate() : newIdentityProvider.getId());
        identityProvider.setReferenceType(domain);
        identityProvider.setReferenceId(domain1);
        identityProvider.setName(newIdentityProvider.getName());
        identityProvider.setType(newIdentityProvider.getType());
        identityProvider.setSystem(system);
        identityProvider.setConfiguration(sanitizeClientAuthCertificate(newIdentityProvider.getConfiguration()));
        identityProvider.setExternal(newIdentityProvider.isExternal());
        identityProvider.setDomainWhitelist(ofNullable(newIdentityProvider.getDomainWhitelist()).orElse(List.of()));
        identityProvider.setCreatedAt(new Date());
        identityProvider.setUpdatedAt(identityProvider.getCreatedAt());
        return identityProvider;
    }

    @Override
    public Single<IdentityProvider> update(ReferenceType referenceType, String referenceId, String id, UpdateIdentityProvider updateIdentityProvider, User principal, boolean isUpgrader) {
        LOGGER.debug("Update an identity provider {} for {} {}", id, referenceType, referenceId);

        return identityProviderRepository.findById(referenceType, referenceId, id)
                .switchIfEmpty(Single.error(new IdentityProviderNotFoundException(id)))
                .flatMap(oldIdentity -> {
                    IdentityProvider identityToUpdate = new IdentityProvider(oldIdentity);
                    identityToUpdate.setName(updateIdentityProvider.getName());
                    if (!identityToUpdate.isSystem() || isUpgrader) {
                        identityToUpdate.setConfiguration(updateIdentityProvider.getConfiguration());
                    }
                    identityToUpdate.setMappers(updateIdentityProvider.getMappers());
                    identityToUpdate.setRoleMapper(updateIdentityProvider.getRoleMapper());
                    identityToUpdate.setGroupMapper(updateIdentityProvider.getGroupMapper());
                    identityToUpdate.setDomainWhitelist(ofNullable(updateIdentityProvider.getDomainWhitelist()).orElse(List.of()));
                    identityToUpdate.setUpdatedAt(new Date());
                    identityToUpdate.setConfiguration(sanitizeClientAuthCertificate(identityToUpdate.getConfiguration()));

                    // for update validate config against schema here instead of the resource
                    // as idp may be system idp so on the UI config is empty.
                    validationService.validate(identityToUpdate.getType(), identityToUpdate.getConfiguration());

                    return datasourceValidator.validate(identityToUpdate.getConfiguration())
                            .andThen(identityProviderRepository.update(identityToUpdate))
                            .flatMap(identityProvider1 -> {
                                // create event for sync process
                                Event event = new Event(Type.IDENTITY_PROVIDER, new Payload(identityProvider1.getId(), identityProvider1.getReferenceType(), identityProvider1.getReferenceId(), Action.UPDATE));
                                return eventService.create(event).flatMap(__ -> Single.just(identityProvider1));
                            });
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update an identity provider", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update an identity provider", ex));
                });
    }

    @Override
    public Single<IdentityProvider> assignDataPlane(IdentityProvider identityProvider, String dataPlaneId) {
        LOGGER.debug("Assign dataPlaneId {} to identity provider {}", dataPlaneId, identityProvider.getId());


        IdentityProvider identityToUpdate = new IdentityProvider(identityProvider);
        identityToUpdate.setDataPlaneId(dataPlaneId);
        identityToUpdate.setUpdatedAt(new Date());
        identityToUpdate.setConfiguration(sanitizeClientAuthCertificate(identityToUpdate.getConfiguration()));

        // for update validate config against schema here instead of the resource
        // as idp may be system idp so on the UI config is empty.
        validationService.validate(identityToUpdate.getType(), identityToUpdate.getConfiguration());

        return identityProviderRepository.update(identityToUpdate)
                .flatMap(identityProvider1 -> {
                    // create event for sync process
                    Event event = new Event(Type.IDENTITY_PROVIDER, new Payload(identityProvider1.getId(), identityProvider1.getReferenceType(), identityProvider1.getReferenceId(), Action.UPDATE));
                    return eventService.create(event).flatMap(__ -> Single.just(identityProvider1));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to update an identity provider", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update an identity provider", ex));
                })
                .doOnSuccess((updatedIdp) -> auditService.report(AuditBuilder.builder(IdentityProviderAuditBuilder.class).type(EventType.IDENTITY_PROVIDER_UPDATED).oldValue(identityProvider).identityProvider(updatedIdp)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(IdentityProviderAuditBuilder.class).type(EventType.IDENTITY_PROVIDER_UPDATED).reference(new Reference(identityProvider.getReferenceType(), identityProvider.getReferenceId()))
                        .identityProvider(identityProvider).throwable(throwable)));
    }

    @Override
    public Completable delete(ReferenceType referenceType, String referenceId, String identityProviderId, User principal) {
        LOGGER.debug("Delete identity provider {}", identityProviderId);

        return identityProviderRepository.findById(referenceType, referenceId, identityProviderId)
                .switchIfEmpty(Maybe.error(new IdentityProviderNotFoundException(identityProviderId)))
                .flatMapSingle(identityProvider -> applicationService.findByIdentityProvider(identityProviderId).count()
                        .flatMap(applications -> {
                            if (applications > 0) {
                                return Single.error(new IdentityProviderWithApplicationsException());
                            }
                            return Single.just(identityProvider);
                        }))
                .flatMapCompletable(identityProvider -> {

                    // create event for sync process
                    Event event = new Event(Type.IDENTITY_PROVIDER, new Payload(identityProviderId, referenceType, referenceId, Action.DELETE));

                    return Completable.fromSingle(identityProviderRepository.delete(identityProviderId)
                                    .andThen(eventService.create(event)))
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(IdentityProviderAuditBuilder.class).principal(principal).type(EventType.IDENTITY_PROVIDER_DELETED).identityProvider(identityProvider)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(IdentityProviderAuditBuilder.class).principal(principal).type(EventType.IDENTITY_PROVIDER_DELETED).reference(new Reference(referenceType, referenceId))
                                    .identityProvider(identityProvider).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to delete identity provider: {}", identityProviderId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete identity provider: %s", identityProviderId), ex));
                });
    }

    @Override
    public Flowable<IdentityProvider> findWithPasswordPolicy(ReferenceType referenceType, String referenceId, String passwordPolicy) {
        LOGGER.debug("Find identity provider with assigned password policy: {}", passwordPolicy);
        return identityProviderRepository.findAllByPasswordPolicy(referenceType, referenceId, passwordPolicy)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find identity providers by password policy: {}", passwordPolicy, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find identity providers by password policy: %s", passwordPolicy), ex));
                });
    }

    @Override
    public Single<IdentityProvider> updatePasswordPolicy(String domain, String id, AssignPasswordPolicy assignPasswordPolicy) {
        LOGGER.debug("Assigning Password Policy {} to IdentityProvider {} for domain {}", assignPasswordPolicy.getPasswordPolicy(), id, domain);

        return identityProviderRepository.findById(ReferenceType.DOMAIN, domain, id)
                .switchIfEmpty(Single.error(() -> new IdentityProviderNotFoundException(id)))
                .flatMap(oldIdentity -> {
                    IdentityProvider identityToUpdate = new IdentityProvider(oldIdentity);
                    identityToUpdate.setUpdatedAt(new Date());
                    identityToUpdate.setPasswordPolicy(assignPasswordPolicy.getPasswordPolicy());

                    return identityProviderRepository.update(identityToUpdate)
                            .flatMap(ip -> {
                                Event event = new Event(Type.IDENTITY_PROVIDER, new Payload(ip.getId(), ip.getReferenceType(), ip.getReferenceId(), Action.UPDATE));
                                return eventService.create(event).flatMap(__ -> Single.just(ip));
                            });
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }

                    LOGGER.error("An error occurs while trying to assign password policy to identity provider", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to assign password policy to identity provider", ex));
                });
    }

    @Override
    public Flowable<IdentityProvider> findByCertificate(Reference reference, String id) {
        return identityProviderRepository.findAll(reference.type(), reference.id())
                .filter(idp -> {
                    var config = objectMapper.readTree(idp.getConfiguration());
                    return hasEntryReferringToCert(config, id);
                });
    }

    private boolean hasEntryReferringToCert(JsonNode config, String certId) {
        return config.properties()
                .stream()
                .anyMatch(entry -> refersToCert(entry, certId));
    }

    private boolean refersToCert(Map.Entry<String, JsonNode> entry, String certId) {
        if (entry.getValue() instanceof ObjectNode nestedEntry) {
            return hasEntryReferringToCert(nestedEntry, certId);
        }
        if (entry.getValue() instanceof ArrayNode arrayEntry) {
            var elements = arrayEntry.elements();
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(elements, Spliterator.ORDERED), false)
                    .anyMatch(elem -> refersToCert(Map.entry(entry.getKey(), elem), certId));
        }
        if (entry.getValue() instanceof TextNode textEntry) {
            // various IDPs might use different keys for referring to a certificate, so we have to be lax
            var isCertRelatedEntry = entry.getKey().toLowerCase(Locale.ROOT).contains("cert");
            var matchesCertId = textEntry.textValue().equals(certId);
            return isCertRelatedEntry && matchesCertId;
        }
        return false;
    }

}
