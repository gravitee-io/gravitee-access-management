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
package io.gravitee.am.repository.mongodb.management;

import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.repository.mongodb.management.internal.model.PasswordSettingsMongo;
/**
 * @author Boualem DJELAILI(boualem.djelaili at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ConversionUtils {

    private ConversionUtils() {

    }

    static PasswordSettingsMongo convert(PasswordSettings other) {
        if (other == null) {
            return null;
        }
        PasswordSettingsMongo passwordSettings = new PasswordSettingsMongo();
        passwordSettings.setInherited(other.isInherited());
        passwordSettings.setRegex(other.getRegex());
        passwordSettings.setRegexFormat(other.getRegexFormat());
        passwordSettings.setMinLength(other.getMinLength());
        passwordSettings.setMaxLength(other.getMaxLength());
        passwordSettings.setPasswordInclude(other.getPasswordInclude());
        passwordSettings.setLettersInMixedCase(other.getLettersInMixedCase());
        passwordSettings.setMaxConsecutiveLetters(other.getMaxConsecutiveLetters());
        return passwordSettings;
    }

    static PasswordSettings convert(PasswordSettingsMongo other) {
        if (other == null) {
            return null;
        }
        PasswordSettings passwordSettings = new PasswordSettings();
        passwordSettings.setInherited(other.isInherited());
        passwordSettings.setRegex(other.getRegex());
        passwordSettings.setRegexFormat(other.getRegexFormat());
        passwordSettings.setMinLength(other.getMinLength());
        passwordSettings.setMaxLength(other.getMaxLength());
        passwordSettings.setPasswordInclude(other.getPasswordInclude());
        passwordSettings.setLettersInMixedCase(other.getLettersInMixedCase());
        passwordSettings.setMaxConsecutiveLetters(other.getMaxConsecutiveLetters());
        return passwordSettings;
    }
}
