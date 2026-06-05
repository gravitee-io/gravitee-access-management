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

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @author Boualem DJELAILI (boualem.djelaili at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Schema(title = "Password settings", description = "Password policy applied to users of the domain: complexity " +
        "requirements, expiry, and history.")
public class PasswordSettings {

    /**
     * See https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
     */
    public static final int PASSWORD_MAX_LENGTH = 128;
    public static final int PASSWORD_MIN_LENGTH = 8;

    @Schema(description = "Whether these password settings are inherited from a parent scope rather than defined " +
            "here. When true, the other fields are ignored.", defaultValue = "true")
    private boolean inherited = true;

    @Schema(description = "Minimum number of characters a password must contain.", defaultValue = "8")
    private Integer minLength = PASSWORD_MIN_LENGTH;

    @Schema(description = "Maximum number of characters a password may contain.", defaultValue = "128")
    private Integer maxLength = PASSWORD_MAX_LENGTH;

    @Schema(description = "Whether a password must contain at least one number.")
    private Boolean includeNumbers;

    @Schema(description = "Whether a password must contain at least one special character.")
    private Boolean includeSpecialCharacters;

    @Schema(description = "Whether a password must contain both uppercase and lowercase letters.")
    private Boolean lettersInMixedCase;

    @Schema(description = "Maximum number of identical consecutive characters allowed in a password.")
    private Integer maxConsecutiveLetters;

    @Schema(description = "Whether passwords found in a common-password dictionary are rejected.")
    private Boolean excludePasswordsInDictionary;

    @Schema(description = "Whether passwords containing the user's profile information are rejected.")
    private Boolean excludeUserProfileInfoInPassword;

    @Schema(description = "Number of days after which a password expires and must be changed.")
    private Integer expiryDuration;

    @Schema(description = "Whether password history is enforced to prevent reuse of recent passwords.",
            defaultValue = "false")
    private boolean passwordHistoryEnabled;

    @Schema(description = "Number of previous passwords retained in history and barred from reuse.")
    private Short oldPasswords;

    public PasswordSettings() {

    }

    public PasswordSettings(PasswordSettings other) {
        this.inherited = other.inherited;
        this.minLength = other.minLength;
        this.maxLength = other.maxLength;
        this.includeNumbers = other.includeNumbers;
        this.includeSpecialCharacters = other.includeSpecialCharacters;
        this.lettersInMixedCase = other.lettersInMixedCase;
        this.maxConsecutiveLetters = other.maxConsecutiveLetters;
        this.excludePasswordsInDictionary = other.excludePasswordsInDictionary;
        this.excludeUserProfileInfoInPassword = other.excludeUserProfileInfoInPassword;
        this.expiryDuration = other.expiryDuration;
        this.passwordHistoryEnabled = other.passwordHistoryEnabled;
        this.oldPasswords = other.oldPasswords;
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

    public boolean isInherited() {
        return inherited;
    }

    public void setInherited(boolean inherited) {
        this.inherited = inherited;
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

    public PasswordPolicy toPasswordPolicy() {
        var policy = new PasswordPolicy();
        policy.setMinLength(this.minLength);
        policy.setMaxLength(this.maxLength);
        policy.setIncludeNumbers(this.includeNumbers);
        policy.setIncludeSpecialCharacters(this.includeSpecialCharacters);
        policy.setLettersInMixedCase(this.lettersInMixedCase);
        policy.setMaxConsecutiveLetters(this.maxConsecutiveLetters);
        policy.setExcludePasswordsInDictionary(this.excludePasswordsInDictionary);
        policy.setExcludeUserProfileInfoInPassword(this.excludeUserProfileInfoInPassword);
        policy.setExpiryDuration(this.expiryDuration);
        policy.setPasswordHistoryEnabled(this.passwordHistoryEnabled);
        policy.setOldPasswords(this.oldPasswords);
        return policy;
    }
}
