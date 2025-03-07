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

package io.gravitee.am.business.user;


import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.common.utils.MovingFactorUtils;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.dataplane.api.search.LoginAttemptCriteria;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.User;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.dataplane.CredentialCommonService;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Function3;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class UpdateUsernameDomainRule extends UpdateUserRule {
    private Function3<Reference, String, String, Maybe<User>> findUserByUsernameAndSource;
    private BiFunction<Domain, LoginAttemptCriteria , Completable> resetLoginAttempts;
    private AuditService auditService;
    private CredentialCommonService credentialService;

    public UpdateUsernameDomainRule(UserValidator validator,
                                    BiFunction<User, UserRepository.UpdateActions, Single<User>> userUpdater,
                                    Function3<Reference, String, String, Maybe<User>> findUserByUsernameAndSource,
                                    AuditService auditService,
                                    CredentialCommonService credentialService,
                                    BiFunction<Domain, LoginAttemptCriteria , Completable> resetLoginAttempts) {
        super(validator, userUpdater);
        this.findUserByUsernameAndSource = findUserByUsernameAndSource;
        this.auditService = auditService;
        this.credentialService = credentialService;
        this.resetLoginAttempts = resetLoginAttempts;
    }

    public Single<User> updateUsername(Domain domain, String username, io.gravitee.am.identityprovider.api.User principal, Function<User, Single<UserProvider>> userProviderSupplier, Supplier<Single<User>> userSupplier) {
        final AtomicReference<String> oldUsername = new AtomicReference<>();
        return validator.validateUsername(username).andThen(Single.defer(() ->
                userSupplier.get().flatMap(user -> findUserByUsernameAndSource.apply(domain.asReference(), username, user.getSource())
                                //If the user is not empty we throw a InvalidUserException to prevent username update
                                .flatMap(existingUser -> Maybe.<User>error(new InvalidUserException(String.format("User with username [%s] and idp [%s] already exists", username, user.getSource()))))
                                .switchIfEmpty(Single.just(user))
                        ).flatMap(user -> userProviderSupplier.apply(user).flatMap(userProvider -> userProvider.findByUsername(user.getUsername())
                                .switchIfEmpty(Single.error(UserNotFoundException::new))
                                .flatMap(idpUser -> userProvider.updateUsername(idpUser, username))
                                .flatMap(idpUser -> {
                                    oldUsername.set(user.getUsername());
                                    return updateCredentialUsername(domain, oldUsername.get(), idpUser);
                                })
                                .flatMap(idpUser -> {
                                    user.updateUsername(username);

                                    // Generate a new moving factor based on user id instead of username. Necessary
                                    // since username can be changed.
                                    generateNewMovingFactorBasedOnUserId(user);

                                    return update(user)
                                            .onErrorResumeNext(ex -> {
                                                // In the case we cannot update on our side, we roll back the username on the iDP and these credentials
                                                ((DefaultUser) idpUser).setUsername(oldUsername.get());
                                                return userProvider.updateUsername(idpUser, idpUser.getUsername())
                                                        .flatMap(idpUser1 -> updateCredentialUsername(domain, idpUser1.getUsername(), oldUsername.get()))
                                                        .flatMap(rolledBackUser -> Single.error(ex));
                                            });
                                })
                        ))
                        .doOnSuccess(user1 -> {
                            auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USERNAME_UPDATED).user(user1));
                            resetLoginAttempts.apply(domain, createLoginAttemptCriteria(user1.getReferenceId(), oldUsername.get()))
                                    .onErrorResumeNext(error -> {
                                        log.warn("Could not delete login attempt {}", error.getMessage());
                                        return Completable.complete();
                                    })
                                    .subscribe();
                        })
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USERNAME_UPDATED).throwable(throwable)))
        ));
    }

    private LoginAttemptCriteria createLoginAttemptCriteria(String domainId, String username) {
        return new LoginAttemptCriteria.Builder()
                .domain(domainId)
                .username(username)
                .build();
    }

    private Single<io.gravitee.am.identityprovider.api.User> updateCredentialUsername(Domain domain, String oldUsername, io.gravitee.am.identityprovider.api.User user) {
        return updateCredentialUsername(domain, oldUsername, user.getUsername())
                .flatMap(__ -> Single.just(user));
    }

    private Single<String> updateCredentialUsername(Domain domain, String oldUsername, String newUsername) {
        return credentialService.findByUsername(domain, oldUsername)
                .map(credential -> {
                    credential.setUsername(newUsername);
                    return credentialService.update(domain, credential).subscribe();
                })
                .toList()
                .flatMapMaybe(singles -> Maybe.just(newUsername))
                .toSingle();
    }

    private void generateNewMovingFactorBasedOnUserId(User user) {

        Optional.ofNullable(user.getFactors()).ifPresent(enrolledFactors ->

                user.getFactors()
                        .stream()
                        .filter(enrolledFactor -> Optional.ofNullable(enrolledFactor.getSecurity()).isPresent())
                        .forEach(enrolledFactor -> {

                            final var additionalData = enrolledFactor.getSecurity().getAdditionalData();

                            if (additionalData.containsKey(FactorDataKeys.KEY_MOVING_FACTOR)) {
                                additionalData.put(
                                        FactorDataKeys.KEY_MOVING_FACTOR,
                                        MovingFactorUtils.generateInitialMovingFactor(user.getId())
                                );
                                enrolledFactor.setUpdatedAt(new Date());
                            }
                        })
        );
    }
}
