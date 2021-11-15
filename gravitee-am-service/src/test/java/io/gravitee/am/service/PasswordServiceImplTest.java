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
package io.gravitee.am.service;

import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.password.dictionary.PasswordDictionaryImpl;
import io.gravitee.am.service.impl.PasswordServiceImpl;
import io.gravitee.am.service.validators.password.impl.DefaultPasswordValidatorImpl;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordServiceImplTest {

    private static final PasswordService passwordService = new PasswordServiceImpl(new DefaultPasswordValidatorImpl("default"), new PasswordDictionaryImpl(
    ).start(false));

    @Test
    public void testPassword_min_8_characters_at_least_one_letter_one_number() {
        PasswordService passwordValidator = new PasswordServiceImpl(new DefaultPasswordValidatorImpl("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$"), null);

        Assert.assertFalse(passwordValidator.isValid("test"));
        Assert.assertFalse(passwordValidator.isValid("password"));
        Assert.assertTrue(passwordValidator.isValid("password01"));
    }

    @Test
    public void testPassword_min_8_characters_at_least_one_letter_one_number_one_special_character() {
        PasswordService passwordValidator = new PasswordServiceImpl(new DefaultPasswordValidatorImpl("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$"), new PasswordDictionaryImpl(
        ));

        Assert.assertFalse(passwordValidator.isValid("test"));
        Assert.assertFalse(passwordValidator.isValid("password"));
        Assert.assertFalse(passwordValidator.isValid("password01"));
        Assert.assertTrue(passwordValidator.isValid("password01*"));
    }

    @Test
    public void testPassword_min_8_characters_at_least_one_uppercase_letter_one_lowercase_letter_one_number_one_special_character() {
        PasswordService passwordValidator = new PasswordServiceImpl(new DefaultPasswordValidatorImpl("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"), null);

        Assert.assertFalse(passwordValidator.isValid("test"));
        Assert.assertFalse(passwordValidator.isValid("password"));
        Assert.assertFalse(passwordValidator.isValid("password01"));
        Assert.assertFalse(passwordValidator.isValid("password01*"));
        Assert.assertTrue(passwordValidator.isValid("Password01*"));
    }

    @Test
    public void testPassword_min_8_characters_max_10_characters_at_least_one_uppercase_letter_one_lowercase_letter_one_number_one_special_character() {
        PasswordService passwordValidator = new PasswordServiceImpl(new DefaultPasswordValidatorImpl("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,10}$"),null);

        Assert.assertFalse(passwordValidator.isValid("test"));
        Assert.assertFalse(passwordValidator.isValid("password"));
        Assert.assertFalse(passwordValidator.isValid("password01"));
        Assert.assertFalse(passwordValidator.isValid("password01*"));
        Assert.assertFalse(passwordValidator.isValid("Password01*"));
        Assert.assertTrue(passwordValidator.isValid("Password0*"));
    }

    @Test
    public void invalidMinLength() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, null, null, null, null, null);
        Optional<String> result = getValidationErrorKey("AB", passwordSettings);
        Assertions.assertThat(result).hasValue("invalid password minimum length");
    }

    @Test
    public void includeNumber() {
        PasswordSettings passwordSettings = buildPasswordSettings(2, true, null, null, null, null);
        Assertions.assertThat(getValidationErrorKey("ABC", passwordSettings)).hasValue("password must contains numbers");
        Assertions.assertThat(getValidationErrorKey("A234", passwordSettings)).isEmpty();
        Assertions.assertThat(getValidationErrorKey("1234", passwordSettings)).isEmpty();
    }

    @Test
    public void includeSpecialCharacters() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, false, true, null, null, null);
        Assertions.assertThat(getValidationErrorKey("AB12", passwordSettings)).hasValue("password must contains special characters");
        Assertions.assertThat(getValidationErrorKey("1234", passwordSettings)).hasValue("password must contains special characters");
        Assertions.assertThat(getValidationErrorKey("ABCD", passwordSettings)).hasValue("password must contains special characters");
        Assertions.assertThat(getValidationErrorKey("A$12", passwordSettings)).isEmpty();
    }

    @Test
    public void lettersInMixedCase() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, null, null, true, null, null);
        Assertions.assertThat(getValidationErrorKey("ABCD", passwordSettings)).hasValue("password must contains letters in mixed case");
        Assertions.assertThat(getValidationErrorKey("abcd", passwordSettings)).hasValue("password must contains letters in mixed case");
        Assertions.assertThat(getValidationErrorKey("ABcd", passwordSettings)).isEmpty();
    }

    @Test
    public void maxConsecutiveLetters() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, null, null, false, 3, null);
        Assertions.assertThat(getValidationErrorKey("ABBBBCD", passwordSettings)).hasValue("invalid max consecutive letters");
        Assertions.assertThat(getValidationErrorKey("ABBBcd", passwordSettings)).isEmpty();
    }

    @Test
    public void passwordInDictionary() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, null, null, false, null, true);
        Assertions.assertThat(getValidationErrorKey("trustno1", passwordSettings)).hasValue("invalid password, try something else");
        Assertions.assertThat(getValidationErrorKey("mY5tR0N9P@SsWoRd!", passwordSettings)).isEmpty();
    }

    private Optional<String> getValidationErrorKey(String password, PasswordSettings passwordSettings) {
        try {
            passwordService.validate(password, passwordSettings);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(e.getMessage());
        }
    }

    private static PasswordSettings buildPasswordSettings(Integer minLength,
                                                          Boolean includeNumbers,
                                                          Boolean includeSpecialCharacters,
                                                          Boolean lettersInMixedCase,
                                                          Integer maxConsecutiveLetters,
                                                          Boolean excludePasswordInDictionary
    ) {
        PasswordSettings passwordSettings = new PasswordSettings();
        passwordSettings.setMinLength(minLength);
        passwordSettings.setIncludeNumbers(includeNumbers);
        passwordSettings.setIncludeSpecialCharacters(includeSpecialCharacters);
        passwordSettings.setLettersInMixedCase(lettersInMixedCase);
        passwordSettings.setMaxConsecutiveLetters(maxConsecutiveLetters);
        passwordSettings.setExcludePasswordsInDictionary(excludePasswordInDictionary);
        return passwordSettings;
    }
}
