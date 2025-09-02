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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.User;
import io.gravitee.am.password.dictionary.PasswordDictionary;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.validators.password.PasswordSettingsStatus;
import io.gravitee.am.service.validators.password.PasswordValidator;
import io.gravitee.am.service.validators.password.impl.ConsecutiveCharacterPasswordValidator;
import io.gravitee.am.service.validators.password.impl.DictionaryPasswordValidator;
import io.gravitee.am.service.validators.password.impl.IncludeNumbersPasswordValidator;
import io.gravitee.am.service.validators.password.impl.IncludeSpecialCharactersPasswordValidator;
import io.gravitee.am.service.validators.password.impl.MaxLengthPasswordValidator;
import io.gravitee.am.service.validators.password.impl.MinLengthPasswordValidator;
import io.gravitee.am.service.validators.password.impl.MixedCasePasswordValidator;
import io.gravitee.am.service.validators.password.impl.UserProfilePasswordValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.stream.Stream;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.function.Predicate.not;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class PasswordServiceImpl implements PasswordService {

    private final PasswordValidator defaultPasswordValidator;
    private final PasswordDictionary passwordDictionary;

    @Autowired
    public PasswordServiceImpl(
            @Qualifier("defaultPasswordValidator") PasswordValidator defaultPasswordValidator,
            PasswordDictionary passwordDictionary
    ) {
        this.defaultPasswordValidator = defaultPasswordValidator;
        this.passwordDictionary = passwordDictionary;
    }

    public void validate(String password, PasswordPolicy passwordPolicy, User user) {
        // fallback to default regex
        if (passwordPolicy == null) {
            if (FALSE.equals(defaultPasswordValidator.validate(password))) {
                throw defaultPasswordValidator.getCause();
            }
        } else {
            // check password settings
            Stream.of(
                    new MaxLengthPasswordValidator(passwordPolicy.getMaxLength()),
                    new MinLengthPasswordValidator(passwordPolicy.getMinLength()),
                    new IncludeNumbersPasswordValidator(TRUE.equals(passwordPolicy.getIncludeNumbers())),
                    new IncludeSpecialCharactersPasswordValidator(TRUE.equals(passwordPolicy.getIncludeSpecialCharacters())),
                    new MixedCasePasswordValidator(passwordPolicy.getLettersInMixedCase()),
                    new ConsecutiveCharacterPasswordValidator(passwordPolicy.getMaxConsecutiveLetters()),
                    new DictionaryPasswordValidator(TRUE.equals(passwordPolicy.getExcludePasswordsInDictionary()), passwordDictionary),
                    new UserProfilePasswordValidator(TRUE.equals(passwordPolicy.getExcludeUserProfileInfoInPassword()), user)
            ).filter(not(passwordValidator -> passwordValidator.validate(password)))
            .findFirst().ifPresent(validator -> {
                throw validator.getCause();
            });
        }
    }

    @Override
    public PasswordSettingsStatus evaluate(String password, PasswordPolicy passwordPolicy, User user) {
        var result = new PasswordSettingsStatus();
        if (password != null && passwordPolicy != null) {
            result.setMinLength(new MinLengthPasswordValidator(passwordPolicy.getMinLength()).validate(password));
            if (TRUE.equals(passwordPolicy.getExcludePasswordsInDictionary())) {
                result.setExcludePasswordsInDictionary(new DictionaryPasswordValidator(passwordPolicy.getExcludePasswordsInDictionary(), passwordDictionary).validate(password));
            }
            if (TRUE.equals(passwordPolicy.getIncludeNumbers())) {
                result.setIncludeNumbers(new IncludeNumbersPasswordValidator(passwordPolicy.getIncludeNumbers()).validate(password));
            }
            if (TRUE.equals(passwordPolicy.getIncludeSpecialCharacters())) {
                result.setIncludeSpecialCharacters(new IncludeSpecialCharactersPasswordValidator(passwordPolicy.getIncludeSpecialCharacters()).validate(password));
            }
            if (TRUE.equals(passwordPolicy.getLettersInMixedCase())) {
                result.setLettersInMixedCase(new MixedCasePasswordValidator(passwordPolicy.getLettersInMixedCase()).validate(password));
            }
            if (passwordPolicy.getMaxConsecutiveLetters() != null) {
                result.setMaxConsecutiveLetters(new ConsecutiveCharacterPasswordValidator(passwordPolicy.getMaxConsecutiveLetters()).validate(password));
            }
            if (TRUE.equals(passwordPolicy.getExcludeUserProfileInfoInPassword())) {
                result.setExcludeUserProfileInfoInPassword(new UserProfilePasswordValidator(passwordPolicy.getExcludeUserProfileInfoInPassword(), user).validate(password));
            }
        }
        return result;
    }

    /**
     * Check the user password status
     * @param user Authenticated user
     * @param passwordPolicy password policy
     * @return True if the password has expired or False if not
     */
    public boolean checkAccountPasswordExpiry(User user, PasswordPolicy passwordPolicy) {

        /** If the expiryDate is null or set to 0 so it's disabled */
        if (passwordPolicy == null ||
                (passwordPolicy.getExpiryDuration() == null
                                || passwordPolicy.getExpiryDuration() <= 0)) {
            return false;
        }

        if (user.getLastPasswordReset() == null) {
            return true;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(user.getLastPasswordReset());
        calendar.add(Calendar.DAY_OF_MONTH, passwordPolicy.getExpiryDuration());

        return calendar.compareTo(Calendar.getInstance()) < 0;
    }
}
