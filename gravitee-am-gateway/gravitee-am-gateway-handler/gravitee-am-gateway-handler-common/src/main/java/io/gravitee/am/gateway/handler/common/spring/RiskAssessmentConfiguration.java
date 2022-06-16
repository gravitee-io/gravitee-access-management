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

package io.gravitee.am.gateway.handler.common.spring;

import io.gravitee.risk.assessment.api.assessment.Assessment;
import io.gravitee.risk.assessment.api.assessment.settings.AssessmentSettings;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class RiskAssessmentConfiguration {

    private static final String RISK_ASSESSMENT_SETTINGS = "alerts.risk_assessment.settings";
    private static final String DEVICES_PREFIX = RISK_ASSESSMENT_SETTINGS + ".devices";
    private static final String IP_REPUTATION_PREFIX = RISK_ASSESSMENT_SETTINGS + ".ipReputation";
    private static final String GEO_VELOCITY_PREFIX = RISK_ASSESSMENT_SETTINGS + ".geoVelocity";

    private static final Map<String, Map<Assessment, Double>> DEFAULT_THRESHOLDS = Map.of(
            DEVICES_PREFIX, Map.of(Assessment.HIGH, 1.0D), // Arbitrary Value
            IP_REPUTATION_PREFIX, Map.of(Assessment.LOW, 1.0D), // Percentage
            GEO_VELOCITY_PREFIX, Map.of(Assessment.LOW, (5.0 / 18.0)) // 1km/h
    );

    @Bean
    public RiskAssessmentSettings riskAssessmentSettings(Environment environment) {
        return new RiskAssessmentSettings()
                .setEnabled(environment.getProperty(RISK_ASSESSMENT_SETTINGS + ".enabled", Boolean.class, true))
                .setDeviceAssessment(getAssessment(environment, DEVICES_PREFIX))
                .setIpReputationAssessment(getAssessment(environment, IP_REPUTATION_PREFIX))
                .setGeoVelocityAssessment(getAssessment(environment, GEO_VELOCITY_PREFIX));
    }

    private AssessmentSettings getAssessment(Environment environment, String propertyPrefix) {
        var thresholds = Arrays.stream(Assessment.values())
                .map(getThreshold(environment, propertyPrefix))
                .filter(entry -> entry.getValue().isPresent())
                .map(entry -> Map.entry(entry.getKey(), entry.getValue().get()))
                .collect(toMap(Entry::getKey, Entry::getValue));

        return new AssessmentSettings()
                .setEnabled(environment.getProperty(propertyPrefix + ".enabled", Boolean.class, true))
                .setThresholds(thresholds.isEmpty() ? DEFAULT_THRESHOLDS.get(propertyPrefix) : thresholds);
    }

    private Function<Assessment, Entry<Assessment, Optional<Double>>> getThreshold(Environment env, String prefix) {
        return assessment -> {
            final String key = prefix + ".thresholds." + assessment.name();
            final Double threshold = env.getProperty(key, Double.class);
            return Map.entry(assessment, ofNullable(threshold));
        };
    }
}
