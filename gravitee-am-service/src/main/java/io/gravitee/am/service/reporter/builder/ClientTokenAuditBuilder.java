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
import com.google.common.collect.ImmutableMap;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.service.reporter.builder.gateway.GatewayAuditBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static io.gravitee.am.common.audit.EventType.TOKEN_CREATED;
import static io.gravitee.am.common.audit.EventType.TOKEN_REVOKED;
import static java.lang.String.format;
import static org.springframework.util.CollectionUtils.isEmpty;

public class ClientTokenAuditBuilder extends GatewayAuditBuilder<ClientTokenAuditBuilder> {
    private static final String REVOKE_MSG_KEY = "revokedMessage";
    private static final String ACCESS_TOKEN_SUB_ATTRIBUTE_KEY = "accessTokenSubject";
    private final Map<String, Object> tokenNewValue;
    private String accessTokenSubject;

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
            tokenNewValue.put(REVOKE_MSG_KEY, message);
        }
        return this;
    }

    public ClientTokenAuditBuilder accessToken(String tokenId) {
        if (tokenId != null) {
            tokenNewValue.put(TokenTypeHint.ACCESS_TOKEN.name(), tokenId);
        }
        return this;
    }

    public ClientTokenAuditBuilder refreshToken(String tokenId) {
        if (tokenId != null) {
            tokenNewValue.put(TokenTypeHint.REFRESH_TOKEN.name(), tokenId);
        }
        return this;
    }

    public ClientTokenAuditBuilder idTokenFor(User user) {
        if (user != null && user.getId() != null) {
            var userId = user.getId();
            tokenNewValue.put(TokenTypeHint.ID_TOKEN.name(), format("Delivered for sub '%s'", userId));
        }
        return this;
    }

    public ClientTokenAuditBuilder tokenActor(Client client) {
        if (client != null) {
            setActor(client.getId(), EntityType.APPLICATION, client.getClientName(), client.getClientName(), ReferenceType.DOMAIN, client.getDomain());
            super.client(client);
            if(client.getDomain() != null){
                reference(Reference.domain(client.getDomain()));
            }

        }
        return this;
    }

    public ClientTokenAuditBuilder tokenActor(User user) {
        if (user != null) {
            setActor(user.getId(), EntityType.USER, user.getUsername(), user.getDisplayName(), user.getReferenceType(), user.getReferenceId(), user.getExternalId(), user.getSource());
            if (ReferenceType.DOMAIN.equals(user.getReferenceType())) {
                reference(Reference.domain(user.getReferenceId()));

            }
        }
        return this;
    }

    public ClientTokenAuditBuilder tokenTarget(User user) {
        if (user != null) {
            setTarget(user.getId(), EntityType.USER, user.getUsername(), user.getDisplayName(), user.getReferenceType(), user.getReferenceId(), user.getExternalId(), user.getSource());
            if (ReferenceType.DOMAIN.equals(user.getReferenceType())) {
                reference(Reference.domain(user.getReferenceId()));
            }
        }
        return this;
    }

    public ClientTokenAuditBuilder accessTokenSubject(String subject) {
        this.accessTokenSubject = subject;
        return this;
    }


    public ClientTokenAuditBuilder withParams(Supplier<Map<String, Object>> supplier) {
        final var params = supplier.get();
        if (!isEmpty(params)) {
            this.tokenNewValue.putAll(params);
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

    @Override
    protected AuditEntity createTarget() {
        AuditEntity target = super.createTarget();
        if(accessTokenSubject != null){
            HashMap<String, Object> attributes = target.getAttributes() == null ? new HashMap<>() : new HashMap<>(target.getAttributes());
            attributes.put(ACCESS_TOKEN_SUB_ATTRIBUTE_KEY, accessTokenSubject);
            target.setAttributes(ImmutableMap.copyOf(attributes));
        }
        return target;
    }
}
