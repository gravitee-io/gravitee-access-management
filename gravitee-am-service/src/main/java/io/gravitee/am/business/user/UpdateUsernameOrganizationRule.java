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
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.User;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Function3;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@AllArgsConstructor
public class UpdateUsernameOrganizationRule {
    private UserValidator validator;
    private Function3<Reference, String, String, Maybe<User>> findUserByUsernameAndSource;
    private Function<User, Single<User>> userUpdater;
    private AuditService auditService;

    public Single<User> updateUsername(String username, io.gravitee.am.identityprovider.api.User principal, Function<User, Single<UserProvider>> userProviderSupplier, Supplier<Single<User>> userSupplier) {
        final AtomicReference<String> oldUsername = new AtomicReference<>();
        return validator.validateUsername(username).andThen(Single.defer(() ->
                userSupplier.get().flatMap(user -> findUserByUsernameAndSource.apply(new Reference(user.getReferenceType(), user.getReferenceId()), username, user.getSource())
                                //If the user is not empty we throw a InvalidUserException to prevent username update
                                .flatMap(existingUser -> Maybe.<User>error(new InvalidUserException(String.format("User with username [%s] and idp [%s] already exists", username, user.getSource()))))
                                .switchIfEmpty(Single.just(user))
                        ).flatMap(user -> userProviderSupplier.apply(user).flatMap(userProvider -> userProvider.findByUsername(user.getUsername())
                                .switchIfEmpty(Single.error(UserNotFoundException::new))
                                .flatMap(idpUser -> userProvider.updateUsername(idpUser, username))
                                .flatMap(idpUser -> {
                                    user.updateUsername(username);

                                    return validator.validate(user).andThen(userUpdater.apply(user))
                                            .onErrorResumeNext(ex -> {
                                                // In the case we cannot update on our side, we rollback the username on the iDP and these credentials
                                                ((DefaultUser) idpUser).setUsername(oldUsername.get());
                                                return userProvider.updateUsername(idpUser, idpUser.getUsername())
                                                        .flatMap(rolledBackUser -> Single.error(ex));
                                            });
                                })
                        ))
                        .doOnSuccess(user1 -> {
                            auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USERNAME_UPDATED).user(user1));
                        })
                        .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USERNAME_UPDATED).throwable(throwable)))
        ));
    }
}
