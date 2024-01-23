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

import java.util.Objects;
import static java.util.Optional.ofNullable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@NoArgsConstructor
public class MFASettings {

    private String loginRule;
    //deprecated since 4.3. use stepUpAuthentication instead
    @Deprecated
    private String stepUpAuthenticationRule;
    private StepUpAuthenticationSettings stepUpAuthentication;
    private String skipStepUpAuthentication;
    private String adaptiveAuthenticationRule;
    private RememberDeviceSettings rememberDevice;
    //deprecated since 4.3. use enroll instead
    @Deprecated
    private EnrollmentSettings enrollment;
    private ChallengeSettings challenge;
    private EnrollSettings enroll;

    public MFASettings(MFASettings other) {
        this.loginRule = other.loginRule;
        this.stepUpAuthenticationRule = other.stepUpAuthenticationRule;
        this.adaptiveAuthenticationRule = other.adaptiveAuthenticationRule;
        this.skipStepUpAuthentication = other.skipStepUpAuthentication;
        this.rememberDevice = ofNullable(other.rememberDevice)
                .filter(Objects::nonNull)
                .map(RememberDeviceSettings::new)
                .orElse(new RememberDeviceSettings());
        this.enrollment = ofNullable(other.enrollment)
                .filter(Objects::nonNull)
                .map(EnrollmentSettings::new)
                .orElse(new EnrollmentSettings());

        this.challenge = ofNullable(other.challenge)
                .filter(Objects::nonNull)
                .map(ChallengeSettings::new)
                .orElse(new ChallengeSettings());
        this.stepUpAuthentication = ofNullable(other.stepUpAuthentication)
                .filter(Objects::nonNull)
                .map(StepUpAuthenticationSettings::new)
                .orElse(new StepUpAuthenticationSettings());
        this.enroll = ofNullable(other.enroll)
                .filter(Objects::nonNull)
                .map(EnrollSettings::new)
                .orElse(new EnrollSettings());
    }
}
