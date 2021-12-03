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
package io.gravitee.am.service.validators.email;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * An email validator based on the rules defined by OWASP and excluding '*' and '&' characters.
 * <a href=https://owasp.org/www-community/OWASP_Validation_Regex_Repository>OWASP_Validation_Regex_Repository</a>.
 *
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class EmailValidatorImpl implements EmailValidator {

    public static final int EMAIL_MAX_LENGTH = 320;
    public static final String EMAIL_PATTERN = "^[a-zA-Z0-9_+-]+(?:\\.[a-zA-Z0-9_+-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

    private static final Pattern PATTERN = Pattern.compile(EMAIL_PATTERN);

    /**
     * Validate the email against owasp pattern.
     * Note: <code>null</code> is considered as valid to allow validation of optional email.
     *
     * @param email the email to validate
     * @return <code>true</code> if email is valid, <code>false</code> else.
     */
    @Override
    public Boolean validate(String email) {
        return email == null || (email.length() <= EMAIL_MAX_LENGTH && PATTERN.matcher(email).matches());
    }
}