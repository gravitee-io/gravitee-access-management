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
package io.gravitee.am.service.reporter.builder.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.gravitee.am.common.audit.EventType.USER_CREATED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagementAuditBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldUsePrincipalAsActorWhenPresent() {
        var principal = new DefaultUser("john");
        principal.setId("user-id");
        principal.setAdditionalInformation(Map.of(Claims.DOMAIN, "domain-id"));

        var audit = AuditBuilder.builder(UserAuditBuilder.class)
                .type(USER_CREATED)
                .actor(principal, client())
                .build(objectMapper);

        assertEquals("user-id", audit.getActor().getId());
        assertEquals(EntityType.USER, audit.getActor().getType());
        assertEquals("john", audit.getActor().getAlternativeId());
        assertEquals(ReferenceType.DOMAIN, audit.getActor().getReferenceType());
        assertEquals("domain-id", audit.getActor().getReferenceId());
        assertEquals("app-id", audit.getAccessPoint().getId());
        assertEquals("client-id", audit.getAccessPoint().getAlternativeId());
        assertEquals("client-name", audit.getAccessPoint().getDisplayName());
    }

    @Test
    void shouldUseClientAsActorWhenPrincipalMissing() {
        var audit = AuditBuilder.builder(UserAuditBuilder.class)
                .type(USER_CREATED)
                .actor(null, client())
                .build(objectMapper);

        assertEquals("app-id", audit.getActor().getId());
        assertEquals(EntityType.APPLICATION, audit.getActor().getType());
        assertEquals("client-name", audit.getActor().getAlternativeId());
        assertEquals("client-name", audit.getActor().getDisplayName());
        assertEquals(ReferenceType.DOMAIN, audit.getActor().getReferenceType());
        assertEquals("domain-id", audit.getActor().getReferenceId());
        assertEquals("app-id", audit.getAccessPoint().getId());
        assertEquals("client-id", audit.getAccessPoint().getAlternativeId());
    }

    @Test
    void shouldKeepSystemActorWhenNoPrincipalAndNoClient() {
        var audit = AuditBuilder.builder(UserAuditBuilder.class)
                .type(USER_CREATED)
                .actor(null, null)
                .build(objectMapper);

        assertEquals("system", audit.getActor().getId());
        assertEquals("system", audit.getActor().getType());
        assertEquals(ReferenceType.PLATFORM, audit.getActor().getReferenceType());
        assertEquals("admin", audit.getAccessPoint().getAlternativeId());
    }

    private static Client client() {
        var client = new Client();
        client.setId("app-id");
        client.setClientId("client-id");
        client.setClientName("client-name");
        client.setDomain("domain-id");
        return client;
    }
}
