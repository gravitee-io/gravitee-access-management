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

import java.util.Optional;

/**
 * @author Boualem DJELAILI (boualem.djelaili at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PasswordSettings {

    /**
     * Account settings configuration inherited ?
     */
    private boolean inherited = true;

    /**
     * Password min length
     */
    private Integer minLength;

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

    public PasswordSettings() {

    }

    public PasswordSettings(PasswordSettings other) {
        this.inherited = other.inherited;
        this.minLength = other.minLength;
        this.includeNumbers = other.includeNumbers;
        this.includeSpecialCharacters = other.includeSpecialCharacters;
        this.lettersInMixedCase = other.lettersInMixedCase;
        this.maxConsecutiveLetters = other.maxConsecutiveLetters;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
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

    public static Optional<PasswordSettings> getInstance(PasswordSettingsAware passwordSettingsAware, Domain domain) {
        if (passwordSettingsAware == null) {
            return Optional.ofNullable(domain.getPasswordSettings());
        }

        PasswordSettings passwordSettings = passwordSettingsAware.getPasswordSettings();
        if (passwordSettings != null && !passwordSettings.isInherited()) {
            return Optional.of(passwordSettings);
        }
        return Optional.ofNullable(domain.getPasswordSettings());
    }
}
