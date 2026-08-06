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
package io.gravitee.am.service.reporter.builder;

import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.command.CommandRequest;
import io.gravitee.am.model.command.CommandStaging;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.reporter.builder.gateway.GatewayAuditBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Audit builder for OpenID Provider Command scheduling (Management API side)
 * and per-client delivery outcomes (gateway side).
 *
 * @author GraviteeSource Team
 */
public class CommandAuditBuilder extends GatewayAuditBuilder<CommandAuditBuilder> {

    private final Map<String, Object> commandNewValue = new HashMap<>();

    public CommandAuditBuilder scheduled(CommandRequest commandRequest) {
        type(EventType.COMMAND_SCHEDULED);
        if (commandRequest.getPrincipalId() != null) {
            setActor(commandRequest.getPrincipalId(), EntityType.USER, commandRequest.getPrincipalUsername(),
                    commandRequest.getPrincipalUsername(), ReferenceType.DOMAIN, commandRequest.getDomainId());
        }
        commandData(commandRequest.getId(), commandRequest.getCommand(), commandRequest.getUserId(), commandRequest.getDomainId());
        return this;
    }

    public CommandAuditBuilder delivered(CommandStaging commandStaging, Client client) {
        type(EventType.COMMAND_DELIVERED);
        return deliveryData(commandStaging, client);
    }

    public CommandAuditBuilder deliveryFailed(CommandStaging commandStaging, Client client, Throwable error) {
        type(EventType.COMMAND_DELIVERY_FAILED);
        throwable(error);
        return deliveryData(commandStaging, client);
    }

    /**
     * Spec-defined benign outcome: the RP answered 409 incompatible_state / unknown
     * because it never provisioned the account. Recorded as a delivery with details.
     */
    public CommandAuditBuilder unknownAccount() {
        commandNewValue.put("accountState", "unknown");
        return this;
    }

    private CommandAuditBuilder deliveryData(CommandStaging commandStaging, Client client) {
        client(client);
        commandData(commandStaging.getId(), commandStaging.getCommand(), commandStaging.getUserId(), commandStaging.getReferenceId());
        commandNewValue.put("commandEndpoint", client.getCommandEndpoint());
        return this;
    }

    private void commandData(String commandId, String command, String userId, String domainId) {
        reference(Reference.domain(domainId));
        setTarget(userId, EntityType.USER, null, userId, ReferenceType.DOMAIN, domainId);
        commandNewValue.put("commandId", commandId);
        commandNewValue.put("command", command);
        setNewValue(commandNewValue);
    }
}
