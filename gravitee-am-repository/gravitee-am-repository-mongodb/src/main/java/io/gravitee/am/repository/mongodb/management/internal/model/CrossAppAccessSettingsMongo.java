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

import io.gravitee.am.model.oidc.CrossAppAccessSettings;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * MongoDB representation of the Cross App Access block of a trusted domain.
 */
@Getter
@Setter
public class CrossAppAccessSettingsMongo {

    private boolean enabled;
    private String audience;
    private List<CrossAppAccessResourceServerMongo> resourceServers;
    private String audSubMapping;
    private Map<String, String> scopeMappings;

    public CrossAppAccessSettings convert() {
        CrossAppAccessSettings settings = new CrossAppAccessSettings();
        settings.setEnabled(isEnabled());
        settings.setAudience(getAudience());
        settings.setResourceServers(CrossAppAccessResourceServerMongo.toModelList(getResourceServers()));
        settings.setAudSubMapping(getAudSubMapping());
        settings.setScopeMappings(getScopeMappings());
        return settings;
    }

    public static CrossAppAccessSettingsMongo convert(CrossAppAccessSettings settings) {
        if (settings == null) {
            return null;
        }
        CrossAppAccessSettingsMongo mongo = new CrossAppAccessSettingsMongo();
        mongo.setEnabled(settings.isEnabled());
        mongo.setAudience(settings.getAudience());
        mongo.setResourceServers(CrossAppAccessResourceServerMongo.fromModelList(settings.getResourceServers()));
        mongo.setAudSubMapping(settings.getAudSubMapping());
        mongo.setScopeMappings(settings.getScopeMappings());
        return mongo;
    }
}
