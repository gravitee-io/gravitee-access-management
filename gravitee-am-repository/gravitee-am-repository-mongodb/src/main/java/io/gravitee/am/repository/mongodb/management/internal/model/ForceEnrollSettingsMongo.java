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

import io.gravitee.am.model.ForceEnrollSettings;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ForceEnrollSettingsMongo {

    private Boolean active;
    private Long skipTimeSeconds;

    public ForceEnrollSettingsMongo() {
    }

    public ForceEnrollSettingsMongo(ForceEnrollSettingsMongo forceEnrollSettings) {
        this.active = forceEnrollSettings.active;
        this.skipTimeSeconds = forceEnrollSettings.skipTimeSeconds;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Long getSkipTimeSeconds() {
        return skipTimeSeconds;
    }

    public void setSkipTimeSeconds(Long skipTimeSeconds) {
        this.skipTimeSeconds = skipTimeSeconds;
    }

    public static ForceEnrollSettingsMongo convert(ForceEnrollSettings forceEnroll) {
        return Optional.of(forceEnroll).filter(Objects::nonNull).map(settings -> {
            var forceEnrollMongo = new ForceEnrollSettingsMongo();
            forceEnrollMongo.setActive(settings.getActive());
            forceEnrollMongo.setSkipTimeSeconds(settings.getSkipTimeSeconds());
            return forceEnrollMongo;
        }).orElse(new ForceEnrollSettingsMongo());

    }

    public ForceEnrollSettings convert() {
        var forceEnroll = new ForceEnrollSettings();
        forceEnroll.setActive(getActive());
        forceEnroll.setSkipTimeSeconds(getSkipTimeSeconds());
        return forceEnroll;
    }
}
