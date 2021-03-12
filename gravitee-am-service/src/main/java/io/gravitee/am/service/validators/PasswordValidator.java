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
package io.gravitee.am.service.validators;

import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.service.exception.InvalidPasswordException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class PasswordValidator {

    /**
     * See https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
     */
    public static final int PASSWORD_MAX_LENGTH = 64;
    private static final Pattern SPECIAL_CHARACTER_PATTERN = Pattern.compile("[^a-zA-Z0-9]");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9]");
    private final Pattern defaultPasswordPattern;

    @Autowired
    public PasswordValidator(@Value("${user.password.policy.pattern:^(?:(?=.*\\d)(?=.*[A-Z])(?=.*[a-z])|(?=.*\\d)(?=.*[^A-Za-z0-9])(?=.*[a-z])|(?=.*[^A-Za-z0-9])(?=.*[A-Z])(?=.*[a-z])|(?=.*\\d)(?=.*[A-Z])(?=.*[^A-Za-z0-9]))(?!.*(.)\\1{2,})[A-Za-z0-9!~<>,;:_\\-=?*+#.\"'&§`£€%°()\\\\\\|\\[\\]\\-\\$\\^\\@\\/]{8,32}$}")
                                     String pattern) {
        this.defaultPasswordPattern = Pattern.compile(pattern);
    }

    public void validate(String password, PasswordSettings passwordSettings) {
        // fallback to default regex
        if (passwordSettings == null) {
            validate(password);
            return;
        }

        // check password settings
        if (password.length() > PASSWORD_MAX_LENGTH) {
            throw InvalidPasswordException.of("invalid password maximum length", "invalid_password_value");
        }

        if (passwordSettings.getMinLength() != null && password.length() < passwordSettings.getMinLength()) {
            throw InvalidPasswordException.of("invalid password minimum length", "invalid_password_value");
        }

        if (Boolean.TRUE.equals(passwordSettings.isIncludeNumbers())) {
            if (!NUMBER_PATTERN.matcher(password).find()) {
                throw InvalidPasswordException.of("password must contains numbers", "invalid_password_value");
            }
        }

        if (Boolean.TRUE.equals(passwordSettings.isIncludeSpecialCharacters())) {
            if (!SPECIAL_CHARACTER_PATTERN.matcher(password).find()) {
                throw InvalidPasswordException.of("password must contains special characters", "invalid_password_value");
            }
        }

        if (Boolean.TRUE.equals(passwordSettings.getLettersInMixedCase())) {
            if (password.chars().noneMatch(Character::isUpperCase) || password.chars().noneMatch(Character::isLowerCase)) {
                throw InvalidPasswordException.of("password must contains letters in mixed case", "invalid_password_value");
            }
        }

        Integer maxConsecutiveLetters = passwordSettings.getMaxConsecutiveLetters();
        if (maxConsecutiveLetters != null && maxConsecutiveLetters > 0) {
            if (isOverMaxConsecutiveLetters(password, maxConsecutiveLetters)) {
                throw InvalidPasswordException.of("invalid max consecutive letters", "invalid_password_value");
            }
        }
    }

    public boolean isValid(String password, PasswordSettings passwordSettings) {
        // fallback to default regex
        if (passwordSettings == null) {
            return isValid(password);
        }

        try {
            validate(password, passwordSettings);
            return true;
        } catch (InvalidPasswordException e) {
            return false;
        }
    }

    public boolean isValid(String password) {
        return password.length() <= PASSWORD_MAX_LENGTH
                && defaultPasswordPattern.matcher(password).matches();
    }

    public void validate(String password) {
        if (!isValid(password)) {
            throw InvalidPasswordException.of("Field [password] is invalid", "invalid_password_value");
        }
    }

    /**
     * Test if any character is repeated consecutively more than the giver max number
     * str="aaabb", max=3 -> true
     * str="aaabb", max=2 -> false
     */
    private static boolean isOverMaxConsecutiveLetters(String str, int max) {
        int len = str.length();
        for (int i = 0; i < len; i++) {
            int cur_count = 1;
            for (int j = i + 1; j < len; j++) {
                if (str.charAt(i) != str.charAt(j)) {
                    break;
                }
                cur_count++;
            }

            if (cur_count > max) {
                return true;
            }
        }
        return false;
    }
}
