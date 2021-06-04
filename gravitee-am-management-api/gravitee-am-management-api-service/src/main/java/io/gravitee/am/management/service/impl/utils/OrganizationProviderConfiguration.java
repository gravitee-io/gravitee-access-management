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
package io.gravitee.am.management.service.impl.utils;

import io.gravitee.am.model.IdentityProvider;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class OrganizationProviderConfiguration {
    private final String type;

    private final String name;

    private final boolean enabled;

    public OrganizationProviderConfiguration(String type, Environment env, int index) {
        this.type = type;
        final String propertyBase = getPropertyBase(index);
        this.name = env.getProperty(propertyBase + "name", StringUtils.capitalize(this.type) + " users");
        this.enabled = env.getProperty(propertyBase + "enabled", boolean.class, false);
    }

    protected String getPropertyBase(int index) {
        return "security.providers[" + index + "].";
    }

    public abstract IdentityProvider buildIdentityProvider();

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
