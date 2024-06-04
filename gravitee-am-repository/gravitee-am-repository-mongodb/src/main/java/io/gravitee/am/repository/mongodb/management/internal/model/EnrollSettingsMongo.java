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

import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.MfaEnrollType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.Optional;

import static java.lang.Boolean.TRUE;

@Getter
@Setter
@NoArgsConstructor
public class EnrollSettingsMongo {
    private Boolean active;
    private Boolean forceEnrollment;
    private Long skipTimeSeconds;
    private String enrollmentRule;
    private Boolean enrollmentSkipActive;
    private String enrollmentSkipRule;
    private String type;

    public static EnrollSettingsMongo convert(EnrollSettings enrollment) {
        return Optional.of(enrollment).filter(Objects::nonNull).map(settings -> {
            var enrollmentMongo = new EnrollSettingsMongo();
            enrollmentMongo.setActive(settings.isActive());
            enrollmentMongo.setForceEnrollment(settings.getForceEnrollment());
            enrollmentMongo.setSkipTimeSeconds(settings.getSkipTimeSeconds());
            enrollmentMongo.setEnrollmentRule(settings.getEnrollmentRule());
            enrollmentMongo.setEnrollmentSkipActive(settings.isEnrollmentSkipActive());
            enrollmentMongo.setEnrollmentSkipRule(settings.getEnrollmentSkipRule());
            enrollmentMongo.setType(settings.getType() != null ? settings.getType().name() : null);
            return enrollmentMongo;
        }).orElse(new EnrollSettingsMongo());
    }

    public EnrollSettings convert() {
        var enrollmentSettings = new EnrollSettings();
        enrollmentSettings.setActive(TRUE.equals(getActive()));
        enrollmentSettings.setForceEnrollment(getForceEnrollment());
        enrollmentSettings.setSkipTimeSeconds(getSkipTimeSeconds());
        enrollmentSettings.setEnrollmentRule(getEnrollmentRule());
        enrollmentSettings.setEnrollmentSkipActive(TRUE.equals(getEnrollmentSkipActive()));
        enrollmentSettings.setEnrollmentSkipRule(getEnrollmentSkipRule());
        enrollmentSettings.setType(getType() != null ? MfaEnrollType.valueOf(getType()) : null);
        return enrollmentSettings;
    }
}
