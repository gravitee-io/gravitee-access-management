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
package io.gravitee.am.service.reporter.builder.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.exception.webauthn.WebAuthnBrowserClientErrorException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnBrowserClientErrorAuditBuilder extends GatewayAuditBuilder<WebAuthnBrowserClientErrorAuditBuilder> {

    public ObjectMapper objectMapper = new ObjectMapper();

    public WebAuthnBrowserClientErrorAuditBuilder() {
        super();
        type(EventType.USER_WEBAUTHN_BROWSER_CLIENT_ERROR);
    }

    public WebAuthnBrowserClientErrorAuditBuilder domain(Domain domain) {
        if (domain != null) {
            reference(Reference.domain(domain.getId()));
        }
        return this;
    }

    public WebAuthnBrowserClientErrorAuditBuilder oauthClient(Client client) {
        if (client != null) {
            client(client);
        }
        return this;
    }

    /**
     * End user when a username is known (login field, register hidden field, MFA challenge).
     */
    public WebAuthnBrowserClientErrorAuditBuilder endUserUsername(String username, Domain domain) {
        if (username != null && !username.isBlank() && domain != null) {
            String u = username.trim();
            setActor(null, EntityType.USER, u, u, ReferenceType.DOMAIN, domain.getId());
        }
        return this;
    }

    public WebAuthnBrowserClientErrorAuditBuilder network(RoutingContext ctx) {
        if (ctx != null) {
            ipAddress(ctx);
            userAgent(ctx);
        }
        return this;
    }

    public WebAuthnBrowserClientErrorAuditBuilder details(
            String phase,
            String operation,
            String category,
            String technicalError,
            String technicalMessage,
            String rpId,
            Long clientTimestamp,
            String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("phase", phase);
        payload.put("operation", operation);
        payload.put("category", category);
        payload.put("technicalError", technicalError != null ? technicalError : "");
        payload.put("technicalMessage", technicalMessage != null ? technicalMessage : "");
        if (rpId != null) {
            payload.put("rpId", rpId);
        }
        if (clientTimestamp != null) {
            payload.put("clientTimestamp", clientTimestamp);
        }
        if (correlationId != null && !correlationId.isEmpty()) {
            payload.put("correlationId", correlationId);
        }
        if (ipAddress != null && !ipAddress.isBlank()) {
            payload.put("remoteAddress", ipAddress);
        }
        if (userAgent != null && !userAgent.isBlank()) {
            payload.put("userAgent", userAgent);
        }
        throwable(new WebAuthnBrowserClientErrorException(new JsonObject(payload).encodePrettily()));
        return this;
    }
}
