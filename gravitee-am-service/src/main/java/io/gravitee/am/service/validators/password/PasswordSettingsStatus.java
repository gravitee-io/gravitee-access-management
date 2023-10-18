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
package io.gravitee.am.service.validators.password;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordSettingsStatus {

    private Boolean minLength;

    private Boolean includeNumbers;

    private Boolean includeSpecialCharacters;

    private Boolean lettersInMixedCase;

    private Boolean maxConsecutiveLetters;

    private Boolean excludePasswordsInDictionary;

    private Boolean excludeUserProfileInfoInPassword;


    public Boolean getMinLength() {
        return minLength;
    }

    public void setMinLength(Boolean minLength) {
        this.minLength = minLength;
    }

    public Boolean getIncludeNumbers() {
        return includeNumbers;
    }

    public void setIncludeNumbers(Boolean includeNumbers) {
        this.includeNumbers = includeNumbers;
    }

    public Boolean getIncludeSpecialCharacters() {
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

    public Boolean getMaxConsecutiveLetters() {
        return maxConsecutiveLetters;
    }

    public void setMaxConsecutiveLetters(Boolean maxConsecutiveLetters) {
        this.maxConsecutiveLetters = maxConsecutiveLetters;
    }

    public Boolean getExcludePasswordsInDictionary() {
        return excludePasswordsInDictionary;
    }

    public void setExcludePasswordsInDictionary(Boolean excludePasswordsInDictionary) {
        this.excludePasswordsInDictionary = excludePasswordsInDictionary;
    }

    public Boolean getExcludeUserProfileInfoInPassword() {
        return excludeUserProfileInfoInPassword;
    }

    public void setExcludeUserProfileInfoInPassword(Boolean excludeUserProfileInfoInPassword) {
        this.excludeUserProfileInfoInPassword = excludeUserProfileInfoInPassword;
    }

    public boolean isValid() {
        return validConstraint(this.minLength) &&
                validConstraint(this.excludePasswordsInDictionary) &&
                validConstraint(this.maxConsecutiveLetters) &&
                validConstraint(this.includeNumbers) &&
                validConstraint(this.lettersInMixedCase) &&
                validConstraint(this.excludeUserProfileInfoInPassword) &&
                validConstraint(this.includeSpecialCharacters);
    }

    private boolean validConstraint(Boolean constraint) {
        return constraint == null || constraint;
    }
}