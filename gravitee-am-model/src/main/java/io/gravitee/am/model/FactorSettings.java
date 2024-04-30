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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FactorSettings {
    private String defaultFactorId;
    private List<ApplicationFactorSettings> applicationFactors = List.of();

    public List<ApplicationFactorSettings> getApplicationFactors() {
        return Objects.requireNonNullElseGet(applicationFactors, List::of);
    }

    public FactorSettings(FactorSettings factorSettings) {
        this.defaultFactorId = factorSettings.defaultFactorId;
        this.applicationFactors = factorSettings.applicationFactors;
    }
}
