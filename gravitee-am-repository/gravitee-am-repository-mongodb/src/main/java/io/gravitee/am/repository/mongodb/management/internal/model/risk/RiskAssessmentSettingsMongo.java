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

package io.gravitee.am.repository.mongodb.management.internal.model.risk;

import io.gravitee.risk.assessment.api.assessment.settings.AssessmentSettings;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RiskAssessmentSettingsMongo {

    private boolean enabled;

    private AssessmentSettingsMongo deviceAssessment;
    private AssessmentSettingsMongo ipReputationAssessment;
    private AssessmentSettingsMongo geoVelocityAssessment;

    public boolean isEnabled() {
        return enabled;
    }

    public AssessmentSettingsMongo getDeviceAssessment() {
        return deviceAssessment;
    }

    public AssessmentSettingsMongo getIpReputationAssessment() {
        return ipReputationAssessment;
    }

    public AssessmentSettingsMongo getGeoVelocityAssessment() {
        return geoVelocityAssessment;
    }

    public RiskAssessmentSettingsMongo setDeviceAssessment(AssessmentSettingsMongo deviceAssessment) {
        this.deviceAssessment = deviceAssessment;
        return this;
    }

    public RiskAssessmentSettingsMongo setIpReputationAssessment(AssessmentSettingsMongo ipReputationAssessment) {
        this.ipReputationAssessment = ipReputationAssessment;
        return this;
    }

    public RiskAssessmentSettingsMongo setGeoVelocityAssessment(AssessmentSettingsMongo geoVelocityAssessment) {
        this.geoVelocityAssessment = geoVelocityAssessment;
        return this;
    }

    public RiskAssessmentSettingsMongo setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public static RiskAssessmentSettingsMongo convert(RiskAssessmentSettings riskAssessment) {
        if (isNull(riskAssessment)) {
            return null;
        }
        return new RiskAssessmentSettingsMongo()
                .setEnabled(riskAssessment.isEnabled())
                .setDeviceAssessment(AssessmentSettingsMongo.convert(riskAssessment.getDeviceAssessment()))
                .setGeoVelocityAssessment(AssessmentSettingsMongo.convert(riskAssessment.getGeoVelocityAssessment()))
                .setIpReputationAssessment(AssessmentSettingsMongo.convert(riskAssessment.getIpReputationAssessment()));
    }

    public RiskAssessmentSettings convert() {
        return new RiskAssessmentSettings()
                .setEnabled(this.isEnabled())
                .setDeviceAssessment(convertAssessement(getDeviceAssessment()))
                .setGeoVelocityAssessment(convertAssessement(getGeoVelocityAssessment()))
                .setIpReputationAssessment(convertAssessement(getIpReputationAssessment()));
    }

    private AssessmentSettings convertAssessement(AssessmentSettingsMongo deviceAssessment) {
        return ofNullable(deviceAssessment).map(AssessmentSettingsMongo::convert).orElse(null);
    }
}
