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

import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.EmailFormatInvalidException;
import io.gravitee.am.service.exception.InvalidUserException;
import io.gravitee.am.service.validators.email.EmailValidatorImpl;
import io.gravitee.am.service.validators.user.UserValidator;
import io.gravitee.am.service.validators.user.UserValidatorImpl;
import org.junit.Test;

import static io.gravitee.am.service.validators.email.EmailValidatorImpl.EMAIL_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.NAME_LAX_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.NAME_STRICT_PATTERN;
import static io.gravitee.am.service.validators.user.UserValidatorImpl.USERNAME_PATTERN;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserValidatorTest {

    private final UserValidator userValidator = new UserValidatorImpl(
            NAME_STRICT_PATTERN,
            NAME_LAX_PATTERN,
            USERNAME_PATTERN,
            new EmailValidatorImpl(EMAIL_PATTERN, true)
    );

    @Test
    public void validate() {
        User user = getValidUser();

        userValidator.validate(user).test().assertNoErrors();
    }

    @Test
    public void validate_usernameEmail() {
        User user = getValidUser();
        user.setUsername("user.valid+1-test@gravitee.io");

        userValidator.validate(user).test().assertNoErrors();
    }

    @Test
    public void validateErrorWhenUsernameEmpty() {
        User user = getValidUser();
        user.setUsername("");

        userValidator.validate(user).test().assertError(InvalidUserException.class);
    }

    @Test
    public void validateWhenUsernameNull() {
        User user = getValidUser();
        user.setUsername(null);
        userValidator.validate(user).test().assertNoErrors();
    }

    @Test
    public void validate_displayNameEmail() {
        User user = getValidUser();
        user.setDisplayName("user.valid+1-test@gravitee.io");

        userValidator.validate(user).test().assertNoErrors();
    }

    @Test
    public void validate_usernameHashtag() {
        User user = getValidUser();
        user.setUsername("user#gravitee.io");

        userValidator.validate(user).test().assertNoErrors();
    }

    @Test
    public void validate_invalidEmail() {
        User user = getValidUser();
        user.setEmail("invalid");

        userValidator.validate(user).test().assertError(EmailFormatInvalidException.class);
    }

    @Test
    public void validate_invalidFirstName() {
        User user = getValidUser();
        user.setFirstName("$¨¨^invalid");

        userValidator.validate(user).test().assertError(InvalidUserException.class);
    }

    @Test
    public void validate_invalidLastName() {
        User user = getValidUser();
        user.setLastName("$¨¨^invalid");

        userValidator.validate(user).test().assertError(InvalidUserException.class);
    }

    @Test
    public void validate_invalidNickName() {
        User user = getValidUser();
        user.setNickName("$¨¨^invalid");

        userValidator.validate(user).test().assertError(InvalidUserException.class);
    }

    @Test
    public void validate_invalidDisplayName() {
        User user = getValidUser();
        user.setDisplayName("$¨¨^invalid");

        userValidator.validate(user).test().assertError(InvalidUserException.class);
    }

    @Test
    public void validate_invalidUsername() {
        User user = getValidUser();
        user.setUsername("$¨¨^invalid");

        userValidator.validate(user).test().assertError(InvalidUserException.class);
    }

    private User getValidUser() {
        User user = new User();
        user.setEmail("valid.email@gravitee.io");
        user.setFirstName("valid first name");
        user.setLastName("valid last name");
        user.setNickName("Valid, nick name;");
        user.setDisplayName("Valid, display-name;");
        user.setUsername("valid-@username");

        return user;
    }
}
