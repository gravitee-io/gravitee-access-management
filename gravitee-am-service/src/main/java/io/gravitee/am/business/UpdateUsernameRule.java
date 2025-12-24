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

package io.gravitee.am.business;


import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.factor.FactorDataKeys;
import io.gravitee.am.common.utils.MovingFactorUtils;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.management.api.search.LoginAttemptCriteria;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CommonUserService;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.LoginAttemptService;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.gravitee.am.model.ReferenceType.DOMAIN;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@AllArgsConstructor
public class UpdateUsernameRule {
    private UserValidator validator;
    private CommonUserService userService;
    private AuditService auditService;
    private CredentialService credentialService;
    private LoginAttemptService loginAttemptService;

    public Single<User> updateUsername(String username, io.gravitee.am.identityprovider.api.User principal, Function<User, Single<UserProvider>> userProviderSupplier, Supplier<Single<User>> userSupplier) {
        final AtomicReference<String> oldUsername = new AtomicReference<>();
        return validator.validateUsername(username).andThen(Single.defer(() ->
                userSupplier.get().flatMap(user -> userService
                                .findByUsernameAndSource(user.getReferenceType(), user.getReferenceId(), username, user.getSource())
                                //If the user is empty we throw a UserNotFoundException to allow the update
                                .switchIfEmpty(Single.error(() -> new UserNotFoundException(user.getReferenceId(), username)))
                                //If the user is not empty we throw a InvalidUserException to prevent username update
                                .flatMap(existingUser -> Single.<User>error(new UserAlreadyExistsException(username)))
                                .onErrorResumeNext(ex -> {
                                    if (ex instanceof UserNotFoundException) {
                                        return Single.just(user);
                                    }
                                    return Single.error(mapUserAlreadyExistsException(ex, username, user.getSource()));
                                })
                        ).flatMap(user -> userProviderSupplier.apply(user).flatMap(userProvider -> userProvider.findByUsername(user.getUsername())
                                        .switchIfEmpty(Single.error(UserNotFoundException::new))
                                        .flatMap(idpUser -> userProvider.updateUsername(idpUser, username))
                                        .flatMap(idpUser -> {
                                            oldUsername.set(user.getUsername());
                                            return updateCredentialUsername(user.getReferenceType(), user.getReferenceId(), oldUsername.get(), idpUser);
                                        })
                                        .flatMap(idpUser -> {
                                            user.updateUsername(username);

                                            // Generate a new moving factor based on user id instead of username. Necessary
                                            // since username can be changed.
                                            generateNewMovingFactorBasedOnUserId(user);

                                            return userService.update(user).onErrorResumeNext(ex -> {
                                                // In the case we cannot update on our side, we rollback the username on the iDP and these credentials
                                                ((DefaultUser) idpUser).setUsername(oldUsername.get());
                                                return userProvider.updateUsername(idpUser, idpUser.getUsername())
                                                        .flatMap(idpUser1 -> updateCredentialUsername(user.getReferenceType(), user.getReferenceId(), idpUser1.getUsername(), oldUsername.get()))
                                                        .flatMap(rolledBackUser -> Single.error(ex));
                                            });
                                        })
                                ).onErrorResumeNext(ex -> Single.error(mapUserAlreadyExistsException(ex, username, user.getSource()))))
                        .doOnSuccess(user1 -> {
                            auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USERNAME_UPDATED).user(user1));
                            if (DOMAIN.equals(user1.getReferenceType())) {
                                loginAttemptService.reset(createLoginAttemptCriteria(user1.getReferenceId(), oldUsername.get()))
                                        .onErrorResumeNext(error -> {
                                            log.warn("Could not delete login attempt {}", error.getMessage());
                                            return Completable.complete();
                                        })
                                        .subscribe();
                            }
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

    private Single<io.gravitee.am.identityprovider.api.User> updateCredentialUsername(ReferenceType
                                                                                              referenceType, String referenceId, String oldUsername, io.gravitee.am.identityprovider.api.User user) {
        return updateCredentialUsername(referenceType, referenceId, oldUsername, user.getUsername())
                .flatMap(__ -> Single.just(user));
    }

    private Single<String> updateCredentialUsername(ReferenceType referenceType, String referenceId, String
            oldUsername, String newUsername) {
        return credentialService.findByUsername(referenceType, referenceId, oldUsername)
                .map(credential -> {
                    credential.setUsername(newUsername);
                    return credentialService.update(credential).subscribe();
                })
                .toList()
                .flatMapMaybe(singles -> Maybe.just(newUsername))
                .toSingle();
    }

    private Throwable mapUserAlreadyExistsException(Throwable ex, String username, String idp) {
        if (ex instanceof UserAlreadyExistsException) {
            return new InvalidUserException(
                    String.format("User with username [%s] and idp [%s] already exists", username, idp)
            );
        }
        return ex;
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
