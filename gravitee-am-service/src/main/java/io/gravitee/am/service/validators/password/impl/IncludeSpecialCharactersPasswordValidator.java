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

import io.gravitee.am.service.exception.InvalidPasswordException;
import io.gravitee.am.service.validators.password.PasswordValidator;

import java.util.regex.Pattern;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IncludeSpecialCharactersPasswordValidator implements PasswordValidator {

    private static final Pattern SPECIAL_CHARACTER_PATTERN = Pattern.compile("[^a-zA-Z0-9]");
    private static final String ERROR_MESSAGE = "password must contains special characters";
    private static final InvalidPasswordException INVALID_PASSWORD_EXCEPTION = InvalidPasswordException.of(ERROR_MESSAGE, ERROR_KEY);
    private final Boolean includeSpecialCharacters;

    public IncludeSpecialCharactersPasswordValidator(Boolean includeSpecialCharacters) {
        this.includeSpecialCharacters = Boolean.TRUE.equals(includeSpecialCharacters);
    }

    @Override
    public Boolean validate(String password) {
        return !includeSpecialCharacters || SPECIAL_CHARACTER_PATTERN.matcher(password).find();
    }

    @Override
    public InvalidPasswordException getCause() {
        return INVALID_PASSWORD_EXCEPTION;
    }
}
