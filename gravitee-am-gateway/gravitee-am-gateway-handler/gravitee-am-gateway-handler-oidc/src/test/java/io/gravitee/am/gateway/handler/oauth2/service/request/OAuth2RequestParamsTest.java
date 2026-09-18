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
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidResourceException;
import io.gravitee.am.gateway.handler.oauth2.service.grant.IdJagAssertionContext;
import io.gravitee.am.gateway.handler.oauth2.service.grant.IdJagAssertionContext.BindingMode;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.gravitee.am.common.audit.EventType.TOKEN_CREATED;
import static io.gravitee.am.common.audit.Status.FAILURE;
import static io.gravitee.am.common.audit.Status.SUCCESS;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.idJagAssertion;
import static io.gravitee.am.gateway.handler.oauth2.service.grant.AssertionFixtures.jwtBearerRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuth2RequestParamsTest {

    private static final String ENTERPRISE_ISSUER = "https://enterprise.example.com/oidc";
    private static final String IDENTITY_PROVIDER = "idp-id";
    private static final String MCP_SERVER = "https://mcp.example.com/calendar";
    private static final String REQUEST_CONTEXT_SEPARATOR = ". Request: ";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRecordRedemptionFactsOnTokenCreatedAudit() {
        String assertion = idJagAssertion();
        OAuth2Request request = new OAuth2Request();
        request.setParameters(jwtBearerRequest(assertion).parameters());
        request.setGrantType(GrantType.JWT_BEARER);
        request.setScopes(Set.of("calendar.read"));
        request.setResources(Set.of(MCP_SERVER));
        request.setIdJagAssertionContext(new IdJagAssertionContext(ENTERPRISE_ISSUER, IDENTITY_PROVIDER, "jti-1", "agent-client-id",
                MCP_SERVER, Set.of("calendar.read"), BindingMode.SUBJECT, "local-user-id"));

        Audit audit = AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .tokenActor(agentApplication())
                .withParams(() -> OAuth2RequestParams.of(request))
                .tokenTarget(boundUser())
                .build(objectMapper);

        assertEquals(TOKEN_CREATED, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals("agent-application-id", audit.getActor().getId());
        assertEquals("local-user-id", audit.getTarget().getId());
        assertEquals(Map.of(
                "GRANT_TYPE", GrantType.JWT_BEARER,
                "ASSERTION_ISSUER", ENTERPRISE_ISSUER,
                "IDENTITY_PROVIDER", IDENTITY_PROVIDER,
                "ASSERTION_JTI", "jti-1",
                "ASSERTION_CLIENT_ID", "agent-client-id",
                "RESOURCE", MCP_SERVER,
                "SCOPE", "calendar.read",
                "BINDING_MODE", "SUBJECT",
                "BOUND_USER", "local-user-id"), recordedParameters(audit));
        assertFalse(audit.getOutcome().getMessage().contains(assertion));
    }

    @Test
    void shouldRecordGrantedScopesResolvedAfterTheAssertionScopes() {
        OAuth2Request request = new OAuth2Request();
        request.setGrantType(GrantType.JWT_BEARER);
        request.setScopes(Set.of("calendar.read"));
        request.setIdJagAssertionContext(new IdJagAssertionContext(ENTERPRISE_ISSUER, IDENTITY_PROVIDER, "jti-1", "agent-client-id",
                MCP_SERVER, Set.of(), BindingMode.TRANSIENT, "alice"));

        Map<String, Object> params = OAuth2RequestParams.of(request);

        assertEquals("calendar.read", params.get("SCOPE"));
    }

    @Test
    void shouldRecordOnlyVerifiedFactsResolvedBeforeTheRefusal() {
        TokenRequest request = jwtBearerRequest(idJagAssertion());
        request.setScopes(Set.of("calendar.admin"));
        request.setResources(Set.of("https://mcp.example.com/mail"));
        request.setIdJagAssertionContext(new IdJagAssertionContext(ENTERPRISE_ISSUER, IDENTITY_PROVIDER, "jti-1", "agent-client-id",
                null, null, null, null));

        Audit audit = AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .tokenActor(agentApplication())
                .withParams(() -> OAuth2RequestParams.of(request))
                .throwable(new InvalidResourceException("Requested resource does not match the assertion resource"))
                .build(objectMapper);

        assertEquals(TOKEN_CREATED, audit.getType());
        assertEquals(FAILURE, audit.getOutcome().getStatus());
        assertEquals("agent-application-id", audit.getActor().getId());
        assertTrue(audit.getOutcome().getMessage().startsWith("Requested resource does not match the assertion resource" + REQUEST_CONTEXT_SEPARATOR));
        assertEquals(Map.of(
                "GRANT_TYPE", GrantType.JWT_BEARER,
                "ASSERTION_ISSUER", ENTERPRISE_ISSUER,
                "IDENTITY_PROVIDER", IDENTITY_PROVIDER,
                "ASSERTION_JTI", "jti-1",
                "ASSERTION_CLIENT_ID", "agent-client-id"), recordedParameters(audit));
    }

    @Test
    void shouldRecordOnlyTheGrantTypeWhenRefusedBeforeTheAssertionIsVerified() {
        String assertion = idJagAssertion();
        TokenRequest request = jwtBearerRequest(assertion);
        request.setScopes(Set.of("calendar.read"));
        request.setResources(Set.of(MCP_SERVER));
        request.setIdJagAssertionContext(IdJagAssertionContext.empty());

        Audit audit = AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .tokenActor(agentApplication())
                .withParams(() -> OAuth2RequestParams.of(request))
                .throwable(new InvalidGrantException("Assertion issuer is not trusted"))
                .build(objectMapper);

        assertEquals(Map.of("GRANT_TYPE", GrantType.JWT_BEARER), recordedParameters(audit));
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

    private static User boundUser() {
        User user = new User();
        user.setId("local-user-id");
        user.setUsername("alice");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domain-id");
        return user;
    }
}
