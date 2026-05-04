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
package io.gravitee.am.gateway.handler.aauth.service.bootstrap;

/**
 * Raised when a bootstrap announcement carries an {@code agent_token.sub} that does not
 * match the {@code agent_identifier} already recorded for the same {@code (userId, agentServerUrl)}
 * pair. Per draft-hardt-aauth-bootstrap §6.6, that mapping is one-to-one — receiving a
 * different identifier indicates a misbehaving Agent Server, not a benign retry.
 */
public class BootstrapBindingConflictException extends RuntimeException {

    private final String userId;
    private final String agentServerUrl;
    private final String existingAgentIdentifier;
    private final String announcedAgentIdentifier;

    public BootstrapBindingConflictException(String userId,
                                             String agentServerUrl,
                                             String existingAgentIdentifier,
                                             String announcedAgentIdentifier) {
        super("Bootstrap binding conflict for (user=" + userId + ", agent_server=" + agentServerUrl
                + "): recorded agent_identifier '" + existingAgentIdentifier
                + "' differs from announced '" + announcedAgentIdentifier + "'");
        this.userId = userId;
        this.agentServerUrl = agentServerUrl;
        this.existingAgentIdentifier = existingAgentIdentifier;
        this.announcedAgentIdentifier = announcedAgentIdentifier;
    }

    public String getUserId() {
        return userId;
    }

    public String getAgentServerUrl() {
        return agentServerUrl;
    }

    public String getExistingAgentIdentifier() {
        return existingAgentIdentifier;
    }

    public String getAnnouncedAgentIdentifier() {
        return announcedAgentIdentifier;
    }
}
