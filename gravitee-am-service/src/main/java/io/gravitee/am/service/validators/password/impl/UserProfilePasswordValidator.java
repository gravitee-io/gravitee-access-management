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

package io.gravitee.am.service.validators.password.impl;

import io.gravitee.am.model.User;
import io.gravitee.am.model.scim.Attribute;
import io.gravitee.am.service.exception.InvalidPasswordException;
import io.gravitee.am.service.validators.password.PasswordValidator;

import java.util.List;
import java.util.Locale;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserProfilePasswordValidator implements PasswordValidator {

    private static final String ERROR_MESSAGE = "invalid password user profile";
    private static final InvalidPasswordException INVALID_PASSWORD_EXCEPTION = InvalidPasswordException.of(ERROR_MESSAGE, ERROR_KEY);
    private final boolean excludeUserProfileInfo;
    private final User user;

    public UserProfilePasswordValidator(Boolean excludeUserProfileInfo, User user) {
        this.excludeUserProfileInfo = Boolean.TRUE.equals(excludeUserProfileInfo);
        this.user = user;
    }

    @Override
    public Boolean validate(String password) {
        if (!this.excludeUserProfileInfo || isNull(user)) {
            return true;
        }
        final String passwordToLower = password.toLowerCase(Locale.ROOT);
        return isFieldValid(passwordToLower, user.getUsername()) &&
                isFieldValid(passwordToLower, user.getNickName()) &&
                isFieldValid(passwordToLower, user.getFirstName()) &&
                isFieldValid(passwordToLower, user.getMiddleName()) &&
                isFieldValid(passwordToLower, user.getLastName()) &&
                isFieldValid(passwordToLower, user.getEmail()) &&
                ofNullable(user.getEmails()).orElse(List.of()).stream().map(Attribute::getValue)
                        .allMatch(phone -> isFieldValid(passwordToLower, phone)) &&
                isFieldValid(passwordToLower, user.getPhoneNumber()) &&
                ofNullable(user.getPhoneNumbers()).orElse(List.of()).stream().map(Attribute::getValue)
                        .allMatch(phone -> isFieldValid(passwordToLower, phone));
    }

    private boolean isFieldValid(String password, String userProfileInfo) {
        return isNullOrEmpty(userProfileInfo) || !password.contains(userProfileInfo.toLowerCase(Locale.ROOT));
    }

    @Override
    public InvalidPasswordException getCause() {
        return INVALID_PASSWORD_EXCEPTION;
    }
}
