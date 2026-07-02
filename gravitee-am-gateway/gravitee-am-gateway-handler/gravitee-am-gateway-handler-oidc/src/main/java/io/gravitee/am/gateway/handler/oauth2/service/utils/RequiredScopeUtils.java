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
package io.gravitee.am.gateway.handler.oauth2.service.utils;

import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequiredScopeUtils {

    /**
     * Gets the scope keys flagged as required in the client's scope settings.
     */
    public static Set<String> requiredScopeKeys(Client client) {
        if (client == null || client.getScopeSettings() == null) {
            return Collections.emptySet();
        }
        return client.getScopeSettings().stream()
                .filter(ApplicationScopeSettings::isRequiredScope)
                .map(ApplicationScopeSettings::getScope)
                .collect(Collectors.toSet());
    }
}
