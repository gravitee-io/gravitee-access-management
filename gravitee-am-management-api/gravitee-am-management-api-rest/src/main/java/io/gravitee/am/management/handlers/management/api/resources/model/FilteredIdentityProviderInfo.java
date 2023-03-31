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
package io.gravitee.am.management.handlers.management.api.resources.model;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FilteredIdentityProviderInfo {
    private final String id;
    private final String name;
    private final String type;
    private final boolean system;
    private final boolean external;

    public FilteredIdentityProviderInfo(String id, String name, String type, boolean system, boolean external) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.system = system;
        this.external = external;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isSystem() {
        return system;
    }

    public boolean isExternal() {
        return external;
    }
}
