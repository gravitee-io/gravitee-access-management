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

package io.gravitee.am.service.impl;

import io.gravitee.am.common.policy.PasswordInclude;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import org.junit.Test;

/**
 * @author Boualem DJELAILI (boualem.djelaili at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationServiceImplTest {

    @Test
    public void passwordSetting_validateRegexNull() {
        Application application = buildApplicationWithPasswordSettings(null, null, null, null, null, null, null);
        ApplicationServiceImpl.validatePasswordSettings(application)
                .test()
                .assertNotComplete()
                .assertErrorMessage("'regex' field must not be null");
    }

    @Test
    public void passwordSetting_validateRegexInput() {
        Application application = buildApplicationWithPasswordSettings(false, null, 9, 6, null, null, null);
        ApplicationServiceImpl.validatePasswordSettings(application)
                .test()
                .assertNotComplete()
                .assertErrorMessage("Password max length must be greater than min length");
    }

    private static Application buildApplicationWithPasswordSettings(Boolean regex,
                                                                    String regexFormat,
                                                                    Integer minLength,
                                                                    Integer maxLength,
                                                                    PasswordInclude passwordInclude,
                                                                    Boolean lettersInMixedCase,
                                                                    Integer maxConsecutiveLetters) {
        Application application = new Application();
        ApplicationSettings settings = new ApplicationSettings();
        application.setSettings(settings);
        PasswordSettings passwordSettings = new PasswordSettings();
        settings.setPasswordSettings(passwordSettings);
        passwordSettings.setRegex(regex);
        passwordSettings.setRegexFormat(regexFormat);
        passwordSettings.setMinLength(minLength);
        passwordSettings.setMaxLength(maxLength);
        passwordSettings.setPasswordInclude(passwordInclude);
        passwordSettings.setLettersInMixedCase(lettersInMixedCase);
        passwordSettings.setMaxConsecutiveLetters(maxConsecutiveLetters);
        return application;
    }
}