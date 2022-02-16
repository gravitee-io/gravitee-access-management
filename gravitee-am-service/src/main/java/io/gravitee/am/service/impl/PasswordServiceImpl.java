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

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.password.dictionary.PasswordDictionary;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.validators.password.PasswordValidator;
import io.gravitee.am.service.validators.password.impl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Optional;
import java.util.stream.Stream;

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

    public void validate(String password, PasswordSettings passwordSettings, User user) {
        // fallback to default regex
        if (passwordSettings == null) {
            if (!defaultPasswordValidator.validate(password)) {
                throw defaultPasswordValidator.getCause();
            }
        } else {
            // check password settings
            Stream.of(
                    new MaxLengthPasswordValidator(passwordSettings.getMaxLength()),
                    new MinLengthPasswordValidator(passwordSettings.getMinLength()),
                    new IncludeNumbersPasswordValidator(passwordSettings.isIncludeNumbers()),
                    new IncludeSpecialCharactersPasswordValidator(passwordSettings.isIncludeSpecialCharacters()),
                    new MixedCasePasswordValidator(passwordSettings.getLettersInMixedCase()),
                    new ConsecutiveCharacterPasswordValidator(passwordSettings.getMaxConsecutiveLetters()),
                    new DictionaryPasswordValidator(passwordSettings.isExcludePasswordsInDictionary(), passwordDictionary),
                    new UserProfilePasswordValidator(passwordSettings.isExcludeUserProfileInfoInPassword(), user)
            ).filter(not(passwordValidator -> passwordValidator.validate(password)))
            .findFirst().ifPresent(validator -> {
                throw validator.getCause();
            });
        }
    }

    /**
     * Check the user password status
     * @param user Authenticated user
     * @return True if the password has expired or False if not
     */
    public boolean checkAccountPasswordExpiry(User user, Client client, Domain domain) {
        Optional<PasswordSettings> passwordSettings = PasswordSettings.getInstance(client, domain);

        /** If the expiryDate is null or set to 0 so it's disabled */
        if (passwordSettings.isEmpty() ||
                (passwordSettings.get().getExpiryDuration() == null
                                || passwordSettings.get().getExpiryDuration() <= 0)) {
            return false;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(user.getLastPasswordReset());
        calendar.add(Calendar.DAY_OF_MONTH, passwordSettings.get().getExpiryDuration());

        return calendar.compareTo(Calendar.getInstance()) < 0;
    }
}
