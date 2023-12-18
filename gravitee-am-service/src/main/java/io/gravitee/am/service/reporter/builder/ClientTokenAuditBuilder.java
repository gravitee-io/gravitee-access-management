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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EntityType;
import static io.gravitee.am.common.audit.EventType.TOKEN_CREATED;
import static io.gravitee.am.common.audit.EventType.TOKEN_REVOKED;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;
import java.util.HashMap;
import java.util.Map;

public class ClientTokenAuditBuilder extends AuditBuilder<ClientTokenAuditBuilder> {
    private static final String ADDITIONAL_INFO = "additionalInfo";
    private final Map<String, Map<String, String>> tokenNewValue;

    public ClientTokenAuditBuilder() {
        super();
        tokenNewValue = new HashMap<>();
        type(TOKEN_CREATED);
    }

    public ClientTokenAuditBuilder revoked() {
        revoked(null);
        return this;
    }

    public ClientTokenAuditBuilder revoked(String message) {
        type(TOKEN_REVOKED);
        if (message != null) {
            var additionalInfo = tokenNewValue.get(ADDITIONAL_INFO) != null ? tokenNewValue.get(ADDITIONAL_INFO) : new HashMap<String, String>();
            additionalInfo.put("revokedMessage", message);
            tokenNewValue.put(ADDITIONAL_INFO, additionalInfo);
        }
        return this;
    }

    public ClientTokenAuditBuilder token(TokenTypeHint tokenTypeHint, String tokenId) {
        if (tokenId != null) {
            var entry = new HashMap<String, String>();
            entry.put("tokenId", tokenId);
            if (tokenTypeHint != null) {
                entry.put("tokenType", tokenTypeHint.name());
            }
            tokenNewValue.put(tokenId, entry);
        }
        return this;
    }

    public ClientTokenAuditBuilder token(TokenTypeHint tokenTypeHint, User user) {
        if (user != null && user.getId() != null) {
            var entry = new HashMap<String, String>();
            var userId = user.getId();
            entry.put("userId", userId);
            if (tokenTypeHint != null) {
                entry.put("tokenType", tokenTypeHint.name());
            }
            tokenNewValue.put(userId, entry);
        }
        return this;
    }

    public ClientTokenAuditBuilder tokenActor(Client client) {
        if (client != null) {
            setActor(client.getId(), EntityType.APPLICATION, client.getClientName(), client.getClientName(), ReferenceType.DOMAIN, client.getDomain());
            super.client(client);
            super.domain(client.getDomain());
        }
        return this;
    }

    public ClientTokenAuditBuilder tokenTarget(User user) {
        if (user != null) {
            setTarget(user.getId(), EntityType.USER, user.getUsername(), user.getDisplayName(), user.getReferenceType(), user.getReferenceId());
            if (ReferenceType.DOMAIN.equals(user.getReferenceType())) {
                super.domain(user.getReferenceId());
            }
        }
        return this;
    }

    @Override
    public Audit build(ObjectMapper mapper) {
        if (!tokenNewValue.isEmpty()) {
            setNewValue(tokenNewValue);
        }
        return super.build(mapper);
    }
}
