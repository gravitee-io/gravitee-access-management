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
package io.gravitee.am.service.exception;

import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.service.validators.password.PasswordSettingsStatus;

/**
 * @author Boualem DJELAILI (boualem.djelaili at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InvalidPasswordException extends InvalidParameterException {

    public static final String INVALID_PASSWORD_VALUE = "invalid_password_value";
    private final String errorKey;

    public InvalidPasswordException(String message) {
        super(message);
        this.errorKey = INVALID_PASSWORD_VALUE;
    }

    private InvalidPasswordException(String message, String errorKey) {
        super(message);
        this.errorKey = errorKey;
    }

    public static InvalidPasswordException of(String message, String errorKey) {
        return new InvalidPasswordException(message, errorKey);
    }
    public static InvalidPasswordException of(String message) {
        return new InvalidPasswordException(message);
    }

    public static InvalidPasswordException of(PasswordSettingsStatus evaluation, PasswordPolicy policy, String errorKey) {
        var message = new StringBuilder("Password doesn't match policy\n");
        if (evaluation.getDefaultPolicy() == Boolean.FALSE) {
            message.append("- Must match the default policy\n");
        }
        if (evaluation.getMinLength() == Boolean.FALSE) {
            message.append("- Must have at least ").append(policy.getMinLength()).append(" characters\n");
        }
        if (evaluation.getIncludeNumbers() == Boolean.FALSE) {
            message.append("- Must contain a number\n");
        }
        if (evaluation.getIncludeSpecialCharacters() == Boolean.FALSE) {
            message.append("- Must contain a special character\n");
        }
        if (evaluation.getLettersInMixedCase() == Boolean.FALSE) {
            message.append("- Must contain a lower- and upper-case letter\n");
        }
        if (evaluation.getMaxConsecutiveLetters() == Boolean.FALSE) {
            message.append("- Can't have any character repeated ").append(policy.getMaxConsecutiveLetters()).append(" times in a row\n");
        }
        if (evaluation.getExcludePasswordsInDictionary() == Boolean.FALSE) {
            message.append("- Can't be a common password\n");
        }
        if (evaluation.getExcludeUserProfileInfoInPassword() == Boolean.FALSE) {
            message.append("- Can't contain information from user's profile\n");
        }
        if (evaluation.getRecentPasswordsNotReused() == Boolean.FALSE) {
            message.append("- Can't be a recent password\n");
        }
        return new InvalidPasswordException(message.toString(), errorKey);
    }

    public String getErrorKey() {
        return errorKey != null ? errorKey : INVALID_PASSWORD_VALUE;
    }

}
