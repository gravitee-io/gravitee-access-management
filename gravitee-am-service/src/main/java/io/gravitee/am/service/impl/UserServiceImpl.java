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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.analytics.AnalyticsQuery;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.factor.FactorStatus;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class UserServiceImpl extends AbstractUserService implements UserService {

    @Lazy
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditService auditService;

    @Override
    protected UserRepository getUserRepository() {
        return this.userRepository;
    }

    @Override
    public Flowable<User> findByDomain(String domain) {
        LOGGER.debug("Find users by domain: {}", domain);
        return userRepository.findAll(ReferenceType.DOMAIN, domain)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find users by domain {}", domain, ex);
                    return Flowable.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by domain %s", domain), ex));
                });
    }

    @Override
    public Single<Page<User>> findByDomain(String domain, int page, int size) {
        return findAll(ReferenceType.DOMAIN, domain, page, size);
    }

    @Override
    public Maybe<User> findById(String id) {
        LOGGER.debug("Find user by id : {}", id);
        return userRepository.findById(id)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its ID {}", id, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s", id), ex));
                });
    }

    @Override
    public Maybe<User> findByDomainAndUsername(String domain, String username) {
        LOGGER.debug("Find user by username and domain: {} {}", username, domain);
        return userRepository.findByUsernameAndDomain(domain, username)
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its ID: {} for the domain {}", username, domain, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s for the domain %s", username, domain), ex));
                });
    }

    @Override
    public Maybe<User> findByDomainAndUsernameAndSource(String domain, String username, String source) {
        return findByUsernameAndSource(ReferenceType.DOMAIN, domain, username, source);
    }

    @Override
    public Single<User> create(String domain, NewUser newUser) {
        return create(ReferenceType.DOMAIN, domain, newUser);
    }


    @Override
    public Single<User> update(String domain, String id, UpdateUser updateUser) {
        return update(ReferenceType.DOMAIN, domain, id, updateUser);
    }

    @Override
    public Single<User> update(User user) {
        LOGGER.debug("Update a user {}", user);
        // updated date
        user.setUpdatedAt(new Date());
        return userValidator.validate(user).andThen(getUserRepository().update(user)
                .flatMap(user1 -> {
                    // create event for sync process
                    Event event = new Event(Type.USER, new Payload(user1.getId(), user1.getReferenceType(), user1.getReferenceId(), Action.UPDATE));
                    return eventService.create(event).flatMap(__ -> Single.just(user1));
                })
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to update a user", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a user", ex));
                }));
    }

    @Override
    public Single<Long> countByDomain(String domain) {
        LOGGER.debug("Count user by domain {}", domain);

        return userRepository.countByReference(ReferenceType.DOMAIN, domain)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to count users by domain: {}", domain, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while count users to delete user: %s", domain), ex));
                });
    }

    @Override
    public Single<Long> countByApplication(String domain, String application) {
        LOGGER.debug("Count user by application {}", application);

        return userRepository.countByApplication(domain, application).onErrorResumeNext(ex -> {
            if (ex instanceof AbstractManagementException) {
                return Single.error(ex);
            }
            LOGGER.error("An error occurs while trying to count users by application: {}", application, ex);
            return Single.error(new TechnicalManagementException(
                    String.format("An error occurs while count users to delete user: %s", application), ex));
        });
    }

    @Override
    public Single<Map<Object, Object>> statistics(AnalyticsQuery query) {
        LOGGER.debug("Get user collection analytics {}", query);

        return userRepository.statistics(query)
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    LOGGER.error("An error occurs while trying to get users analytics : {}", query, ex);
                    return Single.error(new TechnicalManagementException(
                            String.format("An error occurs while count users analytics : %s", query), ex));
                });
    }

    @Override
    public Completable deleteByDomain(String domain) {
        LOGGER.debug("Delete all users for domain {}", domain);
        return credentialService.deleteByReference(ReferenceType.DOMAIN, domain)
                .andThen(userRepository.deleteByReference(ReferenceType.DOMAIN, domain));
    }

    @Override
    public Single<User> upsertFactor(String userId, EnrolledFactor enrolledFactor, io.gravitee.am.identityprovider.api.User principal) {
        return findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapSingle(oldUser -> {
                    User user = new User(oldUser);
                    List<EnrolledFactor> enrolledFactors = user.getFactors();
                    if (enrolledFactors == null || enrolledFactors.isEmpty()) {
                        enrolledFactors = Collections.singletonList(enrolledFactor);
                    } else {
                        // if current factor is primary, set the others to secondary
                        if (Boolean.TRUE.equals(enrolledFactor.isPrimary())) {
                            enrolledFactors.forEach(e -> e.setPrimary(false));
                        }
                        // if the Factor already exists, update the target and the security value
                        Optional<EnrolledFactor> optFactor = enrolledFactors.stream()
                                .filter(existingFactor -> existingFactor.getFactorId().equals(enrolledFactor.getFactorId()))
                                .findFirst();
                        if (optFactor.isPresent()) {
                            EnrolledFactor factorToUpdate = new EnrolledFactor(optFactor.get());
                            factorToUpdate.setStatus(enrolledFactor.getStatus());
                            factorToUpdate.setChannel(enrolledFactor.getChannel());
                            factorToUpdate.setSecurity(enrolledFactor.getSecurity());
                            factorToUpdate.setPrimary(enrolledFactor.isPrimary());
                            // update the factor
                            enrolledFactors.removeIf(ef -> factorToUpdate.getFactorId().equals(ef.getFactorId()));
                            enrolledFactors.add(factorToUpdate);
                        } else {
                            enrolledFactors.add(enrolledFactor);
                        }
                    }
                    user.setFactors(enrolledFactors);

                    if (enrolledFactor.getChannel() != null && EnrolledFactorChannel.Type.SMS.equals(enrolledFactor.getChannel().getType())) {
                        // MFA SMS currently used, preserve the phone number into the user profile if not yet present
                        List<Attribute> phoneNumbers = user.getPhoneNumbers();
                        if (phoneNumbers == null) {
                            phoneNumbers = new ArrayList<>();
                            user.setPhoneNumbers(phoneNumbers);
                        }
                        String enrolledPhoneNumber = enrolledFactor.getChannel().getTarget();
                        if (!phoneNumbers.stream().filter(p -> p.getValue().equals(enrolledPhoneNumber)).findFirst().isPresent()) {
                            Attribute newPhoneNumber = new Attribute();
                            newPhoneNumber.setType("mobile");
                            newPhoneNumber.setPrimary(phoneNumbers.isEmpty());
                            newPhoneNumber.setValue(enrolledPhoneNumber);
                            phoneNumbers.add(newPhoneNumber);
                        }
                    }
                    if (enrolledFactor.getChannel() != null && EnrolledFactorChannel.Type.EMAIL.equals(enrolledFactor.getChannel().getType())) {
                        // MFA EMAIL currently used, preserve the email into the user profile if not yet present
                        String email = user.getEmail();
                        String enrolledEmail = enrolledFactor.getChannel().getTarget();
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
                            if (!emails.stream().filter(p -> p.getValue().equals(enrolledEmail)).findFirst().isPresent()) {
                                Attribute additionalEmail = new Attribute();
                                additionalEmail.setPrimary(false);
                                additionalEmail.setValue(enrolledEmail);
                                emails.add(additionalEmail);
                            }
                        }
                    }
                    return update(user)
                            .doOnSuccess(user1 -> {
                                if (needToAuditUserFactorsOperation(user1, oldUser)) {
                                    // remove sensitive data about factors
                                    removeSensitiveFactorsData(user1.getFactors());
                                    removeSensitiveFactorsData(oldUser.getFactors());
                                    auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).user(user1).oldValue(oldUser));
                                }
                            })
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).throwable(throwable)));
                });
    }

    @Override
    public Completable removeFactor(String userId, String factorId, io.gravitee.am.identityprovider.api.User principal) {
        return findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapCompletable(oldUser -> {
                    if (oldUser.getFactors() == null) {
                        return Completable.complete();
                    }
                    List<EnrolledFactor> enrolledFactors = oldUser.getFactors()
                            .stream()
                            .filter(enrolledFactor -> !factorId.equals(enrolledFactor.getFactorId()))
                            .collect(Collectors.toList());
                    User userToUpdate = new User(oldUser);
                    userToUpdate.setFactors(enrolledFactors);
                    return update(userToUpdate)
                            .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).user(user1).oldValue(oldUser)))
                            .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).throwable(throwable)))
                            .ignoreElement();
                });
    }

    private void removeSensitiveFactorsData(List<EnrolledFactor> enrolledFactors) {
        if (enrolledFactors == null) {
            return;
        }
        enrolledFactors
                .stream()
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
        if (newEnrolledFactors
                .stream()
                .anyMatch(newEnrolledFactor -> {
                    return oldEnrolledFactors
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
                            });
                })) {
            return true;
        }
        return false;
    }

    private boolean emailInformationHasChanged(User newUser, User oldUser) {
        if (oldUser.getEmail() == null
                && newUser.getEmail() != null) {
            return true;
        }

        // if email list not match, create an audit
        final List<Attribute> newEmails = newUser.getEmails() != null ? newUser.getEmails() : Collections.emptyList();
        final List<Attribute> oldEmails = oldUser.getEmails() != null ? oldUser.getEmails() : Collections.emptyList();
        return newEmails.size() != oldEmails.size();
    }

    private boolean phoneNumberInformationHasChanged(User newUser, User oldUser) {
        final List<Attribute> newPhoneNumbers = newUser.getPhoneNumbers() != null ? newUser.getPhoneNumbers() : Collections.emptyList();
        final List<Attribute> oldPhoneNumbers = oldUser.getPhoneNumbers() != null ? oldUser.getPhoneNumbers() : Collections.emptyList();
        return newPhoneNumbers.size() != oldPhoneNumbers.size();
    }
}
