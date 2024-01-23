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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@NoArgsConstructor
public class EnrollmentSettings {
    private Boolean active;
    private Boolean forceEnrollment;
    private Long skipTimeSeconds;
    private MfaEnrollType type;

    public EnrollmentSettings(EnrollmentSettings enrollment) {
        this.forceEnrollment = enrollment.forceEnrollment;
        this.skipTimeSeconds = enrollment.skipTimeSeconds;
        this.active = enrollment.active;
        this.type = enrollment.type;
    }
}
