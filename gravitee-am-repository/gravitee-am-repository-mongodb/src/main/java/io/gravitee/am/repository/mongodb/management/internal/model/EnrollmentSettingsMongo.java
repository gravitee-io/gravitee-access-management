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

import io.gravitee.am.model.EnrollmentSettings;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EnrollmentSettingsMongo {

    private Boolean forceEnrollment;
    private Long skipTimeSeconds;

    public EnrollmentSettingsMongo() {
    }

    public EnrollmentSettingsMongo(EnrollmentSettingsMongo other) {
        this.forceEnrollment = other.forceEnrollment;
        this.skipTimeSeconds = other.skipTimeSeconds;
    }

    public Boolean getForceEnrollment() {
        return forceEnrollment;
    }

    public void setForceEnrollment(Boolean forceEnrollment) {
        this.forceEnrollment = forceEnrollment;
    }

    public Long getSkipTimeSeconds() {
        return skipTimeSeconds;
    }

    public void setSkipTimeSeconds(Long skipTimeSeconds) {
        this.skipTimeSeconds = skipTimeSeconds;
    }

    public static EnrollmentSettingsMongo convert(EnrollmentSettings enrollment) {
        return Optional.of(enrollment).filter(Objects::nonNull).map(settings -> {
            var enrollmentMongo = new EnrollmentSettingsMongo();
            enrollmentMongo.setForceEnrollment(settings.getForceEnrollment());
            enrollmentMongo.setSkipTimeSeconds(settings.getSkipTimeSeconds());
            return enrollmentMongo;
        }).orElse(new EnrollmentSettingsMongo());

    }

    public EnrollmentSettings convert() {
        var enrollmentSettings = new EnrollmentSettings();
        enrollmentSettings.setForceEnrollment(getForceEnrollment());
        enrollmentSettings.setSkipTimeSeconds(getSkipTimeSeconds());
        return enrollmentSettings;
    }
}
