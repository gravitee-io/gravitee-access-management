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

import io.gravitee.risk.assessment.api.assessment.Assessment;
import io.gravitee.risk.assessment.api.assessment.settings.AssessmentSettings;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static io.gravitee.risk.assessment.api.assessment.Assessment.valueOf;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AssessmentSettingsMongo {

    private boolean enabled;
    private Map<String, Double> thresholds;

    public boolean isEnabled() {
        return enabled;
    }

    public Map<String, Double> getThresholds() {
        return thresholds;
    }

    public AssessmentSettingsMongo setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public AssessmentSettingsMongo setThresholds(Map<String, Double> thresholds) {
        this.thresholds = thresholds;
        return this;
    }

    public static AssessmentSettingsMongo convert(AssessmentSettings riskAssessment) {
        if (isNull(riskAssessment)) {
            return null;
        }
        return new AssessmentSettingsMongo()
                .setEnabled(riskAssessment.isEnabled())
                .setThresholds(getMongoThresholds(riskAssessment));
    }

    private static Map<String, Double> getMongoThresholds(AssessmentSettings riskAssessment) {
        return ofNullable(riskAssessment.getThresholds()).orElse(Map.of()).entrySet().stream()
                .map(e -> Map.entry(e.getKey().name(), e.getValue()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    public AssessmentSettings convert() {
        return new AssessmentSettings().setEnabled(this.isEnabled()).setThresholds(this.getRiskThresholds());
    }

    private Map<Assessment, Double> getRiskThresholds() {
        return ofNullable(this.getThresholds()).orElse(Map.of()).entrySet().stream()
                .map(e -> Map.entry(valueOf(e.getKey()), e.getValue()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }
}
