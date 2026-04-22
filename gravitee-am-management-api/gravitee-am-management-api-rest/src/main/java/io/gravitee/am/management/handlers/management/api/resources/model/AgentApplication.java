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

import com.fasterxml.jackson.annotation.JsonInclude;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.AgentSettings;
import io.gravitee.am.model.application.ApplicationAdvancedSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Read-only projection of an {@link Application} for the agent-applications list endpoint.
 * Only exposes the fields needed by the agent UI: identity, lifecycle, the OAuth clientId/redirectUris,
 * the agentIdentityMode flag and the whole {@code settings.agent} sub-object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentApplication(
        String id,
        String name,
        String description,
        ApplicationType type,
        boolean enabled,
        Date updatedAt,
        Settings settings) {

    public static AgentApplication of(Application application) {
        return new AgentApplication(
                application.getId(),
                application.getName(),
                application.getDescription(),
                application.getType(),
                application.isEnabled(),
                application.getUpdatedAt(),
                Settings.of(application.getSettings())
        );
    }

    public record Settings(OAuth oauth, Advanced advanced, AgentSettings agent) {
        static Settings of(ApplicationSettings source) {
            if (source == null) {
                return new Settings(null, new Advanced(true), null);
            }
            return new Settings(
                    OAuth.of(source.getOauth()),
                    Advanced.of(source.getAdvanced()),
                    Optional.ofNullable(source.getAgent()).map(AgentSettings::new).orElse(null)
            );
        }
    }

    public record OAuth(String clientId, List<String> redirectUris) {
        static OAuth of(ApplicationOAuthSettings source) {
            if (source == null) {
                return null;
            }
            return new OAuth(source.getClientId(), source.getRedirectUris());
        }
    }

    public record Advanced(boolean agentIdentityMode) {
        static Advanced of(ApplicationAdvancedSettings source) {
            return new Advanced(source != null && source.isAgentIdentityMode());
        }
    }
}
