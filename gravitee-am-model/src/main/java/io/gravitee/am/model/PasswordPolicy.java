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
package io.gravitee.am.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@NoArgsConstructor
public class PasswordPolicy implements Resource {
    private String id;
    private String referenceId;
    private ReferenceType referenceType;
    private Date createdAt;
    private Date updatedAt;
    private String name;
    /**
     * Password min length
     */
    private Integer minLength = PasswordSettings.PASSWORD_MIN_LENGTH;

    /**
     * Password max length
     */
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
    private boolean passwordHistoryEnabled;

    /**
     * How many passwords are preserved into the history
     */
    private Short oldPasswords;

    private Boolean defaultPolicy;
}
