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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

import static io.gravitee.am.model.PasswordSettings.PASSWORD_MAX_LENGTH;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component("defaultPasswordValidator")
public class DefaultPasswordValidatorImpl implements PasswordValidator {

    private static final String DEFAULT_PASSWORD_PATTERN_VALUE = "^(?:(?=.*\\d)(?=.*[A-Z])(?=.*[a-z])|(?=.*\\d)(?=.*[^A-Za-z0-9])(?=.*[a-z])|(?=.*[^A-Za-z0-9])(?=.*[A-Z])(?=.*[a-z])|(?=.*\\d)(?=.*[A-Z])(?=.*[^A-Za-z0-9]))(?!.*(.)\\1{2,})[A-Za-z0-9!~<>,;:_\\-=?*+#.\"'&§`£€%°()\\\\\\|\\[\\]\\-\\$\\^\\@\\/]{8,32}$";

    private static final String MESSAGE = "Field [password] is invalid";
    private static final String ERROR_KEY = "invalid_password_value";
    private static final InvalidPasswordException INVALID_PASSWORD_VALUE = InvalidPasswordException.of(MESSAGE, ERROR_KEY);

    private final Pattern defaultPasswordPattern;

    @Autowired
    public DefaultPasswordValidatorImpl(@Value("${user.password.policy.pattern:" + DEFAULT_PASSWORD_PATTERN_VALUE + "}") String pattern) {
        this.defaultPasswordPattern = Pattern.compile(pattern);
    }

    public Boolean validate(String password) {
        return password.length() <= PASSWORD_MAX_LENGTH && defaultPasswordPattern.matcher(password).matches();
    }

    @Override
    public InvalidPasswordException getCause() {
        return INVALID_PASSWORD_VALUE;
    }
}
