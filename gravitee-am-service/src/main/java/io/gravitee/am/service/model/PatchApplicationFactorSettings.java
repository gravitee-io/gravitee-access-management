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
package io.gravitee.am.service.model;

import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.service.utils.SetterUtils;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PatchApplicationFactorSettings {
    private Optional<String> id;
    private Optional<String> selectionRule;

    public ApplicationFactorSettings patch(ApplicationFactorSettings _toPatch) {
        ApplicationFactorSettings toPatch = _toPatch == null ? new ApplicationFactorSettings() : new ApplicationFactorSettings(_toPatch);
        SetterUtils.safeSet(toPatch::setId, getId());
        SetterUtils.safeSet(toPatch::setSelectionRule, getSelectionRule());
        return toPatch;
    }
}
