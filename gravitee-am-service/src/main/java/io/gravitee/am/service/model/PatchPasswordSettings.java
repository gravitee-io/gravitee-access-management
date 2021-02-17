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

import io.gravitee.am.common.policy.PasswordInclude;
import io.gravitee.am.model.application.PasswordSettings;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Optional;

/**
 * @author Boualem DJELAILI (boualem.djelaili at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchPasswordSettings {

    private Optional<Boolean> regex;
    private Optional<String> regexFormat;
    private Optional<Integer> minLength;
    private Optional<Integer> maxLength;
    private Optional<PasswordInclude> passwordInclude;
    private Optional<Boolean> lettersInMixedCase;
    private Optional<Integer> maxConsecutiveLetters;

    public Optional<Boolean> getRegex() {
        return regex;
    }

    public void setRegex(Optional<Boolean> regex) {
        this.regex = regex;
    }

    public Optional<String> getRegexFormat() {
        return regexFormat;
    }

    public void setRegexFormat(Optional<String> regexFormat) {
        this.regexFormat = regexFormat;
    }

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

    public Optional<PasswordInclude> getPasswordInclude() {
        return passwordInclude;
    }

    public void setPasswordInclude(Optional<PasswordInclude> passwordInclude) {
        this.passwordInclude = passwordInclude;
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

    public PasswordSettings patch(PasswordSettings _toPatch) {
        // create new object for audit purpose (patch json result)
        PasswordSettings toPatch = Optional.ofNullable(_toPatch).map(PasswordSettings::new).orElseGet(PasswordSettings::new);
        SetterUtils.safeSet(toPatch::setRegex, this.regex);
        SetterUtils.safeSet(toPatch::setRegexFormat, this.regexFormat);
        SetterUtils.safeSet(toPatch::setMinLength, this.minLength);
        SetterUtils.safeSet(toPatch::setMaxLength, this.maxLength);
        SetterUtils.safeSet(toPatch::setPasswordInclude, this.passwordInclude);
        SetterUtils.safeSet(toPatch::setLettersInMixedCase, this.lettersInMixedCase);
        SetterUtils.safeSet(toPatch::setMaxConsecutiveLetters, this.maxConsecutiveLetters);
        return toPatch;
    }
}
