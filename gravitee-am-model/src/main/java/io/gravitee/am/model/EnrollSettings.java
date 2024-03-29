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
 * @author Ashraful HASAN (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@NoArgsConstructor
public class EnrollSettings {
    private boolean active;
    private Boolean forceEnrollment;
    private String enrollmentRule;
    private boolean enrollmentSkipActive;
    private String enrollmentSkipRule;
    private Long skipTimeSeconds;
    private MfaEnrollType type;

    public EnrollSettings(EnrollSettings enroll) {
        this.forceEnrollment = enroll.forceEnrollment;
        this.skipTimeSeconds = enroll.skipTimeSeconds;
        this.active = enroll.active;
        this.enrollmentRule = enroll.enrollmentRule;
        this.enrollmentSkipActive = enroll.enrollmentSkipActive;
        this.enrollmentSkipRule = enroll.enrollmentSkipRule;
        this.type = enroll.type;
    }
}
