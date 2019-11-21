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
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum ManagementPermission implements Permission {
    SETTINGS("SETTINGS", "settings"),
    DOMAIN("DOMAIN", "domain"),
    IDENTITY_PROVIDER("IDENTITY_PROVIDER", "identity_provider"),
    AUDIT("AUDIT", "audit"),
    REPORTER("REPORTER", "reporter"),
    SCOPE("SCOPE", "scope"),
    USER("USER", "user"),
    GROUP("GROUP", "group"),
    ROLE("ROLE", "role"),
    TAG("TAG", "tag"),
    FORM("FORM", "form");

    String name;
    String mask;

    ManagementPermission(String name, String mask) {
        this.name = name;
        this.mask = mask;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getMask() {
        return mask;
    }

    public static List<String> permissions() {
        return Arrays.asList(values()).stream().map(ManagementPermission::getMask).collect(Collectors.toList());
    }

}
