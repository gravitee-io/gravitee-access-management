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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.Client;
import org.junit.jupiter.api.Test;

import static io.gravitee.am.common.audit.EventType.CLIENT_AUTHENTICATION;
import static io.gravitee.am.common.audit.Status.FAILURE;
import static io.gravitee.am.common.audit.Status.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClientAuthAuditBuilderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildDefaultWhenNulls() {
        var audit = AuditBuilder.builder(ClientAuthAuditBuilder.class)
                .clientActor(null)
                .throwable(null)
                .build(objectMapper);
        assertEquals(CLIENT_AUTHENTICATION, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertNull(audit.getOutcome().getMessage());
    }

    @Test
    void shouldBuildWithClientTarget() {
        var applicationId = "client-applicationId";
        var clientId = "client-id";
        var clientName = "client-name";
        var domainId = "domainId";
        var c = new Client();
        c.setId(applicationId);
        c.setClientId(clientId);
        c.setClientName(clientName);
        c.setDomain(domainId);

        var audit = AuditBuilder.builder(ClientAuthAuditBuilder.class).clientActor(c).build(objectMapper);

        assertEquals(domainId, audit.getReferenceId());
        assertEquals(applicationId, audit.getAccessPoint().getId());
        assertEquals(clientId, audit.getAccessPoint().getAlternativeId());
        assertEquals(clientName, audit.getAccessPoint().getDisplayName());
        assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
        assertEquals(applicationId, audit.getActor().getId());
        assertEquals(clientName, audit.getActor().getDisplayName());
        assertEquals(clientName, audit.getActor().getAlternativeId());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals(CLIENT_AUTHENTICATION, audit.getType());
    }

    @Test
    void shouldBuildWithClientTargetError() {
        var applicationId = "client-applicationId";
        var clientId = "client-id";
        var clientName = "client-name";
        var domainId = "domainId";
        var errorMessage = "test";
        var client = new Client();
        client.setId(applicationId);
        client.setClientId(clientId);
        client.setClientName(clientName);
        client.setDomain(domainId);

        var audit = AuditBuilder.builder(ClientAuthAuditBuilder.class).clientActor(client).throwable(new Exception(errorMessage)).build(objectMapper);

        assertEquals(domainId, audit.getReferenceId());
        assertEquals(applicationId, audit.getAccessPoint().getId());
        assertEquals(clientId, audit.getAccessPoint().getAlternativeId());
        assertEquals(clientName, audit.getAccessPoint().getDisplayName());
        assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
        assertEquals(applicationId, audit.getActor().getId());
        assertEquals(clientName, audit.getActor().getDisplayName());
        assertEquals(clientName, audit.getActor().getAlternativeId());
        assertEquals(FAILURE, audit.getOutcome().getStatus());
        assertEquals(errorMessage, audit.getOutcome().getMessage());
        assertEquals(CLIENT_AUTHENTICATION, audit.getType());
    }
}
