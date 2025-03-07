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


import io.gravitee.am.dataplane.api.repository.UserRepository.UpdateActions;
import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Single;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.function.BiFunction;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@AllArgsConstructor
@Slf4j
public class UpdateUserRule {
    protected final UserValidator validator;
    private BiFunction<User, UpdateActions, Single<User>> userUpdater;

    /**
     * Validate the user entity and persist the full profile changes if the validation succeeds
     * see {@link UpdateUserRule#update(User, UpdateActions)}
     * @param user
     * @return
     */
   public Single<User> update(User user) {
        return update(user, UpdateActions.updateAll());
    }

    /**
     * Validate the user entity and persist the attributes specified by the UpdateActions if the validation succeeds
     *
     * @param user
     * @param updateActions
     * @return
     */
    public Single<User> update(User user, UpdateActions updateActions) {
        log.debug("Update a user {}", user);
        // updated date
        user.setUpdatedAt(new Date());
        return validator.validate(user).andThen(userUpdater.apply(user, updateActions))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error("An error occurs while trying to update a user", ex);
                    return Single.error(new TechnicalManagementException("An error occurs while trying to update a user", ex));
                });
    }
}
