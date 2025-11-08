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
package io.gravitee.am.model.permissions;

import java.util.Arrays;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum SystemRole {
    PLATFORM_ADMIN(true),
    ORGANIZATION_PRIMARY_OWNER(false),
    DOMAIN_PRIMARY_OWNER(false),
    ENVIRONMENT_PRIMARY_OWNER(false),
    APPLICATION_PRIMARY_OWNER(false),
    PROTECTED_RESOURCE_PRIMARY_OWNER(false);

    private boolean internalOnly;

    SystemRole(boolean internalOnly) {
        this.internalOnly = internalOnly;
    }

    public boolean isInternalOnly() {
        return internalOnly;
    }

    public static SystemRole fromName(String name) {
        return Arrays.stream(values()).filter(e -> e.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
