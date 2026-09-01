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
package io.gravitee.am.service.validators.crossappaccess;

import io.gravitee.am.model.application.ApplicationCrossAppAccessResourceServer;
import io.gravitee.am.model.application.ApplicationCrossAppAccessSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.service.validators.Validator;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Validates the Cross App Access configuration of an application: the shape of its resource server
 * mappings and the ID-JAG lifetime.
 *
 * <p>A mapping whose resource server no longer exists on any trusted domain is accepted. Resolution
 * happens when minting, so an application does not become unsaveable because a resource server was
 * removed at domain level.
 *
 * @author GraviteeSource Team
 */
@Component
public class ApplicationCrossAppAccessValidator implements Validator<ApplicationOAuthSettings, Optional<String>> {

    public static final int MIN_ID_JAG_VALIDITY_SECONDS = 1;

    @Override
    public Optional<String> validate(ApplicationOAuthSettings oauthSettings) {
        if (oauthSettings == null) {
            return Optional.empty();
        }
        ApplicationCrossAppAccessSettings settings = oauthSettings.getCrossAppAccessSettings();
        if (settings == null || !settings.isEnabled()) {
            return Optional.empty();
        }
        if (oauthSettings.getIdJagValiditySeconds() < MIN_ID_JAG_VALIDITY_SECONDS) {
            return Optional.of("idJagValiditySeconds must be at least " + MIN_ID_JAG_VALIDITY_SECONDS);
        }
        return validateResourceServers(settings.getResourceServers());
    }

    private Optional<String> validateResourceServers(List<ApplicationCrossAppAccessResourceServer> resourceServers) {
        if (resourceServers == null || resourceServers.isEmpty()) {
            return Optional.empty();
        }
        Set<String> resourceServerIds = new HashSet<>();
        for (ApplicationCrossAppAccessResourceServer resourceServer : resourceServers) {
            if (resourceServer == null) {
                return Optional.of("crossAppAccessSettings.resourceServers must not contain a null entry");
            }
            if (isBlank(resourceServer.getResourceServerId())) {
                return Optional.of("crossAppAccessSettings.resourceServers entries must reference a resource server");
            }
            if (isBlank(resourceServer.getClientId())) {
                return Optional.of("crossAppAccessSettings.resourceServers entries must have a non-blank clientId");
            }
            if (resourceServer.getClientId().length() > ApplicationCrossAppAccessResourceServer.CLIENT_ID_MAX_LENGTH) {
                return Optional.of("crossAppAccessSettings.resourceServers clientId must be at most "
                        + ApplicationCrossAppAccessResourceServer.CLIENT_ID_MAX_LENGTH + " characters");
            }
            if (!resourceServerIds.add(resourceServer.getResourceServerId())) {
                return Optional.of("crossAppAccessSettings.resourceServers must not repeat resource server "
                        + resourceServer.getResourceServerId());
            }
        }
        return Optional.empty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
