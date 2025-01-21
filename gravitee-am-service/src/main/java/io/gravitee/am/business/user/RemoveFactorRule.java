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
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * This rule remove the given factor from a user profile before saving it.
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RemoveFactorRule extends UpdateUserRule {
    private AuditService auditService;

    public RemoveFactorRule(UserValidator validator,
                            BiFunction<User, UserRepository.UpdateActions, Single<User>> userUpdater,
                            AuditService auditService) {
        super(validator, userUpdater);
        this.auditService = auditService;
    }

    public Completable execute(User user, String factorId, io.gravitee.am.identityprovider.api.User principal) {
        if (user.getFactors() == null) {
            return Completable.complete();
        }
        List<EnrolledFactor> enrolledFactors = user.getFactors()
                .stream()
                .filter(enrolledFactor -> !factorId.equals(enrolledFactor.getFactorId()))
                .collect(Collectors.toList());
        User userToUpdate = new User(user);
        userToUpdate.setFactors(enrolledFactors);
        return update(userToUpdate)
                .doOnSuccess(user1 -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).user(user1).oldValue(user)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(UserAuditBuilder.class).principal(principal).type(EventType.USER_UPDATED).user(userToUpdate).throwable(throwable)))
                .ignoreElement();

    }
}
