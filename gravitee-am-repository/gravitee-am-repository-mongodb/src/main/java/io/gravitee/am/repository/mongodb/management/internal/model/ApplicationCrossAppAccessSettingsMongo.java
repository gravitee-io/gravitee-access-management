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

import io.gravitee.am.model.application.ApplicationCrossAppAccessSettings;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MongoDB representation of {@link ApplicationCrossAppAccessSettings}.
 */
public class ApplicationCrossAppAccessSettingsMongo {

    private boolean enabled;
    private List<ApplicationCrossAppAccessResourceServerMongo> resourceServers;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<ApplicationCrossAppAccessResourceServerMongo> getResourceServers() {
        return resourceServers;
    }

    public void setResourceServers(List<ApplicationCrossAppAccessResourceServerMongo> resourceServers) {
        this.resourceServers = resourceServers;
    }

    public ApplicationCrossAppAccessSettings convert() {
        ApplicationCrossAppAccessSettings settings = new ApplicationCrossAppAccessSettings();
        settings.setEnabled(isEnabled());
        if (getResourceServers() != null) {
            settings.setResourceServers(getResourceServers().stream()
                    .map(ApplicationCrossAppAccessResourceServerMongo::convert)
                    .collect(Collectors.toList()));
        }
        return settings;
    }

    public static ApplicationCrossAppAccessSettingsMongo convert(ApplicationCrossAppAccessSettings settings) {
        if (settings == null) {
            return null;
        }
        ApplicationCrossAppAccessSettingsMongo mongo = new ApplicationCrossAppAccessSettingsMongo();
        mongo.setEnabled(settings.isEnabled());
        if (settings.getResourceServers() != null) {
            mongo.setResourceServers(settings.getResourceServers().stream()
                    .map(ApplicationCrossAppAccessResourceServerMongo::convert)
                    .collect(Collectors.toList()));
        }
        return mongo;
    }
}
