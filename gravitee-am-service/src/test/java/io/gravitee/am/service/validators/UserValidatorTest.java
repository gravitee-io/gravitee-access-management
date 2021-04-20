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

import static org.junit.Assert.*;

import io.gravitee.am.model.User;
import io.gravitee.am.service.exception.EmailFormatInvalidException;
import io.gravitee.am.service.exception.InvalidUserException;
import org.junit.Test;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserValidatorTest {

    @Test
    public void validate() {
        User user = getValidUser();

        Throwable throwable = UserValidator.validate(user).blockingGet();

        assertNull(throwable);
    }

    @Test
    public void validate_usernameEmail() {
        User user = getValidUser();
        user.setUsername("user.valid+1-test@gravitee.io");

        Throwable throwable = UserValidator.validate(user).blockingGet();

        assertNull(throwable);
    }

    @Test
    public void validate_displayNameEmail() {
        User user = getValidUser();
        user.setDisplayName("user.valid+1-test@gravitee.io");

        Throwable throwable = UserValidator.validate(user).blockingGet();

        assertNull(throwable);
    }

    @Test
    public void validate_usernameHashtag() {
        User user = getValidUser();
        user.setUsername("user#gravitee.io");

        Throwable throwable = UserValidator.validate(user).blockingGet();

        assertNull(throwable);
    }

    @Test
    public void validate_invalidEmail() {
        User user = getValidUser();
        user.setEmail("invalid");

        Throwable throwable = UserValidator.validate(user).blockingGet();

        assertNotNull(throwable);
        assertTrue(throwable instanceof EmailFormatInvalidException);
    }

    @Test
    public void validate_invalidFirstName() {
        User user = getValidUser();
        user.setFirstName("$¨¨^invalid");

        Throwable throwable = UserValidator.validate(user).blockingGet();

        assertNotNull(throwable);
        assertTrue(throwable instanceof InvalidUserException);
    }

    @Test
    public void validate_invalidLastName() {
        User user = getValidUser();
        user.setLastName("$¨¨^invalid");

        Throwable throwable = UserValidator.validate(user).blockingGet();

        assertNotNull(throwable);
        assertTrue(throwable instanceof InvalidUserException);
    }

    @Test
    public void validate_invalidNickName() {
        User user = getValidUser();
        user.setNickName("$¨¨^invalid");

        Throwable throwable = UserValidator.validate(user).blockingGet();

        assertNotNull(throwable);
        assertTrue(throwable instanceof InvalidUserException);
    }

    @Test
    public void validate_invalidDisplayName() {
        User user = getValidUser();
        user.setDisplayName("$¨¨^invalid");

        Throwable throwable = UserValidator.validate(user).blockingGet();

        assertNotNull(throwable);
        assertTrue(throwable instanceof InvalidUserException);
    }

    @Test
    public void validate_invalidUsername() {
        User user = getValidUser();
        user.setUsername("$¨¨^invalid");

        Throwable throwable = UserValidator.validate(user).blockingGet();

        assertNotNull(throwable);
        assertTrue(throwable instanceof InvalidUserException);
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
