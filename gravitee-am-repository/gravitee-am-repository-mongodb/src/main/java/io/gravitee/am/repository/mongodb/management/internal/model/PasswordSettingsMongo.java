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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.model.PasswordSettings;

/**
 * @author Boualem DJELAILI (boualem.djelaili at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordSettingsMongo {

    /**
     * Account settings configuration inherited ?
     */
    private boolean inherited;

    /**
     * password min length
     */
    private Integer minLength;

    /**
     * password max length
     */
    private Integer maxLength;

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
     *  Excludes passwords contained in dictionary
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

    public boolean isInherited() {
        return inherited;
    }

    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public Boolean isIncludeNumbers() {
        return includeNumbers;
    }

    public void setIncludeNumbers(Boolean includeNumbers) {
        this.includeNumbers = includeNumbers;
    }

    public Boolean isIncludeSpecialCharacters() {
        return includeSpecialCharacters;
    }

    public void setIncludeSpecialCharacters(Boolean includeSpecialCharacters) {
        this.includeSpecialCharacters = includeSpecialCharacters;
    }

    public Boolean getLettersInMixedCase() {
        return lettersInMixedCase;
    }

    public void setLettersInMixedCase(Boolean lettersInMixedCase) {
        this.lettersInMixedCase = lettersInMixedCase;
    }

    public Integer getMaxConsecutiveLetters() {
        return maxConsecutiveLetters;
    }

    public void setMaxConsecutiveLetters(Integer maxConsecutiveLetters) {
        this.maxConsecutiveLetters = maxConsecutiveLetters;
    }

    public Boolean isExcludePasswordsInDictionary() {
        return excludePasswordsInDictionary;
    }

    public void setExcludePasswordsInDictionary(Boolean excludePasswordsInDictionary) {
        this.excludePasswordsInDictionary = excludePasswordsInDictionary;
    }

    public Boolean isExcludeUserProfileInfoInPassword() {
        return excludeUserProfileInfoInPassword;
    }

    public void setExcludeUserProfileInfoInPassword(Boolean excludeUserProfileInfoInPassword) {
        this.excludeUserProfileInfoInPassword = excludeUserProfileInfoInPassword;
    }

    public Integer getExpiryDuration() {
        return expiryDuration;
    }

    public void setExpiryDuration(Integer expiryDuration) {
        this.expiryDuration = expiryDuration;
    }

    public boolean isPasswordHistoryEnabled() {
        return passwordHistoryEnabled;
    }

    public void setPasswordHistoryEnabled(boolean passwordHistoryEnabled) {
        this.passwordHistoryEnabled = passwordHistoryEnabled;
    }

    public Short getOldPasswords() {
        return oldPasswords;
    }

    public void setOldPasswords(Short oldPasswords) {
        this.oldPasswords = oldPasswords;
    }

    public PasswordSettings convert() {
        PasswordSettings passwordSettings = new PasswordSettings();
        passwordSettings.setInherited(isInherited());
        passwordSettings.setMinLength(getMinLength());
        passwordSettings.setMaxLength(getMaxLength() == null ? PasswordSettings.PASSWORD_MAX_LENGTH : getMaxLength());
        passwordSettings.setIncludeNumbers(isIncludeNumbers());
        passwordSettings.setIncludeSpecialCharacters(isIncludeSpecialCharacters());
        passwordSettings.setLettersInMixedCase(getLettersInMixedCase());
        passwordSettings.setMaxConsecutiveLetters(getMaxConsecutiveLetters());
        passwordSettings.setExcludePasswordsInDictionary(isExcludePasswordsInDictionary());
        passwordSettings.setExcludeUserProfileInfoInPassword(isExcludeUserProfileInfoInPassword());
        passwordSettings.setExpiryDuration(getExpiryDuration());
        passwordSettings.setPasswordHistoryEnabled(isPasswordHistoryEnabled());
        passwordSettings.setOldPasswords(getOldPasswords());
        return passwordSettings;
    }

    public static PasswordSettingsMongo convert(PasswordSettings other) {
        if (other == null) {
            return null;
        }
        PasswordSettingsMongo passwordSettings = new PasswordSettingsMongo();
        passwordSettings.setInherited(other.isInherited());
        passwordSettings.setMinLength(other.getMinLength());
        passwordSettings.setMaxLength(other.getMaxLength());
        passwordSettings.setIncludeNumbers(other.isIncludeNumbers());
        passwordSettings.setIncludeSpecialCharacters(other.isIncludeSpecialCharacters());
        passwordSettings.setLettersInMixedCase(other.getLettersInMixedCase());
        passwordSettings.setMaxConsecutiveLetters(other.getMaxConsecutiveLetters());
        passwordSettings.setExcludePasswordsInDictionary(other.isExcludePasswordsInDictionary());
        passwordSettings.setExcludeUserProfileInfoInPassword(other.isExcludeUserProfileInfoInPassword());
        passwordSettings.setExpiryDuration(other.getExpiryDuration());
        passwordSettings.setPasswordHistoryEnabled(other.isPasswordHistoryEnabled());
        passwordSettings.setOldPasswords(other.getOldPasswords());
        return passwordSettings;
    }
}
