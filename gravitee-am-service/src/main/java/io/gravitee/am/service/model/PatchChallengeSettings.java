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

import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.MfaChallengeType;
import io.gravitee.am.service.utils.SetterUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Optional;


@Getter
@Setter
@NoArgsConstructor
public class PatchChallengeSettings {
    private Optional<Boolean> active;
    private Optional<String> challengeRule;
    private Optional<MfaChallengeType> type;
    public ChallengeSettings patch(ChallengeSettings _toPatch) {
        var toPatch = _toPatch == null ? new ChallengeSettings() : new ChallengeSettings(_toPatch);
        SetterUtils.safeSet(toPatch::setActive, getActive());
        SetterUtils.safeSet(toPatch::setChallengeRule, getChallengeRule());
        SetterUtils.safeSet(toPatch::setType, getType());
        return toPatch;
    }
}
