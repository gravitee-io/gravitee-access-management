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
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PolicySet;
import io.gravitee.am.model.PolicySetVersion;
import io.gravitee.am.repository.management.api.PolicySetRepository;
import io.gravitee.am.repository.management.api.PolicySetVersionRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.PolicySetService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.PolicySetNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewPolicySet;
import io.gravitee.am.service.model.UpdatePolicySet;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.PolicySetAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author GraviteeSource Team
 */
@Component
@Primary
public class PolicySetServiceImpl implements PolicySetService {

    private final Logger LOGGER = LoggerFactory.getLogger(PolicySetServiceImpl.class);

    private final PolicySetRepository policySetRepository;
    private final PolicySetVersionRepository policySetVersionRepository;
    private final AuditService auditService;

    public PolicySetServiceImpl(@Lazy PolicySetRepository policySetRepository,
                                @Lazy PolicySetVersionRepository policySetVersionRepository,
                                AuditService auditService) {
        this.policySetRepository = policySetRepository;
        this.policySetVersionRepository = policySetVersionRepository;
        this.auditService = auditService;
    }

    @Override
    public Flowable<PolicySet> findByDomain(String domainId) {
        LOGGER.debug("Find policy sets by domain: {}", domainId);
        return policySetRepository.findByDomain(domainId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find policy sets by domain: {}", domainId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find policy sets by domain: %s", domainId), ex));
                });
    }

    @Override
    public Maybe<PolicySet> findById(String id) {
        LOGGER.debug("Find policy set by ID: {}", id);
        return policySetRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a policy set using its ID: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a policy set using its ID: %s", id), ex));
                });
    }

    @Override
    public Maybe<PolicySet> findByDomainAndId(String domainId, String id) {
        LOGGER.debug("Find policy set by domain {} and ID: {}", domainId, id);
        return policySetRepository.findByDomainAndId(domainId, id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a policy set by domain {} and ID: {}", domainId, id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a policy set by domain %s and ID: %s", domainId, id), ex));
                });
    }

    @Override
    public Single<PolicySet> create(Domain domain, NewPolicySet request, User principal) {
        LOGGER.debug("Create a new policy set {} for domain {}", request, domain.getId());

        PolicySet policySet = new PolicySet();
        policySet.setId(RandomString.generate());
        policySet.setDomainId(domain.getId());
        policySet.setName(request.getName());
        policySet.setLatestVersion(1);
        policySet.setCreatedAt(new Date());
        policySet.setUpdatedAt(policySet.getCreatedAt());

        return policySetRepository.create(policySet)
                .flatMap(created -> {
                    PolicySetVersion version = new PolicySetVersion();
                    version.setId(RandomString.generate());
                    version.setPolicySetId(created.getId());
                    version.setVersion(1);
                    version.setContent(request.getContent());
                    version.setCommitMessage(request.getCommitMessage());
                    version.setCreatedAt(created.getCreatedAt());
                    version.setCreatedBy(principal != null ? principal.getId() : null);

                    return policySetVersionRepository.create(version)
                            .map(v -> created);
                })
                .doOnSuccess(ps -> auditService.report(AuditBuilder.builder(PolicySetAuditBuilder.class).principal(principal).type(EventType.POLICY_SET_CREATED).policySet(ps)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(PolicySetAuditBuilder.class).principal(principal).type(EventType.POLICY_SET_CREATED).throwable(throwable)))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to create a policy set", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create a policy set", ex));
                });
    }

    @Override
    public Single<PolicySet> update(Domain domain, String id, UpdatePolicySet request, User principal) {
        LOGGER.debug("Update policy set {} for domain {}", id, domain.getId());

        return policySetRepository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Single.error(new PolicySetNotFoundException(id)))
                .flatMap(existing -> {
                    PolicySet oldValue = new PolicySet();
                    oldValue.setId(existing.getId());
                    oldValue.setDomainId(existing.getDomainId());
                    oldValue.setName(existing.getName());
                    oldValue.setLatestVersion(existing.getLatestVersion());

                    if (request.getName() != null) {
                        existing.setName(request.getName());
                    }

                    int newVersion = existing.getLatestVersion() + 1;
                    existing.setLatestVersion(newVersion);
                    existing.setUpdatedAt(new Date());

                    return policySetRepository.update(existing)
                            .flatMap(updated -> {
                                String content = request.getContent();
                                if (content == null) {
                                    return policySetVersionRepository.findLatestByPolicySetId(id)
                                            .map(PolicySetVersion::getContent)
                                            .defaultIfEmpty("")
                                            .flatMap(prevContent -> createVersionRecord(updated, newVersion, prevContent, request.getCommitMessage(), principal));
                                }
                                return createVersionRecord(updated, newVersion, content, request.getCommitMessage(), principal);
                            })
                            .doOnSuccess(ps -> auditService.report(AuditBuilder.builder(PolicySetAuditBuilder.class).principal(principal).type(EventType.POLICY_SET_UPDATED).oldValue(oldValue).policySet(ps)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(PolicySetAuditBuilder.class).principal(principal).type(EventType.POLICY_SET_UPDATED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update a policy set", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a policy set", ex));
                });
    }

    private Single<PolicySet> createVersionRecord(PolicySet policySet, int versionNum, String content, String commitMessage, User principal) {
        PolicySetVersion version = new PolicySetVersion();
        version.setId(RandomString.generate());
        version.setPolicySetId(policySet.getId());
        version.setVersion(versionNum);
        version.setContent(content);
        version.setCommitMessage(commitMessage);
        version.setCreatedAt(new Date());
        version.setCreatedBy(principal != null ? principal.getId() : null);

        return policySetVersionRepository.create(version)
                .map(v -> policySet);
    }

    @Override
    public Completable delete(Domain domain, String id, User principal) {
        LOGGER.debug("Delete policy set {}", id);

        return policySetRepository.findByDomainAndId(domain.getId(), id)
                .switchIfEmpty(Maybe.error(new PolicySetNotFoundException(id)))
                .flatMapCompletable(ps -> policySetVersionRepository.deleteByPolicySetId(id)
                        .andThen(policySetRepository.delete(id))
                        .doOnComplete(() -> auditService.report(AuditBuilder.builder(PolicySetAuditBuilder.class).principal(principal).type(EventType.POLICY_SET_DELETED).policySet(ps)))
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(PolicySetAuditBuilder.class).principal(principal).type(EventType.POLICY_SET_DELETED).throwable(throwable))))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete policy set: {}", id, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete policy set: %s", id), ex));
                });
    }

    @Override
    public Completable deleteByDomain(String domainId) {
        LOGGER.debug("Delete policy sets by domain {}", domainId);
        return policySetRepository.findByDomain(domainId)
                .flatMapCompletable(ps -> policySetVersionRepository.deleteByPolicySetId(ps.getId()))
                .andThen(policySetRepository.deleteByDomain(domainId))
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to delete policy sets for domain: {}", domainId, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete policy sets for domain: %s", domainId), ex));
                });
    }

    @Override
    public Flowable<PolicySetVersion> getVersions(String policySetId) {
        LOGGER.debug("Get versions for policy set: {}", policySetId);
        return policySetVersionRepository.findByPolicySetId(policySetId)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to get versions for policy set: {}", policySetId, ex);
                    return Flowable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to get versions for policy set: %s", policySetId), ex));
                });
    }

    @Override
    public Maybe<PolicySetVersion> getVersion(String policySetId, int version) {
        LOGGER.debug("Get version {} for policy set: {}", version, policySetId);
        return policySetVersionRepository.findByPolicySetIdAndVersion(policySetId, version)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to get version {} for policy set: {}", version, policySetId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to get version %d for policy set: %s", version, policySetId), ex));
                });
    }

    @Override
    public Single<PolicySet> restoreVersion(Domain domain, String id, int version, User principal) {
        LOGGER.debug("Restore policy set {} to version {}", id, version);

        return policySetVersionRepository.findByPolicySetIdAndVersion(id, version)
                .switchIfEmpty(Single.error(new TechnicalManagementException(
                        String.format("Version %d not found for policy set %s", version, id))))
                .flatMap(versionRecord -> {
                    UpdatePolicySet updateRequest = new UpdatePolicySet();
                    updateRequest.setContent(versionRecord.getContent());
                    updateRequest.setCommitMessage("Restore to version " + version);
                    return update(domain, id, updateRequest, principal);
                });
    }
}
