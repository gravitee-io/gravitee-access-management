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
package io.gravitee.am.service.idp;

import io.gravitee.am.common.env.CloudProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The storage rule applied to a mongo identity provider that reuses the system cluster.
 * {@link SystemClusterIdpPolicy} enforces it and the console reads it to shape its forms.
 *
 * @author GraviteeSource Team
 */
@Component
public class SystemClusterIdpSettings {

    static final String SYSTEM_CLUSTER_RESTRICTED = "repositories.system-cluster-restricted";

    private final Environment environment;

    public SystemClusterIdpSettings(Environment environment) {
        this.environment = environment;
    }

    /**
     * The platform owns where the users of such a provider are stored: both the database and the
     * users collection come from the platform rather than from the form. The two go together, so
     * that a provider can never end up with a collection the platform named inside a database it
     * did not choose.
     *
     * <p>Enabled in a Gravitee-managed cloud installation, otherwise by system configuration.
     */
    public boolean isRestricted() {
        return CloudProperties.isManagedCloudEnabled(environment)
                || environment.getProperty(SYSTEM_CLUSTER_RESTRICTED, Boolean.class, false);
    }
}
