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

import io.gravitee.am.service.validators.email.EmailValidatorImpl;
import org.junit.Test;

import static io.gravitee.am.service.validators.email.EmailValidatorImpl.EMAIL_PATTERN;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailValidatorTest {

    private static final String[] VALID_EMAILS = {
            "email@gravitee.io",
            "firstname.lastname@gravitee.io",
            "email@subdomain.gravitee.io",
            "firstname+lastname@gravitee.io",
            "firstname.lastname+1-test@gravitee.io",
            "1234567890@gravitee.io",
            "email@gravitee-io.com",
            "_______@gravitee.io",
            "firstname-lastname@gravitee.io",
            "firstname-lastname@gravitee.io"
    };

    private static final String[] INVALID_EMAILS = {
            "email",
            "#@%^%#$@#$@#.com",
            "@gravitee.io",
            "Joe Smith <email@gravitee.io>",
            "email@gravitee@gravitee.io",
            ".email@gravitee.io",
            "email.@gravitee.io",
            "email..email@gravitee.io",
            "email@gravitee",
            "email@gravitee",
            "email@gravitee..io"
    };


    private static final String[] VALID_EXTENDED_EMAILS = {
            "émail@gravitee.io",
            "émail@gråvitèê.iø",
            "电子邮件@重力.阿约",
            "めーる@ぐらびてぃー.あよ",
            "メール@グラビティー.アヨ",
            "이메일@중력.아요",
            "почта@гравитация.Айо",
            "ηλεκτρονικόταχυδρομείο@βαρύτητα.ιο"
    };

    @Test
    public void validate() {
        var emailValidator = new EmailValidatorImpl(EMAIL_PATTERN);
        for (String email : VALID_EMAILS) {
            assertTrue(email + " should be valid", emailValidator.validate(email));
        }
    }

    @Test
    public void validate_notValid() {
        var emailValidator = new EmailValidatorImpl(EMAIL_PATTERN);
        for (String email : INVALID_EMAILS) {
            assertFalse(email + " should be invalid", emailValidator.validate(email));
        }
    }

    @Test
    public void validate_extended() {
        var emailValidator = new EmailValidatorImpl("^[\\p{L}0-9_+-]+(?:\\.[\\p{L}0-9_+-]+)*@(?:[\\p{L}0-9-]+\\.)+[\\p{L}]{2,7}$");
        for (String email : VALID_EXTENDED_EMAILS) {
            assertTrue(email + " should be valid", emailValidator.validate(email));
        }
    }
}