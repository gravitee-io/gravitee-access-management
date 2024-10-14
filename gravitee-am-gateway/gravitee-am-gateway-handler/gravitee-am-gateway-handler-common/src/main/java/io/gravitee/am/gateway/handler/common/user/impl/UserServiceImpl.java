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
package io.gravitee.am.gateway.handler.common.user.impl;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.exception.mfa.InvalidFactorAttributeException;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.common.user.UserStore;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.repository.management.api.CommonUserRepository.UpdateActions;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    protected io.gravitee.am.service.UserService userService;

    @Autowired
    protected UserStore userStore;

    @Autowired
    private AuditService auditService;

    @Override
    public Maybe<User> findById(String id) {
        return userStore.get(id).switchIfEmpty(Maybe.defer(() -> userService.findById(id)));
    }

    @Override
    public Maybe<User> findByDomainAndExternalIdAndSource(String domain, String externalId, String source) {
        return userService.findByExternalIdAndSource(ReferenceType.DOMAIN, domain, externalId, source);
    }

    @Override
    public Maybe<User> findByDomainAndUsernameAndSource(String domain, String username, String source) {
        return userService.findByDomainAndUsernameAndSource(domain, username, source);
    }

    @Override
    public Maybe<User> findByDomainAndUsernameAndSource(String domain, String username, String source, boolean includeLinkedIdentities) {
        return userService.findByUsernameAndSource(ReferenceType.DOMAIN, domain, username, source, includeLinkedIdentities);
    }

    @Override
    public Single<List<User>> findByDomainAndCriteria(String domain, FilterCriteria criteria) {
        return userService.search(ReferenceType.DOMAIN, domain, criteria).toList();
    }

    @Override
    public Single<User> create(User user) {
        return userService.create(user)
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).type(EventType.USER_CREATED).user(user1)))
                .doOnError(err -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).type(EventType.USER_CREATED).reference(new Reference(user.getReferenceType(), user.getReferenceId())).throwable(err)))
                .flatMap(persistedUser -> userStore.add(persistedUser).switchIfEmpty(Single.just(persistedUser)));
    }

    @Override
    public Single<User> update(User user, UpdateActions updateActions) {
        return userService.update(user, updateActions)
                .flatMap(persistedUser -> userStore.add(persistedUser).switchIfEmpty(Single.just(persistedUser)));
    }

    @Override
    public Single<User> enhance(User user) {
        return userService.enhance(user);
    }

    @Override
    public Single<User> upsertFactor(String userId, EnrolledFactor enrolledFactor, io.gravitee.am.identityprovider.api.User principal) {
        return findById(userId)
                .switchIfEmpty(Single.error(new UserNotFoundException(userId)))
                .flatMap(oldUser -> {
                    User user = new User(oldUser);
                    try {
                        refreshFactors(enrolledFactor, user);
                        updateUserProfileWithFactorPhoneNumber(enrolledFactor, user);
                        updateUserProfileWithFactorEmail(enrolledFactor, user);
                    } catch (InvalidFactorAttributeException e) {
                        return Single.error(e);
                    }

                    if (enrolledFactor.getStatus() == FactorStatus.ACTIVATED) {
                        // reset the MFA skip date if the factor is active
                        // this is to force the MFA challenge when the user
                        // skip enrollment during authentication phase
                        // but enroll using the self account API
                        user.setMfaEnrollmentSkippedAt(null);
                    }

                    return update(user) // update is managing the UserStore usage
                            .doOnSuccess(user1 -> log.debug("Factor {} registered for user {}", enrolledFactor.getFactorId(), user1.getId()))
                            .doOnSuccess(user1 -> {
                                if (needToAuditUserFactorsOperation(user1, oldUser)) {
                                    // remove sensitive data about factors
                                    removeSensitiveFactorsData(user1.getFactors());
                                    removeSensitiveFactorsData(oldUser.getFactors());
                                    auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).user(user1).oldValue(oldUser));
                                }
                            })
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).reference(new Reference(user.getReferenceType(), user.getReferenceId())).throwable(throwable)));
                });
    }

    private static void refreshFactors(EnrolledFactor enrolledFactor, User user) {
        List<EnrolledFactor> enrolledFactors = user.getFactors();
        if (enrolledFactors == null || enrolledFactors.isEmpty()) {
            enrolledFactors = Collections.singletonList(enrolledFactor);
        } else {
            // if current factor is primary, set the others to secondary
            if (Boolean.TRUE.equals(enrolledFactor.isPrimary())) {
                enrolledFactors.forEach(e -> e.setPrimary(false));
            }
            // if the Factor already exists, update the target and the security value
            var foundFactor = enrolledFactors.stream()
                    .filter(existingFactor -> existingFactor.getFactorId().equals(enrolledFactor.getFactorId()))
                    .findFirst();
            if (foundFactor.isPresent()) {
                var factorToUpdate = new EnrolledFactor(foundFactor.get());
                factorToUpdate.setStatus(ofNullable(enrolledFactor.getStatus()).orElse(factorToUpdate.getStatus()));
                factorToUpdate.setChannel(ofNullable(enrolledFactor.getChannel()).orElse(factorToUpdate.getChannel()));
                factorToUpdate.setSecurity(ofNullable(enrolledFactor.getSecurity()).orElse(factorToUpdate.getSecurity()));
                factorToUpdate.setPrimary(ofNullable(enrolledFactor.isPrimary()).orElse(factorToUpdate.isPrimary()));
                // update the factor
                enrolledFactors.removeIf(ef -> factorToUpdate.getFactorId().equals(ef.getFactorId()));
                enrolledFactors.add(factorToUpdate);
            } else {
                enrolledFactors.add(enrolledFactor);
            }
        }
        user.setFactors(enrolledFactors);
    }

    private static void updateUserProfileWithFactorEmail(EnrolledFactor enrolledFactor, User user) {
        if (enrolledFactor.getChannel() != null && EnrolledFactorChannel.Type.EMAIL.equals(enrolledFactor.getChannel().getType())) {
            // MFA EMAIL currently used, preserve the email into the user profile if not yet present
            String email = user.getEmail();
            String enrolledEmail = enrolledFactor.getChannel().getTarget();
            if (isNullOrEmpty(enrolledEmail)) {
                throw new InvalidFactorAttributeException("Email address required to enroll Email factor");
            }
            if (email == null) {
                user.setEmail(enrolledEmail);
            } else if (!email.equals(enrolledEmail)){
                // an email is already present but doesn't match the one provided as security factor
                // register this email in the user profile.
                List<Attribute> emails = user.getEmails();
                if (emails == null) {
                    emails = new ArrayList<>();
                    user.setEmails(emails);
                }
                if (emails.stream().noneMatch(p -> p.getValue().equals(enrolledEmail))) {
                    Attribute additionalEmail = new Attribute();
                    additionalEmail.setPrimary(false);
                    additionalEmail.setValue(enrolledEmail);
                    emails.add(additionalEmail);
                }
            }
        }
    }

    private static void updateUserProfileWithFactorPhoneNumber(EnrolledFactor enrolledFactor, User user) {
        if (enrolledFactor.getChannel() != null && EnrolledFactorChannel.Type.SMS.equals(enrolledFactor.getChannel().getType())) {
            // MFA SMS currently used, preserve the phone number into the user profile if not yet present
            List<Attribute> phoneNumbers = user.getPhoneNumbers();
            if (phoneNumbers == null) {
                phoneNumbers = new ArrayList<>();
                user.setPhoneNumbers(phoneNumbers);
            }
            String enrolledPhoneNumber = enrolledFactor.getChannel().getTarget();
            if (isNullOrEmpty(enrolledPhoneNumber)) {
                throw new InvalidFactorAttributeException("Phone Number required to enroll SMS factor");
            }
            if (phoneNumbers.stream().noneMatch(p -> p.getValue().equals(enrolledPhoneNumber))) {
                Attribute newPhoneNumber = new Attribute();
                newPhoneNumber.setType("mobile");
                newPhoneNumber.setPrimary(phoneNumbers.isEmpty());
                newPhoneNumber.setValue(enrolledPhoneNumber);
                phoneNumbers.add(newPhoneNumber);
            }
        }
    }

    private void removeSensitiveFactorsData(List<EnrolledFactor> enrolledFactors) {
        if (enrolledFactors == null) {
            return;
        }
        enrolledFactors
                .forEach(enrolledFactor -> enrolledFactor.setSecurity(null));
    }

    private boolean needToAuditUserFactorsOperation(User newUser, User oldUser) {
        final List<EnrolledFactor> newEnrolledFactors = newUser.getFactors() != null ? newUser.getFactors() : Collections.emptyList();
        final List<EnrolledFactor> oldEnrolledFactors = oldUser.getFactors() != null ? oldUser.getFactors() : Collections.emptyList();

        // if new enrolled factor, create an audit
        if (newEnrolledFactors.size() != oldEnrolledFactors.size()) {
            return true;
        }

        // if enrolled factors not match, create an audit
        return newEnrolledFactors
                .stream()
                .anyMatch(newEnrolledFactor -> oldEnrolledFactors
                        .stream()
                        .anyMatch(oldEnrolledFactor -> {
                            if (!newEnrolledFactor.getFactorId().equals(oldEnrolledFactor.getFactorId())) {
                                return false;
                            }
                            // check if enrolled factor was in pending activation
                            if (oldEnrolledFactor.getStatus().equals(FactorStatus.PENDING_ACTIVATION)) {
                                return true;
                            }
                            if (oldEnrolledFactor.getChannel() != null) {
                                // check if email has changed
                                if (EnrolledFactorChannel.Type.EMAIL.equals(oldEnrolledFactor.getChannel().getType())) {
                                    return emailInformationHasChanged(newUser, oldUser);
                                }
                                // check if phoneNumber has changed
                                if (EnrolledFactorChannel.Type.SMS.equals(oldEnrolledFactor.getChannel().getType())) {
                                    return phoneNumberInformationHasChanged(newUser, oldUser);
                                }
                            }
                            return false;
                        }));
    }

    private boolean emailInformationHasChanged(User newUser, User oldUser) {
        if (oldUser.getEmail() == null
                && newUser.getEmail() != null) {
            return true;
        }

        // if email list not match, create an audit
        final List<Attribute> newEmails = ofNullable(newUser.getEmails()).orElse(Collections.emptyList());
        final List<Attribute> oldEmails = ofNullable(oldUser.getEmails()).orElse(Collections.emptyList());
        return newEmails.size() != oldEmails.size();
    }

    private boolean phoneNumberInformationHasChanged(User newUser, User oldUser) {
        final List<Attribute> newPhoneNumbers = ofNullable(newUser.getPhoneNumbers()).orElse(Collections.emptyList());
        final List<Attribute> oldPhoneNumbers = ofNullable(oldUser.getPhoneNumbers()).orElse(Collections.emptyList());
        return newPhoneNumbers.size() != oldPhoneNumbers.size();
    }
}
