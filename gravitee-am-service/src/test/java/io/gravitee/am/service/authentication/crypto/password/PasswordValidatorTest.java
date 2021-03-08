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
package io.gravitee.am.service.authentication.crypto.password;

import io.gravitee.am.common.policy.PasswordInclude;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.service.validators.PasswordValidator;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordValidatorTest {

    private final PasswordValidator defaultPasswordValidator = new PasswordValidator("default");

    @Test
    public void testPassword_min_8_characters_at_least_one_letter_one_number() {
        PasswordValidator passwordValidator = new PasswordValidator("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$");

        Assert.assertFalse(passwordValidator.isValid("test"));
        Assert.assertFalse(passwordValidator.isValid("password"));
        Assert.assertTrue(passwordValidator.isValid("password01"));
    }

    @Test
    public void testPassword_min_8_characters_at_least_one_letter_one_number_one_special_character() {
        PasswordValidator passwordValidator = new PasswordValidator("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$");

        Assert.assertFalse(passwordValidator.isValid("test"));
        Assert.assertFalse(passwordValidator.isValid("password"));
        Assert.assertFalse(passwordValidator.isValid("password01"));
        Assert.assertTrue(passwordValidator.isValid("password01*"));
    }

    @Test
    public void testPassword_min_8_characters_at_least_one_uppercase_letter_one_lowercase_letter_one_number_one_special_character() {
        PasswordValidator passwordValidator = new PasswordValidator("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$");

        Assert.assertFalse(passwordValidator.isValid("test"));
        Assert.assertFalse(passwordValidator.isValid("password"));
        Assert.assertFalse(passwordValidator.isValid("password01"));
        Assert.assertFalse(passwordValidator.isValid("password01*"));
        Assert.assertTrue(passwordValidator.isValid("Password01*"));
    }

    @Test
    public void testPassword_min_8_characters_max_10_characters_at_least_one_uppercase_letter_one_lowercase_letter_one_number_one_special_character() {
        PasswordValidator passwordValidator = new PasswordValidator("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,10}$");

        Assert.assertFalse(passwordValidator.isValid("test"));
        Assert.assertFalse(passwordValidator.isValid("password"));
        Assert.assertFalse(passwordValidator.isValid("password01"));
        Assert.assertFalse(passwordValidator.isValid("password01*"));
        Assert.assertFalse(passwordValidator.isValid("Password01*"));
        Assert.assertTrue(passwordValidator.isValid("Password0*"));
    }

    @Test
    public void invalidMinLength() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, null, null, null);
        Optional<String> result = getValidationErrorKey("AB", passwordSettings);
        Assertions.assertThat(result).hasValue("invalid password minimum length");
    }

    @Test
    public void includeNumber() {
        PasswordSettings passwordSettings = buildPasswordSettings(2, PasswordInclude.NUMBERS, null, null);
        Assertions.assertThat(getValidationErrorKey("ABC", passwordSettings)).hasValue("password must contains numbers");
        Assertions.assertThat(getValidationErrorKey("A234", passwordSettings)).isEmpty();
        Assertions.assertThat(getValidationErrorKey("1234", passwordSettings)).isEmpty();
    }

    @Test
    public void includeNumbersAndSpecialCharacters() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, PasswordInclude.NUMBERS_AND_SPECIAL_CHARACTERS, null, null);
        Assertions.assertThat(getValidationErrorKey("AB12", passwordSettings)).hasValue("password must contains numbers and special characters");
        Assertions.assertThat(getValidationErrorKey("1234", passwordSettings)).hasValue("password must contains numbers and special characters");
        Assertions.assertThat(getValidationErrorKey("ABCD", passwordSettings)).hasValue("password must contains numbers and special characters");
        Assertions.assertThat(getValidationErrorKey("A$12", passwordSettings)).isEmpty();
    }

    @Test
    public void lettersInMixedCase() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, null, true, null);
        Assertions.assertThat(getValidationErrorKey("ABCD", passwordSettings)).hasValue("password must contains letters in mixed case");
        Assertions.assertThat(getValidationErrorKey("abcd", passwordSettings)).hasValue("password must contains letters in mixed case");
        Assertions.assertThat(getValidationErrorKey("ABcd", passwordSettings)).isEmpty();
    }

    @Test
    public void maxConsecutiveLetters() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, null, false, 3);
        Assertions.assertThat(getValidationErrorKey("ABBBCD", passwordSettings)).hasValue("invalid max consecutive letters");
        Assertions.assertThat(getValidationErrorKey("ABcd", passwordSettings)).isEmpty();
    }

    private Optional<String> getValidationErrorKey(String password, PasswordSettings passwordSettings) {
        try {
            defaultPasswordValidator.validate(password, passwordSettings);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(e.getMessage());
        }
    }

    private static PasswordSettings buildPasswordSettings(Integer minLength,
                                                          PasswordInclude passwordInclude,
                                                          Boolean lettersInMixedCase,
                                                          Integer maxConsecutiveLetters) {
        PasswordSettings passwordSettings = new PasswordSettings();
        passwordSettings.setMinLength(minLength);
        passwordSettings.setPasswordInclude(passwordInclude);
        passwordSettings.setLettersInMixedCase(lettersInMixedCase);
        passwordSettings.setMaxConsecutiveLetters(maxConsecutiveLetters);
        return passwordSettings;
    }
}
