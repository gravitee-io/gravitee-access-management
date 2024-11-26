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

import lombok.Builder;
import lombok.Getter;

import static org.apache.commons.lang3.BooleanUtils.isNotFalse;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Builder(toBuilder = true)
public class PasswordSettingsStatus {

    private Boolean minLength;

    private Boolean includeNumbers;

    private Boolean includeSpecialCharacters;

    private Boolean lettersInMixedCase;

    private Boolean maxConsecutiveLetters;

    private Boolean excludePasswordsInDictionary;

    private Boolean excludeUserProfileInfoInPassword;

    private Boolean recentPasswordsNotReused;

    public boolean isValid() {
        return isNotFalse(this.minLength) &&
                isNotFalse(this.excludePasswordsInDictionary) &&
                isNotFalse(this.maxConsecutiveLetters) &&
                isNotFalse(this.includeNumbers) &&
                isNotFalse(this.lettersInMixedCase) &&
                isNotFalse(this.excludeUserProfileInfoInPassword) &&
                isNotFalse(this.includeSpecialCharacters) &&
                isNotFalse(this.recentPasswordsNotReused);
    }
}
