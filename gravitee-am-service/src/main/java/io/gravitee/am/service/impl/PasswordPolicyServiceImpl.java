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
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.PasswordSettingsAware;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.management.api.PasswordPolicyRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.PasswordPolicyService;
import io.gravitee.am.service.exception.PasswordPolicyNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.AssignPasswordPolicy;
import io.gravitee.am.service.model.NewPasswordPolicy;
import io.gravitee.am.service.model.UpdatePasswordPolicy;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.PasswordPolicyAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;

import static java.util.function.Predicate.not;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author Rafal PODLES (rafal.podles at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class PasswordPolicyServiceImpl implements PasswordPolicyService {

    @Lazy
    @Autowired
    private PasswordPolicyRepository passwordPolicyRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EventService eventService;

    @Autowired
    private IdentityProviderService identityProviderService;

    @Override
    public Flowable<PasswordPolicy> findByDomain(String domain) {
        log.debug("Find password policy by domain: {}", domain);
        return passwordPolicyRepository.findByReference(ReferenceType.DOMAIN, domain)
                .onErrorResumeNext(ex -> {
                    log.error("An error occurs while trying to find password policy by domain", ex);
                    return Flowable.error(new TechnicalManagementException("An error occurs while trying to find password policy by domain", ex));
                });
    }


    @Override
    public Single<PasswordPolicy> create(ReferenceType referenceType, String referenceId, NewPasswordPolicy policy, User principal) {
        log.debug("Create a new password policy named '{}' for {} {}", policy.getName(), referenceType, referenceId);

        final var entity = policy.toPasswordPolicy(referenceType, referenceId);
        // TODO during AM-2893, check if there is existing policies, if not set this one as default

        return passwordPolicyRepository.create(entity)
                .flatMap(createdPolicy -> {
                    Event event = new Event(Type.PASSWORD_POLICY, new Payload(createdPolicy.getId(), referenceType, referenceId, Action.CREATE));
                    return eventService.create(event).flatMap(__ -> Single.just(createdPolicy));
                })
                .doOnSuccess(createdPolicy -> auditService.report(AuditBuilder.builder(PasswordPolicyAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.PASSWORD_POLICY_CREATED)
                        .policy(createdPolicy)))
                .doOnError(error -> auditService.report(AuditBuilder.builder(PasswordPolicyAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.PASSWORD_POLICY_CREATED).throwable(error)));
    }

    @Override
    public Maybe<PasswordPolicy> findByReferenceAndId(ReferenceType referenceType, String referenceId, String policyId) {
        log.debug("Find password policy with id '{}' for {} {}", policyId, referenceType, referenceId);
        return passwordPolicyRepository.findByReferenceAndId(referenceType, referenceId, policyId);
    }

    @Override
    public Single<PasswordPolicy> update(ReferenceType referenceType, String referenceId, String policyId, UpdatePasswordPolicy policy, User principal) {
        log.debug("Update password policy id '{}' for {} {}", policyId, referenceType, referenceId);
        return passwordPolicyRepository.findByReferenceAndId(referenceType, referenceId, policyId)
                .switchIfEmpty(Single.error(() -> new PasswordPolicyNotFoundException(policyId)))
                .flatMap(existingPolicy -> {
                    final var entityToUpdate = policy.toPasswordPolicy(referenceType, referenceId);
                    // override immutable attributes
                    entityToUpdate.setId(existingPolicy.getId());
                    entityToUpdate.setCreatedAt(existingPolicy.getCreatedAt());
                    entityToUpdate.setReferenceType(existingPolicy.getReferenceType());
                    entityToUpdate.setReferenceId(existingPolicy.getReferenceId());

                    return passwordPolicyRepository.update(entityToUpdate)
                            .flatMap(updatedPolicy -> {
                                Event event = new Event(Type.PASSWORD_POLICY, new Payload(updatedPolicy.getId(), referenceType, referenceId, Action.UPDATE));
                                return eventService.create(event).flatMap(__ -> Single.just(updatedPolicy));
                            })
                            .doOnSuccess(updatedPolicy -> auditService.report(AuditBuilder.builder(PasswordPolicyAuditBuilder.class)
                                    .principal(principal)
                                    .type(EventType.PASSWORD_POLICY_UPDATED)
                                    .policy(updatedPolicy)
                                    .oldValue(existingPolicy)));
                })
                .doOnError(error -> auditService.report(AuditBuilder.builder(PasswordPolicyAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.PASSWORD_POLICY_UPDATED).throwable(error)));
    }

    @Override
    public Completable delete(ReferenceType referenceType, String referenceId, String policyId, User principal) {
        log.debug("Delete password policy with id '{}' for {} {}", policyId, referenceType, referenceId);
        return passwordPolicyRepository.findByReferenceAndId(referenceType, referenceId, policyId)
                .flatMapSingle(policy -> resetPolicyOnIdentityProviders(referenceType, referenceId, policyId)
                        .andThen(Completable.defer(() -> passwordPolicyRepository.delete(policy.getId())))
                        .andThen(Single.defer(() -> {
                            Event event = new Event(Type.PASSWORD_POLICY, new Payload(policy.getId(), referenceType, referenceId, Action.DELETE));
                            return eventService.create(event).map(__ -> policy);
                        }
                        )))
                .doOnSuccess(policy -> auditService.report(AuditBuilder.builder(PasswordPolicyAuditBuilder.class)
                                        .principal(principal)
                                        .type(EventType.PASSWORD_POLICY_DELETED)
                                        .oldValue(policy)))
                .doOnError(error -> auditService.report(AuditBuilder.builder(PasswordPolicyAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.PASSWORD_POLICY_DELETED)
                        .throwable(error)))
                .ignoreElement();
                // TODO Add here a reset default policy if we are deleting the default one (AM-3008)
    }

    private Completable resetPolicyOnIdentityProviders(ReferenceType referenceType, String referenceId, String policyId) {
        return identityProviderService.findWithPasswordPolicy(referenceType, referenceId, policyId)
                .flatMapSingle(provider -> identityProviderService.updatePasswordPolicy(referenceId, provider.getId(), new AssignPasswordPolicy()))
                .ignoreElements();
    }

    @Override
    public Maybe<PasswordPolicy> retrievePasswordPolicy(io.gravitee.am.model.User user, PasswordSettingsAware passwordSettingsAware) {
        // TODO this implementation will change once IDP will contains the policyId
        //      as the rule will be to provide the Policy linked to the user.getSource IDP
        //      and if no policy if found fallback to this implementation where app PasswordSettings
        //      will have precedence on the default policy linked to the domain
        return passwordSettingsAware == null ? defaultPasswordPolicy(user) : Optional.of(passwordSettingsAware)
                .map(PasswordSettingsAware::getPasswordSettings)
                .filter(not(PasswordSettings::isInherited))
                .map(PasswordSettings::toPasswordPolicy)
                .map(Maybe::just)
                .orElse(Maybe.empty())
                .switchIfEmpty(defaultPasswordPolicy(user));
    }

    private Maybe<PasswordPolicy> defaultPasswordPolicy(io.gravitee.am.model.User user) {
        return passwordPolicyRepository.findByReference(user.getReferenceType(), user.getReferenceId())
                // TODO replace this sort by a filter on default attribute during AM-2893
                .sorted(Comparator.comparing(PasswordPolicy::getCreatedAt))
                .firstElement();
    }
}
