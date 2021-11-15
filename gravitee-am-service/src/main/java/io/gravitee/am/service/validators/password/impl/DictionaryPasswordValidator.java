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

import io.gravitee.am.password.dictionary.PasswordDictionary;
import io.gravitee.am.service.exception.InvalidPasswordException;
import io.gravitee.am.service.validators.password.PasswordValidator;

import static java.lang.Boolean.TRUE;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DictionaryPasswordValidator implements PasswordValidator {

    private static final String ERROR_MESSAGE = "invalid password, try something else";
    private static final InvalidPasswordException INVALID_PASSWORD_EXCEPTION = InvalidPasswordException.of(ERROR_MESSAGE, ERROR_KEY);
    private final PasswordDictionary passwordDictionary;
    private final boolean isExcludePasswordDictionary;

    public DictionaryPasswordValidator(
            Boolean isExcludePasswordDictionary,
            PasswordDictionary passwordDictionary
    ){
        this.isExcludePasswordDictionary = TRUE.equals(isExcludePasswordDictionary);
        this.passwordDictionary = passwordDictionary;
    }

    @Override
    public Boolean validate(String password) {
        return !isExcludePasswordDictionary || !passwordDictionary.wordExists(password);
    }

    @Override
    public InvalidPasswordException getCause() {
        return INVALID_PASSWORD_EXCEPTION;
    }
}
