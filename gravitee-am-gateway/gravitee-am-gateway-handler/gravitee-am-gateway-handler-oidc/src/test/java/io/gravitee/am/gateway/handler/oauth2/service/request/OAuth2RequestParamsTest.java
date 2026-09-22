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
package io.gravitee.am.gateway.handler.oauth2.service.request;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.gravitee.am.common.audit.Status.FAILURE;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.idJagAssertion;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.jwtBearerRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OAuth2RequestParamsTest {

    private static final String MCP_SERVER = "https://mcp.example.com/calendar";
    private static final String REQUEST_CONTEXT_SEPARATOR = ". Request: ";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRecordTheRequestedAccessWhenRefusedBeforeTheAssertionIsVerified() {
        String assertion = idJagAssertion();
        TokenRequest request = jwtBearerRequest(assertion);
        request.setScopes(Set.of("calendar.read"));
        request.setResources(Set.of(MCP_SERVER));

        Audit audit = AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .tokenActor(agentApplication())
                .withParams(() -> OAuth2RequestParams.of(request))
                .throwable(new InvalidGrantException("Assertion issuer is not trusted"))
                .build(objectMapper);

        assertEquals(Map.of(
                "GRANT_TYPE", GrantType.JWT_BEARER,
                "SCOPE", "calendar.read",
                "RESOURCE", MCP_SERVER), recordedParameters(audit));
        assertFalse(audit.getOutcome().getMessage().contains(assertion));
    }

    private Map<String, Object> recordedParameters(Audit audit) {
        String message = audit.getOutcome().getMessage();
        try {
            if (FAILURE.equals(audit.getOutcome().getStatus())) {
                return objectMapper.readValue(message.substring(message.indexOf(REQUEST_CONTEXT_SEPARATOR) + REQUEST_CONTEXT_SEPARATOR.length()),
                        new TypeReference<>() { });
            }
            Map<String, Object> parameters = new HashMap<>();
            for (JsonNode operation : objectMapper.readTree(message)) {
                if (!operation.get("value").isNull()) {
                    parameters.put(operation.get("path").asText().substring(1), operation.get("value").asText());
                }
            }
            return parameters;
        } catch (Exception e) {
            throw new AssertionError("Unreadable audit message: " + message, e);
        }
    }

    private static Client agentApplication() {
        Client client = new Client();
        client.setId("agent-application-id");
        client.setClientId("agent-client-id");
        client.setClientName("Agent");
        client.setDomain("domain-id");
        return client;
    }

}
