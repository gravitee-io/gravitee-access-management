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

package io.gravitee.am.service.model;


import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.validators.passwordpolicy.CheckLength;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@CheckLength
public abstract class AbstractPasswordPolicy {

    @NotBlank
    private String name;
    /**
     * Password min length
     */
    @Min(0)
    private Integer minLength = PasswordSettings.PASSWORD_MIN_LENGTH;

    /**
     * Password max length
     */
    @Min(0)
    private Integer maxLength = PasswordSettings.PASSWORD_MAX_LENGTH;

    /**
     * Must include numbers
     */
    private Boolean includeNumbers;

    /**
     * Must include special characters
     */
    private Boolean includeSpecialCharacters;

    /**
     * letters in mixed case
     */
    private Boolean lettersInMixedCase;

    /**
     * Max consecutive letters
     */
    private Integer maxConsecutiveLetters;

    /**
     * Excludes passwords contained within password dictionary
     */
    private Boolean excludePasswordsInDictionary;

    /**
     * Excludes user profile information from password
     */
    private Boolean excludeUserProfileInfoInPassword;

    /**
     * The Expiration duration (in days) of a password
     */
    private Integer expiryDuration;

    /**
     * Does the password history is enabled to prevent the usage of old password
     */
    private Boolean passwordHistoryEnabled;

    /**
     * How many passwords are preserved into the history
     */
    private Short oldPasswords;

    public PasswordPolicy toPasswordPolicy(ReferenceType referenceType, String referenceId) {
        final var policy = new PasswordPolicy();
        final var now = new Date();
        policy.setCreatedAt(now);
        policy.setUpdatedAt(now);
        policy.setReferenceId(referenceId);
        policy.setReferenceType(referenceType);

        policy.setName(this.getName());
        policy.setMaxLength(this.getMaxLength());
        policy.setMinLength(this.getMinLength());
        policy.setOldPasswords(this.getOldPasswords());
        policy.setExpiryDuration(this.getExpiryDuration());
        policy.setIncludeNumbers(this.getIncludeNumbers());
        policy.setLettersInMixedCase(this.getLettersInMixedCase());
        policy.setMaxConsecutiveLetters(this.getMaxConsecutiveLetters());
        policy.setPasswordHistoryEnabled(this.getPasswordHistoryEnabled());
        policy.setIncludeSpecialCharacters(this.getIncludeSpecialCharacters());
        policy.setExcludePasswordsInDictionary(this.getExcludePasswordsInDictionary());
        policy.setExcludeUserProfileInfoInPassword(this.getExcludeUserProfileInfoInPassword());
        return policy;
    }
}
