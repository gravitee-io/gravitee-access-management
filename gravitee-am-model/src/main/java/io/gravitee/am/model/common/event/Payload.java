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
package io.gravitee.am.model.common.event;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.command.CommandRequest;
import io.gravitee.am.model.token.RevokeToken;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Payload extends HashMap<String, Object> {

    private static final String ID = "id";
    private static final String REFERENCE_TYPE = "referenceType";
    private static final String REFERENCE_ID = "referenceId";
    private static final String ACTION = "action";
    public static final String REVOKE_TOKEN_DEFINITION = "revokeTokenDef";
    // OpenID Provider Command attributes, kept flat (scalar values only) so the
    // payload survives Mongo/JDBC serialization without dedicated converters
    private static final String COMMAND = "command";
    private static final String COMMAND_USER_ID = "commandUserId";
    private static final String COMMAND_PRINCIPAL_ID = "commandPrincipalId";
    private static final String COMMAND_PRINCIPAL_USERNAME = "commandPrincipalUsername";

    public Payload(String id, Reference reference, Action action) {
        this(id, reference.type(), reference.id(), action);
    }

    public Payload(String id, ReferenceType referenceType, String referenceId, Action action) {
        put(ID, id);
        if(referenceType != null) {
            put(REFERENCE_TYPE, referenceType.name());
        }
        put(REFERENCE_ID, referenceId);
        put(ACTION, action);
    }

    public Payload(Map<? extends String, ?> m) {
        super(m);
    }

    public String getId() {
        return (String) get(ID);
    }

    public ReferenceType getReferenceType() {
        String rawReferenceType = (String) get(REFERENCE_TYPE);
        if (rawReferenceType == null) {
            return null;
        } else {
            return ReferenceType.valueOf(rawReferenceType);
        }
    }

    public String getReferenceId() {
        return (String) get(REFERENCE_ID);
    }

    public Action getAction() {
        return (Action) get(ACTION);
    }

    public RevokeToken getRevokeToken() {
        return (RevokeToken) get(REVOKE_TOKEN_DEFINITION);
    }

    public static Payload from(RevokeToken request) {
        final var data = new HashMap<String, Object>();
        data.put(REFERENCE_TYPE, ReferenceType.DOMAIN.name());
        data.put(REFERENCE_ID, request.getDomainId());
        data.put(ACTION, Action.DELETE);
        data.put(REVOKE_TOKEN_DEFINITION, request);
        return new Payload(data);
    }

    public CommandRequest getCommandRequest() {
        return CommandRequest.builder()
                .id(getId())
                .command((String) get(COMMAND))
                .userId((String) get(COMMAND_USER_ID))
                .domainId(getReferenceId())
                .principalId((String) get(COMMAND_PRINCIPAL_ID))
                .principalUsername((String) get(COMMAND_PRINCIPAL_USERNAME))
                .build();
    }

    public static Payload from(CommandRequest request) {
        final var data = new HashMap<String, Object>();
        // the command id identifies the payload so distinct commands of a same
        // sync window are not coalesced by the gateway event deduplication
        data.put(ID, request.getId());
        data.put(REFERENCE_TYPE, ReferenceType.DOMAIN.name());
        data.put(REFERENCE_ID, request.getDomainId());
        data.put(ACTION, Action.CREATE);
        data.put(COMMAND, request.getCommand());
        data.put(COMMAND_USER_ID, request.getUserId());
        if (request.getPrincipalId() != null) {
            data.put(COMMAND_PRINCIPAL_ID, request.getPrincipalId());
        }
        if (request.getPrincipalUsername() != null) {
            data.put(COMMAND_PRINCIPAL_USERNAME, request.getPrincipalUsername());
        }
        return new Payload(data);
    }
}
