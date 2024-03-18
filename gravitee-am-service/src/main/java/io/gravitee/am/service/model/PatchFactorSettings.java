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
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.utils.SetterUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import static org.springframework.util.StringUtils.hasText;

@Getter
@Setter
@NoArgsConstructor
public class PatchFactorSettings {
    private Optional<String> defaultFactorId;
    private Optional<List<PatchApplicationFactorSettings>> applicationFactors;

    public FactorSettings patch(FactorSettings _toPatch) {
        FactorSettings toPatch = _toPatch == null ? new FactorSettings() : new FactorSettings(_toPatch);
        SetterUtils.safeSet(toPatch::setDefaultFactorId, getDefaultFactorId());
        getApplicationFactors().ifPresent(patchApplicationFactorSettings -> toPatch.setApplicationFactors(
                applicationFactorsPath(toPatch.getApplicationFactors(), patchApplicationFactorSettings)
        ));
        boolean hasFactors = toPatch.getApplicationFactors() != null && !toPatch.getApplicationFactors().isEmpty();
        if(hasFactors && !hasText(toPatch.getDefaultFactorId())){
            throw new InvalidParameterException("Default factor is not set");
        }
        return toPatch;
    }

    private List<ApplicationFactorSettings> applicationFactorsPath(List<ApplicationFactorSettings> toPath, List<PatchApplicationFactorSettings> appFactors) {
        var newFactors = new ArrayList<ApplicationFactorSettings>(appFactors.size());
        for (PatchApplicationFactorSettings p : appFactors) {
            var appFactor = toPath.stream().filter(i -> p.getId().isPresent() && i.getId().equals(p.getId().get())).findFirst().orElse(null);
            newFactors.add(p.patch(appFactor));
        }
        return newFactors;
    }
}
