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
package io.gravitee.am.service.utils;

import io.gravitee.am.common.exception.uma.InvalidPasswordException;
import io.gravitee.am.common.policy.PasswordInclude;
import io.gravitee.am.model.application.PasswordSettings;

import java.util.regex.Pattern;

/**
 * @author Boualem DJELAILI (boualem.djelaili at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordUtils {

    private static final Pattern SPECIAL_CHARACTER_PATTERN = Pattern.compile("[^a-zA-Z0-9]");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9]");

    private PasswordUtils() {
    }

    /**
     * @throws InvalidPasswordException if password is incorrect
     */
    public static void validate(String password, PasswordSettings passwordSettings) {
        //regex case
        if (Boolean.TRUE.equals(passwordSettings.getRegex())) {
            String regexFormat = passwordSettings.getRegexFormat();
            if (regexFormat == null) {
                throw new IllegalStateException("regexFormat value is null");
            }
            if (Pattern.compile(regexFormat).matcher(password).matches()) {
                return;
            }
            throw InvalidPasswordException.of("invalid password value", "invalid_password_value");
        }

        //details case
        if (passwordSettings.getMinLength() != null && password.length() < passwordSettings.getMinLength()) {
            throw InvalidPasswordException.of("invalid password minimum length", "invalid_password_value");
        }
        if (passwordSettings.getMaxLength() != null && password.length() > passwordSettings.getMaxLength()) {
            throw InvalidPasswordException.of("invalid password maximum length", "invalid_password_value");
        }

        PasswordInclude passwordInclude = passwordSettings.getPasswordInclude();
        if (passwordInclude != null) {
            switch (passwordInclude) {
                case NUMBERS:
                    if (!NUMBER_PATTERN.matcher(password).find()) {
                        throw InvalidPasswordException.of("password must contains numbers", "invalid_password_value");
                    }
                    break;
                case NUMBERS_AND_SPECIAL_CHARACTERS:
                    if (!NUMBER_PATTERN.matcher(password).find() || !SPECIAL_CHARACTER_PATTERN.matcher(password).find()) {
                        throw InvalidPasswordException.of("password must contains numbers and special characters", "invalid_password_value");
                    }
                    break;
                default:
                    throw new IllegalStateException("Unsupported enum : {}" + passwordInclude);
            }
        }

        if (Boolean.TRUE.equals(passwordSettings.getLettersInMixedCase())) {
            if (password.chars().noneMatch(Character::isUpperCase) || password.chars().noneMatch(Character::isLowerCase)) {
                throw InvalidPasswordException.of("password must contains letters in mixed case", "invalid_password_value");
            }
        }

        Integer maxConsecutiveLetters = passwordSettings.getMaxConsecutiveLetters();
        if (maxConsecutiveLetters != null && maxConsecutiveLetters > 1) {
            for (char c : password.toCharArray()) {
                if (password.chars().filter(ch -> ch == c).count() >= maxConsecutiveLetters) {
                    throw InvalidPasswordException.of("invalid max consecutive letters", "invalid_password_value");
                }
            }
        }
    }

    public static boolean isValid(String password, PasswordSettings passwordSettings) {
        try {
            validate(password, passwordSettings);
            return true;
        } catch (InvalidPasswordException e) {
            return false;
        }
    }
}
