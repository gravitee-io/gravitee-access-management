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


import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserInvalidException;
import io.gravitee.am.service.validators.user.UserValidator;
import io.reactivex.rxjava3.core.Single;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.function.Function;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@AllArgsConstructor
public class CreateUserRule {
    public static final String CREATE_USER_ERROR = "An error occurs while trying to create a user";

    private UserValidator validator;
    private Function<User, Single<User>> userCreator;

    /**
     * Call user validator before trying to persist the entity
     * @param user
     * @return
     */
    public Single<User> create(User user) {
        log.debug("Create a user {}", user);
        if (StringUtils.isBlank(user.getUsername())) {
            return Single.error(() -> new UserInvalidException("Field [username] is required"));
        }
        user.setCreatedAt(new Date());
        user.setUpdatedAt(user.getCreatedAt());
        return validator.validate(user)
                .andThen(userCreator.apply(user))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException) {
                        return Single.error(ex);
                    }
                    log.error(CREATE_USER_ERROR, ex);
                    return Single.error(new TechnicalManagementException(CREATE_USER_ERROR, ex));
                });
    }
}
