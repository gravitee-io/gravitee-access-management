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
package io.gravitee.am.common.env;

import org.springframework.core.env.Environment;

/**
 * Access to the cloud deployment properties of the node.
 *
 * @author GraviteeSource Team
 */
public final class CloudProperties {

    private CloudProperties() {
    }

    /**
     * @return whether this node is part of a Gravitee-managed cloud deployment.
     */
    public static boolean isManagedCloudEnabled(Environment environment) {
        return isCloudEnabled(environment) && isManagedInstallation(environment);
    }

    private static boolean isCloudEnabled(Environment environment) {
        final Boolean cloudEnabled = environment.getProperty("cloud.enabled", Boolean.class);
        if (cloudEnabled != null) {
            return cloudEnabled;
        }
        return environment.getProperty("cockpit.enabled", Boolean.class, false);
    }

    private static boolean isManagedInstallation(Environment environment) {
        return "managed".equalsIgnoreCase(environment.getProperty("installation.type", "standalone"));
    }
}
