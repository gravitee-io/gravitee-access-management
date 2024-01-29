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

import io.gravitee.am.model.StepUpAuthenticationSettings;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StepUpAuthenticationMongoSettings {
    private Boolean active;
    private String stepUpAuthenticationRule;

    public StepUpAuthenticationSettings convert() {
        var stepUpAuthenticationSettings = new StepUpAuthenticationSettings();
        stepUpAuthenticationSettings.setActive(stepUpAuthenticationSettings.getActive());
        stepUpAuthenticationSettings.setStepUpAuthenticationRule(stepUpAuthenticationSettings.getStepUpAuthenticationRule());
        return stepUpAuthenticationSettings;
    }

    public static StepUpAuthenticationMongoSettings convert(StepUpAuthenticationSettings stepUpAuthenticationSettings) {
        var stepUpAuthenticationMongoSettings = new StepUpAuthenticationMongoSettings();
        stepUpAuthenticationMongoSettings.setActive(stepUpAuthenticationSettings.getActive());
        stepUpAuthenticationMongoSettings.setStepUpAuthenticationRule(stepUpAuthenticationSettings.getStepUpAuthenticationRule());
        return stepUpAuthenticationMongoSettings;
    }
}
