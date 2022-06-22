/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.gateway.handler.common.alert;

import org.springframework.beans.factory.annotation.Value;

import java.util.List;

public class RiskAssessment {
    @Value("alerts.risk_assessment.settings.ipReputation.enabled")
    private boolean ipReputationEnabled;
    @Value("alerts.risk_assessment.settings.ipReputation.thresholds.HIGH")
    private Double ipReputationHigh;
    @Value("alerts.risk_assessment.settings.ipReputation.thresholds.LOW")
    private Double ipReputationLow;
    @Value("alerts.risk_assessment.settings.ipReputation.thresholds.MEDIUM")
    private Double ipReputationMedium;
    @Value("alerts.risk_assessment.settings.geoVelocity.enabled")
    private boolean geoVelocityEnabled;
    @Value("alerts.risk_assessment.settings.geoVelocity.thresholds.HIGH")
    private Double geoVelocityHigh;
}
