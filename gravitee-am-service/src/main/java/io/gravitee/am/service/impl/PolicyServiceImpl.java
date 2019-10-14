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
import io.gravitee.am.model.Policy;
import io.gravitee.am.model.common.event.Action;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.common.event.Type;
import io.gravitee.am.repository.management.api.PolicyRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.PolicyService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.PolicyNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewPolicy;
import io.gravitee.am.service.model.UpdatePolicy;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.PolicyAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class PolicyServiceImpl implements PolicyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyServiceImpl.class);

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EventService eventService;

    @Override
    public Single<List<Policy>> findAll() {
        LOGGER.debug("Find all policies");
        return policyRepository.findAll()
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find all policies", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to find all policies", ex));
                });
    }

    @Override
    public Single<List<Policy>> findByDomain(String domain) {
        LOGGER.debug("Find policies by domain: {}", domain);
        return policyRepository.findByDomain(domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find policies by domain: {}", domain, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find policies by domain: %s", domain), ex));
                });
    }

    @Override
    public Maybe<Policy> findById(String id) {
        LOGGER.debug("Find policy by id: {}", id);
        return policyRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find policy by id: {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(String.format("An error occurs while trying to find policy by id: %s", id), ex));
                });
    }

    @Override
    public Single<Policy> create(String domain, NewPolicy newPolicy, User principal) {
        LOGGER.debug("Create a new policy {} for domain {}", newPolicy, domain);

        Policy policy = new Policy();
        policy.setId(newPolicy.getId() == null ? RandomString.generate() : newPolicy.getId());
        policy.setEnabled(newPolicy.isEnabled());
        policy.setName(newPolicy.getName());
        policy.setType(newPolicy.getType());
        policy.setExtensionPoint(newPolicy.getExtensionPoint());
        policy.setOrder(newPolicy.getOrder());
        policy.setConfiguration(newPolicy.getConfiguration());
        policy.setDomain(domain);
        policy.setCreatedAt(new Date());
        policy.setUpdatedAt(policy.getCreatedAt());

        return policyRepository.create(policy)
                .flatMap(policy1 -> {
                    // create event for sync process
                    Event event = new Event(Type.POLICY, new Payload(policy1.getId(), policy1.getDomain(), Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(policy1));
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to create an identity provider", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to create an identity provider", ex));
                })
                .doOnSuccess(policy1 -> auditService.report(AuditBuilder.builder(PolicyAuditBuilder.class).principal(principal).type(EventType.POLICY_CREATED).policy(policy1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(PolicyAuditBuilder.class).principal(principal).type(EventType.POLICY_CREATED).throwable(throwable)));
    }

    @Override
    public Single<Policy> update(String domain, String id, UpdatePolicy updatePolicy, User principal) {
        LOGGER.debug("Update a policy {} for domain {}", id, domain);

        return policyRepository.findById(id)
                .switchIfEmpty(Maybe.error(new PolicyNotFoundException(id)))
                .flatMapSingle(oldPolicy -> {
                    Policy policyToUpdate = new Policy(oldPolicy);
                    policyToUpdate.setEnabled(updatePolicy.isEnabled());
                    policyToUpdate.setName(updatePolicy.getName());
                    policyToUpdate.setOrder(updatePolicy.getOrder());
                    policyToUpdate.setConfiguration(updatePolicy.getConfiguration());
                    policyToUpdate.setUpdatedAt(new Date());

                    return policyRepository.update(policyToUpdate)
                            .flatMap(policy1 -> {
                                // create event for sync process
                                Event event = new Event(Type.POLICY, new Payload(policy1.getId(), policy1.getDomain(), Action.UPDATE));
                                return eventService.create(event).flatMap(__ -> Single.just(policy1));
                            })
                            .doOnSuccess(policy1 -> auditService.report(AuditBuilder.builder(PolicyAuditBuilder.class).principal(principal).type(EventType.POLICY_UPDATED).oldValue(oldPolicy).policy(policy1)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(PolicyAuditBuilder.class).principal(principal).type(EventType.POLICY_UPDATED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update a policy", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a policy", ex));
                });
    }

    @Override
    public Single<List<Policy>> update(String domain, List<Policy> policies, User principal) {
        LOGGER.debug("Update policies {} for domain {}", policies, domain);

        List<Single<Policy>> singleList = policies.stream().map(p -> policyRepository.update(p)).collect(Collectors.toList());

        return Single.concat(singleList)
                .toList()
                .flatMap(policies1 -> {
                    // create event for sync process
                    Event event = new Event(Type.POLICY, new Payload(null, domain, Action.BULK_UPDATE));
                    return eventService.create(event).flatMap(__ -> Single.just(policies1));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update a policy", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a policy", ex));
                });
    }

    @Override
    public Completable delete(String id, User principal) {
        LOGGER.debug("Delete policy {}", id);
        return policyRepository.findById(id)
                .switchIfEmpty(Maybe.error(new PolicyNotFoundException(id)))
                .flatMapCompletable(policy -> {
                    // create event for sync process
                    Event event = new Event(Type.POLICY, new Payload(id, policy.getDomain(), Action.DELETE));
                    return policyRepository.delete(id)
                            .andThen(eventService.create(event))
                            .toCompletable()
                            .doOnComplete(() -> auditService.report(AuditBuilder.builder(PolicyAuditBuilder.class).principal(principal).type(EventType.POLICY_DELETED).policy(policy)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(PolicyAuditBuilder.class).principal(principal).type(EventType.POLICY_DELETED).throwable(throwable)));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Completable.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to delete policy: {}", id, ex);
                    return Completable.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to delete policy: %s", id), ex));
                });
    }
}
