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

import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.User;
import io.gravitee.am.password.dictionary.PasswordDictionaryImpl;
import io.gravitee.am.service.impl.PasswordServiceImpl;
import io.gravitee.am.service.validators.password.PasswordSettingsStatus;
import io.gravitee.am.service.validators.password.impl.DefaultPasswordValidatorImpl;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Optional;

import static io.gravitee.am.common.oidc.StandardClaims.EMAIL;
import static io.gravitee.am.common.oidc.StandardClaims.PHONE_NUMBER;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
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
        PasswordService passwordValidator = new PasswordServiceImpl(new DefaultPasswordValidatorImpl("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,10}$"), null);

        Assert.assertFalse(passwordValidator.isValid("test"));
        Assert.assertFalse(passwordValidator.isValid("password"));
        Assert.assertFalse(passwordValidator.isValid("password01"));
        Assert.assertFalse(passwordValidator.isValid("password01*"));
        Assert.assertFalse(passwordValidator.isValid("Password01*"));
        Assert.assertTrue(passwordValidator.isValid("Password0*"));
    }

    @Test
    public void invalidMinLength() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(3, null, null, null, null, null, null);
        Optional<String> result = getValidationErrorKey("AB", passwordPolicy);
        Assertions.assertThat(result).hasValue("invalid password minimum length");
        var statuses = callEvaluate("AB", passwordPolicy, null);
        Assertions.assertThat(statuses).isNotNull();
        Assertions.assertThat(statuses.isValid()).isFalse();
        Assertions.assertThat(statuses.getMinLength()).isNotNull().isFalse();
        Assertions.assertThat(statuses.getLettersInMixedCase()).isNull();
        Assertions.assertThat(statuses.getIncludeNumbers()).isNull();
        Assertions.assertThat(statuses.getIncludeSpecialCharacters()).isNull();
        Assertions.assertThat(statuses.getMaxConsecutiveLetters()).isNull();
        Assertions.assertThat(statuses.getExcludeUserProfileInfoInPassword()).isNull();
        Assertions.assertThat(statuses.getExcludePasswordsInDictionary()).isNull();
    }

    @Test
    public void includeNumber() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(2, true, null, null, null, null, null);
        Assertions.assertThat(getValidationErrorKey("ABC", passwordPolicy)).hasValue("password must contains numbers");

        var statuses = callEvaluate("ABC", passwordPolicy, null);
        Assertions.assertThat(statuses).isNotNull();
        Assertions.assertThat(statuses.isValid()).isFalse();
        Assertions.assertThat(statuses.getMinLength()).isNotNull().isTrue();
        Assertions.assertThat(statuses.getIncludeNumbers()).isNotNull().isFalse();

        Assertions.assertThat(getValidationErrorKey("A234", passwordPolicy)).isEmpty();

        statuses = callEvaluate("A234", passwordPolicy, null);
        Assertions.assertThat(statuses).isNotNull();
        Assertions.assertThat(statuses.isValid()).isTrue();
        Assertions.assertThat(statuses.getMinLength()).isNotNull().isTrue();
        Assertions.assertThat(statuses.getIncludeNumbers()).isNotNull().isTrue();

        Assertions.assertThat(getValidationErrorKey("1234", passwordPolicy)).isEmpty();
    }

    @Test
    public void includeSpecialCharacters() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(3, false, true, null, null, null, null);
        Assertions.assertThat(getValidationErrorKey("AB12", passwordPolicy)).hasValue("password must contains special characters");
        Assertions.assertThat(getValidationErrorKey("1234", passwordPolicy)).hasValue("password must contains special characters");
        Assertions.assertThat(getValidationErrorKey("ABCD", passwordPolicy)).hasValue("password must contains special characters");
        Assertions.assertThat(getValidationErrorKey("A$12", passwordPolicy)).isEmpty();

        var statuses = callEvaluate("AB12", passwordPolicy, null);
        Assertions.assertThat(statuses).isNotNull();
        Assertions.assertThat(statuses.isValid()).isFalse();
        Assertions.assertThat(statuses.getMinLength()).isTrue();
        Assertions.assertThat(statuses.getIncludeNumbers()).isNull();
        Assertions.assertThat(statuses.getIncludeSpecialCharacters()).isFalse();

        statuses = callEvaluate("A$12", passwordPolicy, null);
        Assertions.assertThat(statuses).isNotNull();
        Assertions.assertThat(statuses.isValid()).isTrue();
        Assertions.assertThat(statuses.getMinLength()).isTrue();
        Assertions.assertThat(statuses.getIncludeNumbers()).isNull();
        Assertions.assertThat(statuses.getIncludeSpecialCharacters()).isTrue();
    }

    @Test
    public void lettersInMixedCase() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(3, null, null, true, null, null, null);
        Assertions.assertThat(getValidationErrorKey("ABCD", passwordPolicy)).hasValue("password must contains letters in mixed case");
        Assertions.assertThat(getValidationErrorKey("abcd", passwordPolicy)).hasValue("password must contains letters in mixed case");
        Assertions.assertThat(getValidationErrorKey("ABcd", passwordPolicy)).isEmpty();

        var statuses = callEvaluate("ABCD", passwordPolicy, null);
        Assertions.assertThat(statuses).isNotNull();
        Assertions.assertThat(statuses.isValid()).isFalse();
        Assertions.assertThat(statuses.getMinLength()).isTrue();
        Assertions.assertThat(statuses.getLettersInMixedCase()).isFalse();

        statuses = callEvaluate("AbcD", passwordPolicy, null);
        Assertions.assertThat(statuses).isNotNull();
        Assertions.assertThat(statuses.isValid()).isTrue();
        Assertions.assertThat(statuses.getMinLength()).isTrue();
        Assertions.assertThat(statuses.getLettersInMixedCase()).isTrue();
    }

    @Test
    public void maxConsecutiveLetters() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(3, null, null, false, 3, null, null);
        Assertions.assertThat(getValidationErrorKey("ABBBBCD", passwordPolicy)).hasValue("invalid max consecutive letters");
        Assertions.assertThat(getValidationErrorKey("ABBBcd", passwordPolicy)).isEmpty();

        var statuses = callEvaluate("ABBBBCD", passwordPolicy, null);
        Assertions.assertThat(statuses).isNotNull();
        Assertions.assertThat(statuses.isValid()).isFalse();
        Assertions.assertThat(statuses.getMinLength()).isTrue();
        Assertions.assertThat(statuses.getMaxConsecutiveLetters()).isFalse();

        statuses = callEvaluate("ABBBcd", passwordPolicy, null);
        Assertions.assertThat(statuses).isNotNull();
        Assertions.assertThat(statuses.isValid()).isTrue();
        Assertions.assertThat(statuses.getMinLength()).isTrue();
        Assertions.assertThat(statuses.getMaxConsecutiveLetters()).isTrue();
    }

    @Test
    public void passwordInDictionary() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(3, null, null, false, null, true, null);
        Assertions.assertThat(getValidationErrorKey("trustno1", passwordPolicy)).hasValue("invalid password, try something else");
        Assertions.assertThat(getValidationErrorKey("mY5tR0N9P@SsWoRd!", passwordPolicy)).isEmpty();

        var statuses = callEvaluate("trustno1", passwordPolicy, null);
        Assertions.assertThat(statuses).isNotNull();
        Assertions.assertThat(statuses.isValid()).isFalse();
        Assertions.assertThat(statuses.getMinLength()).isTrue();
        Assertions.assertThat(statuses.getExcludePasswordsInDictionary()).isFalse();

        statuses = callEvaluate("mY5tR0N9P@SsWoRd!", passwordPolicy, null);
        Assertions.assertThat(statuses).isNotNull();
        Assertions.assertThat(statuses.isValid()).isTrue();
        Assertions.assertThat(statuses.getMinLength()).isTrue();
        Assertions.assertThat(statuses.getExcludePasswordsInDictionary()).isTrue();
    }

    @Test
    public void userProfileInPassword_username() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(3, null, null, false, null, null, true);
        User user = new User();
        user.setUsername("myUsername");
        Assertions.assertThat(getValidationErrorKey("MyUsErNaMe-and-a-suffix@@@", passwordPolicy, user)).hasValue("invalid password user profile");
        Assertions.assertThat(getValidationErrorKey("mY5tR0N9P@SsWoRd!", passwordPolicy, user)).isEmpty();

        var statuses = callEvaluate("MyUsErNaMe-and-a-suffix@@@", passwordPolicy, user);
        Assertions.assertThat(statuses).isNotNull();
        Assertions.assertThat(statuses.isValid()).isFalse();
        Assertions.assertThat(statuses.getMinLength()).isTrue();
        Assertions.assertThat(statuses.getExcludeUserProfileInfoInPassword()).isFalse();

        statuses = callEvaluate("mY5tR0N9P@SsWoRd!", passwordPolicy, user);
        Assertions.assertThat(statuses).isNotNull();
        Assertions.assertThat(statuses.isValid()).isTrue();
        Assertions.assertThat(statuses.getMinLength()).isTrue();
        Assertions.assertThat(statuses.getExcludeUserProfileInfoInPassword()).isTrue();
    }

    @Test
    public void userProfileInPassword_firstname() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(3, null, null, false, null, null, true);
        User user = new User();
        user.setFirstName("myFirstname");
        Assertions.assertThat(getValidationErrorKey("SomePaSSwordWith-myFiRsTnAmE", passwordPolicy, user)).hasValue("invalid password user profile");
        Assertions.assertThat(getValidationErrorKey("mY5tR0N9P@SsWoRd!", passwordPolicy, user)).isEmpty();
    }

    @Test
    public void userProfileInPassword_lastname() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(3, null, null, false, null, null, true);
        User user = new User();
        user.setLastName("myLastName");
        Assertions.assertThat(getValidationErrorKey("SomePasswordWith-myLaStNaMe", passwordPolicy, user)).hasValue("invalid password user profile");
        Assertions.assertThat(getValidationErrorKey("mY5tR0N9P@SsWoRd!", passwordPolicy, user)).isEmpty();
    }

    @Test
    public void userProfileInPassword_nickname() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(3, null, null, false, null, null, true);
        User user = new User();
        user.setNickName("myNickName");
        Assertions.assertThat(getValidationErrorKey("SomePasswordWith-myNiCkNaMe", passwordPolicy, user)).hasValue("invalid password user profile");
        Assertions.assertThat(getValidationErrorKey("mY5tR0N9P@SsWoRd!", passwordPolicy, user)).isEmpty();
    }

    @Test
    public void userProfileInPassword_middlename() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(3, null, null, false, null, null, true);
        User user = new User();
        user.setAdditionalInformation(new HashMap<>());
        user.setMiddleName("myMiddleName");
        Assertions.assertThat(getValidationErrorKey("myMiDdLeNaMe-withsomething", passwordPolicy, user)).hasValue("invalid password user profile");
        Assertions.assertThat(getValidationErrorKey("mY5tR0N9P@SsWoRd!", passwordPolicy, user)).isEmpty();
    }


    @Test
    public void userProfileInPassword_email() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(3, null, null, false, null, null, true);
        User user = new User();
        user.setEmail("user@email.com");
        Assertions.assertThat(getValidationErrorKey("uSeR@eMaIl.com", passwordPolicy, user)).hasValue("invalid password user profile");
        Assertions.assertThat(getValidationErrorKey("mY5tR0N9P@SsWoRd!", passwordPolicy, user)).isEmpty();
    }

    @Test
    public void userProfileInPassword_emails() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(3, null, null, false, null, null, true);
        User user = new User();
        user.setAdditionalInformation(new HashMap<>());
        user.putAdditionalInformation(EMAIL, "email1@email.com");
        user.putAdditionalInformation(EMAIL, "email2@email.com");
        Assertions.assertThat(getValidationErrorKey("somePassword-email2@email.com", passwordPolicy, user)).hasValue("invalid password user profile");
        Assertions.assertThat(getValidationErrorKey("mY5tR0N9P@SsWoRd!", passwordPolicy, user)).isEmpty();
    }

    @Test
    public void userProfileInPassword_phonenumbers() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(3, null, null, false, null, null, true);
        User user = new User();
        user.setAdditionalInformation(new HashMap<>());
        user.putAdditionalInformation(PHONE_NUMBER, "0712345678");
        user.putAdditionalInformation(PHONE_NUMBER, "0798765432");
        Assertions.assertThat(getValidationErrorKey("somePassword-0798765432", passwordPolicy, user)).hasValue("invalid password user profile");
        Assertions.assertThat(getValidationErrorKey("mY5tR0N9P@SsWoRd!", passwordPolicy, user)).isEmpty();
    }

    @Test
    public void userProfileInPassword_phonenumber() {
        PasswordPolicy passwordPolicy = buildPasswordPolicy(3, null, null, false, null, null, true);
        User user = new User();
        user.setAdditionalInformation(new HashMap<>());
        user.setPhoneNumber("0712345678");
        Assertions.assertThat(getValidationErrorKey("somePassword-0712345678", passwordPolicy, user)).hasValue("invalid password user profile");
        Assertions.assertThat(getValidationErrorKey("mY5tR0N9P@SsWoRd!", passwordPolicy, user)).isEmpty();
    }

    @Test
    public void checkAccountPasswordExpiry_shouldReturnExpired() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -6);
        User user = new User();
        user.setLastPasswordReset(calendar.getTime());
        PasswordPolicy passwordSettings = new PasswordPolicy();
        passwordSettings.setExpiryDuration(5);

        Assertions.assertThat(passwordService.checkAccountPasswordExpiry(user, passwordSettings)).isTrue();
    }

    @Test
    public void checkAccountPasswordExpiry_nullLastPasswordReset_shouldReturnExpired() {
        User user = new User();
        user.setLastPasswordReset(null);
        PasswordPolicy passwordSettings = new PasswordPolicy();
        passwordSettings.setExpiryDuration(90);

        Assertions.assertThat(passwordService.checkAccountPasswordExpiry(user, passwordSettings)).isTrue();
    }

    @Test
    public void checkAccountPasswordExpiry_shouldNotReturnExpired() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -6);
        User user = new User();
        user.setLastPasswordReset(calendar.getTime());
        PasswordPolicy passwordSettings = new PasswordPolicy();
        passwordSettings.setExpiryDuration(10);

        Assertions.assertThat(passwordService.checkAccountPasswordExpiry(user, passwordSettings)).isFalse();
    }

    @Test
    public void checkAccountPasswordExpiry_noExpirationDefined_shouldNotReturnExpired() {
        User user = new User();
        Assertions.assertThat(passwordService.checkAccountPasswordExpiry(user, null)).isFalse();
    }

    private Optional<String> getValidationErrorKey(String password, PasswordPolicy passwordSettings) {
        return getValidationErrorKey(password, passwordSettings, null);
    }

    private Optional<String> getValidationErrorKey(String password, PasswordPolicy passwordPolicy, User user) {
        try {
            passwordService.validate(password, passwordPolicy, user);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(e.getMessage());
        }
    }

    private PasswordSettingsStatus callEvaluate(String password, PasswordPolicy passwordSettings, User user) {
        return passwordService.evaluate(password, passwordSettings, user);
    }

    private static PasswordPolicy buildPasswordPolicy(Integer minLength,
                                                      Boolean includeNumbers,
                                                      Boolean includeSpecialCharacters,
                                                      Boolean lettersInMixedCase,
                                                      Integer maxConsecutiveLetters,
                                                      Boolean excludePasswordInDictionary,
                                                      Boolean excludeUserProfile
    ) {
        PasswordPolicy passwordSettings = new PasswordPolicy();
        passwordSettings.setMinLength(minLength);
        passwordSettings.setIncludeNumbers(includeNumbers);
        passwordSettings.setIncludeSpecialCharacters(includeSpecialCharacters);
        passwordSettings.setLettersInMixedCase(lettersInMixedCase);
        passwordSettings.setMaxConsecutiveLetters(maxConsecutiveLetters);
        passwordSettings.setExcludePasswordsInDictionary(excludePasswordInDictionary);
        passwordSettings.setExcludeUserProfileInfoInPassword(excludeUserProfile);
        return passwordSettings;
    }
}
