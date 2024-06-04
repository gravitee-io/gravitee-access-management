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

import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.MfaChallengeType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
public class ChallengeSettingsMongo {
    private Boolean active;
    private String challengeRule;
    private String type;

    public static ChallengeSettingsMongo convert(ChallengeSettings challengeSettings) {
        return Optional.of(challengeSettings).filter(Objects::nonNull).map(settings -> {
            var enrollmentMongo = new ChallengeSettingsMongo();
            enrollmentMongo.setActive(settings.isActive());
            enrollmentMongo.setChallengeRule(settings.getChallengeRule());
            enrollmentMongo.setType(settings.getType() != null ? settings.getType().name() : null);
            return enrollmentMongo;
        }).orElse(new ChallengeSettingsMongo());
    }
    public ChallengeSettings convert() {
        var challengeSettings = new ChallengeSettings();
        challengeSettings.setActive(Boolean.TRUE.equals(getActive()));
        challengeSettings.setChallengeRule(getChallengeRule());
        challengeSettings.setType(getType() != null ? MfaChallengeType.valueOf(getType()) : null);
        return challengeSettings;
    }
}
