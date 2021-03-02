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

import io.gravitee.am.common.policy.PasswordInclude;
import io.gravitee.am.model.application.PasswordSettings;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.Optional;

/**
 * @author Boualem DJELAILI (boualem.djelaili at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordUtilsTest {

    @Test
    public void errorIfRegexFormatIsNull() {
        PasswordSettings passwordSettings = buildPasswordSettings(true, null);
        Optional<String> result = getValidationErrorKey("", passwordSettings);
        Assertions.assertThat(result).hasValue("regexFormat value is null");
    }

    @Test
    public void regexFormatKo() {
        PasswordSettings passwordSettings = buildPasswordSettings(true, "[0-9]");
        Optional<String> result = getValidationErrorKey("ABC", passwordSettings);
        Assertions.assertThat(result).hasValue("invalid password value");
    }

    @Test
    public void regexFormatOK() {
        PasswordSettings passwordSettings = buildPasswordSettings(true, "[0-9]");
        Optional<String> result = getValidationErrorKey("1", passwordSettings);
        Assertions.assertThat(result).isEmpty();
    }

    @Test
    public void invalidMinLength() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, null, null, null, null);
        Optional<String> result = getValidationErrorKey("AB", passwordSettings);
        Assertions.assertThat(result).hasValue("invalid password minimum length");
    }

    @Test
    public void invalidMMaxLength() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, 5, null, null, null);
        Optional<String> result = getValidationErrorKey("AZERTYU", passwordSettings);
        Assertions.assertThat(result).hasValue("invalid password maximum length");
    }

    @Test
    public void includeNumber() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, 5, PasswordInclude.NUMBERS, null, null);
        Assertions.assertThat(getValidationErrorKey("ABC", passwordSettings)).hasValue("password must contains numbers");
        Assertions.assertThat(getValidationErrorKey("A234", passwordSettings)).isEmpty();
        Assertions.assertThat(getValidationErrorKey("1234", passwordSettings)).isEmpty();
    }

    @Test
    public void includeNumbersAndSpecialCharacters() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, 5, PasswordInclude.NUMBERS_AND_SPECIAL_CHARACTERS, null, null);
        Assertions.assertThat(getValidationErrorKey("AB12", passwordSettings)).hasValue("password must contains numbers and special characters");
        Assertions.assertThat(getValidationErrorKey("1234", passwordSettings)).hasValue("password must contains numbers and special characters");
        Assertions.assertThat(getValidationErrorKey("ABCD", passwordSettings)).hasValue("password must contains numbers and special characters");
        Assertions.assertThat(getValidationErrorKey("A$12", passwordSettings)).isEmpty();
    }

    @Test
    public void lettersInMixedCase() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, 5, null, true, null);
        Assertions.assertThat(getValidationErrorKey("ABCD", passwordSettings)).hasValue("password must contains letters in mixed case");
        Assertions.assertThat(getValidationErrorKey("abcd", passwordSettings)).hasValue("password must contains letters in mixed case");
        Assertions.assertThat(getValidationErrorKey("ABcd", passwordSettings)).isEmpty();
    }

    @Test
    public void maxConsecutiveLetters() {
        PasswordSettings passwordSettings = buildPasswordSettings(3, 10, null, false, 3);
        Assertions.assertThat(getValidationErrorKey("ABBBCD", passwordSettings)).hasValue("invalid max consecutive letters");
        Assertions.assertThat(getValidationErrorKey("ABcd", passwordSettings)).isEmpty();
    }

    private static Optional<String> getValidationErrorKey(String password, PasswordSettings passwordSettings) {
        try {
            PasswordUtils.validate(password, passwordSettings);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(e.getMessage());
        }
    }

    private static PasswordSettings buildPasswordSettings(Boolean regex, String regexFormat) {
        PasswordSettings passwordSettings = new PasswordSettings();
        passwordSettings.setRegex(regex);
        passwordSettings.setRegexFormat(regexFormat);
        return passwordSettings;
    }

    private static PasswordSettings buildPasswordSettings(Integer minLength,
                                                          Integer maxLength,
                                                          PasswordInclude passwordInclude,
                                                          Boolean lettersInMixedCase,
                                                          Integer maxConsecutiveLetters) {
        PasswordSettings passwordSettings = new PasswordSettings();
        passwordSettings.setMinLength(minLength);
        passwordSettings.setMaxLength(maxLength);
        passwordSettings.setPasswordInclude(passwordInclude);
        passwordSettings.setLettersInMixedCase(lettersInMixedCase);
        passwordSettings.setMaxConsecutiveLetters(maxConsecutiveLetters);
        return passwordSettings;
    }
}