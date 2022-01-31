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

import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Optional;

/**
 * @author Boualem DJELAILI (boualem.djelaili at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchPasswordSettings {

    private Optional<Boolean> inherited;
    private Optional<Integer> minLength;
    private Optional<Integer> maxLength;
    private Optional<Boolean> includeNumbers;
    private Optional<Boolean> includeSpecialCharacters;
    private Optional<Boolean> lettersInMixedCase;
    private Optional<Integer> maxConsecutiveLetters;
    private Optional<Boolean> excludePasswordsInDictionary;
    private Optional<Boolean> excludeUserProfileInfoInPassword;
    private Optional<Integer> expiryDuration;

    public Optional<Integer> getMinLength() {
        return minLength;
    }

    public void setMinLength(Optional<Integer> minLength) {
        this.minLength = minLength;
    }

    public Optional<Integer> getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Optional<Integer> maxLength) {
        this.maxLength = maxLength;
    }

    public Optional<Boolean> getIncludeNumbers() {
        return includeNumbers;
    }

    public void setIncludeNumbers(Optional<Boolean> includeNumbers) {
        this.includeNumbers = includeNumbers;
    }

    public Optional<Boolean> getIncludeSpecialCharacters() {
        return includeSpecialCharacters;
    }

    public void setIncludeSpecialCharacters(Optional<Boolean> includeSpecialCharacters) {
        this.includeSpecialCharacters = includeSpecialCharacters;
    }

    public Optional<Boolean> getLettersInMixedCase() {
        return lettersInMixedCase;
    }

    public void setLettersInMixedCase(Optional<Boolean> lettersInMixedCase) {
        this.lettersInMixedCase = lettersInMixedCase;
    }

    public Optional<Integer> getMaxConsecutiveLetters() {
        return maxConsecutiveLetters;
    }

    public void setMaxConsecutiveLetters(Optional<Integer> maxConsecutiveLetters) {
        this.maxConsecutiveLetters = maxConsecutiveLetters;
    }

    public Optional<Boolean> getInherited() {
        return inherited;
    }

    public void setInherited(Optional<Boolean> inherited) {
        this.inherited = inherited;
    }

    public Optional<Boolean> getExcludePasswordsInDictionary() {
        return excludePasswordsInDictionary;
    }

    public void setExcludePasswordsInDictionary(Optional<Boolean> excludePasswordsInDictionary) {
        this.excludePasswordsInDictionary = excludePasswordsInDictionary;
    }

    public Optional<Boolean> getExcludeUserProfileInfoInPassword() {
        return excludeUserProfileInfoInPassword;
    }

    public void setExcludeUserProfileInfoInPassword(Optional<Boolean> excludeUserProfileInfoInPassword) {
        this.excludeUserProfileInfoInPassword = excludeUserProfileInfoInPassword;
    }

    public Optional<Integer> getExpiryDuration() {
        return expiryDuration;
    }

    public void setExpiryDuration(Optional<Integer> expiryDuration) {
        this.expiryDuration = expiryDuration;
    }

    public PasswordSettings patch(PasswordSettings _toPatch) {
        // create new object for audit purpose (patch json result)
        PasswordSettings toPatch = Optional.ofNullable(_toPatch).map(PasswordSettings::new).orElseGet(PasswordSettings::new);
        SetterUtils.safeSet(toPatch::setInherited, this.inherited);
        SetterUtils.safeSet(toPatch::setMinLength, this.minLength);
        SetterUtils.safeSet(toPatch::setMaxLength, this.maxLength);
        SetterUtils.safeSet(toPatch::setIncludeNumbers, this.includeNumbers);
        SetterUtils.safeSet(toPatch::setIncludeSpecialCharacters, this.includeSpecialCharacters);
        SetterUtils.safeSet(toPatch::setLettersInMixedCase, this.lettersInMixedCase);
        SetterUtils.safeSet(toPatch::setMaxConsecutiveLetters, this.maxConsecutiveLetters);
        SetterUtils.safeSet(toPatch::setExcludePasswordsInDictionary, this.excludePasswordsInDictionary);
        SetterUtils.safeSet(toPatch::setExcludeUserProfileInfoInPassword, this.excludeUserProfileInfoInPassword);
        SetterUtils.safeSet(toPatch::setExpiryDuration, this.expiryDuration);

        if (toPatch.getMinLength() != null && toPatch.getMaxLength() != null) {
            if (toPatch.getMinLength() > toPatch.getMaxLength()) {
                throw new InvalidParameterException("Min password length must be inferior to max password length");
            }
        }

        return toPatch;
    }
}
