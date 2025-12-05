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
import io.gravitee.am.authorizationengine.api.model.AuthorizationEngineRequest;
import io.gravitee.am.authorizationengine.api.model.AuthorizationEngineResponse;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@SuppressWarnings("unchecked")
class PermissionEvaluatedAuditBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildDefault() {
        var audit = AuditBuilder.builder(PermissionEvaluatedAuditBuilder.class)
                .type(EventType.PERMISSION_EVALUATED)
                .build(objectMapper);
        
        assertEquals(EventType.PERMISSION_EVALUATED, audit.getType());
        assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
    }

    @Test
    void shouldBuildWithDecisionId() {
        var decisionId = "custom-decision-id-123";
        
        var request = AuthorizationEngineRequest.builder()
                .subject(AuthorizationEngineRequest.Subject.builder()
                        .id("user-1")
                        .type("user")
                        .build())
                .resource(AuthorizationEngineRequest.Resource.builder()
                        .id("resource-1")
                        .type("document")
                        .build())
                .action(AuthorizationEngineRequest.Action.builder()
                        .name("read")
                        .build())
                .build();
        
        var response = AuthorizationEngineResponse.builder()
                .decisionId(decisionId)
                .decision(true)
                .build();
        
        var audit = AuditBuilder.builder(PermissionEvaluatedAuditBuilder.class)
                .type(EventType.PERMISSION_EVALUATED)
                .request(request)
                .response(response)
                .build(objectMapper);
        
        assertEquals(EventType.PERMISSION_EVALUATED, audit.getType());
        assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
        assertNotNull(audit.getTarget());
        assertEquals(decisionId, audit.getTarget().getAttributes().get("decisionId"));
    }

    @Test
    void shouldBuildWithRequestAndResponse() {
        var decisionId = "decision-id-456";
        var subjectId = "john";
        var actionName = "hotel.booking.create";
        var resourceId = "room-2025";
        var reason = "Tuple matched";
        
        var request = AuthorizationEngineRequest.builder()
                .subject(AuthorizationEngineRequest.Subject.builder()
                        .id(subjectId)
                        .type("user")
                        .build())
                .resource(AuthorizationEngineRequest.Resource.builder()
                        .id(resourceId)
                        .type("room")
                        .build())
                .action(AuthorizationEngineRequest.Action.builder()
                        .name(actionName)
                        .build())
                .build();
        
        var response = AuthorizationEngineResponse.builder()
                .decisionId(decisionId)
                .decision(true)
                .context(Map.of("reason", reason))
                .build();
        
        var audit = AuditBuilder.builder(PermissionEvaluatedAuditBuilder.class)
                .type(EventType.PERMISSION_EVALUATED)
                .request(request)
                .response(response)
                .build(objectMapper);
        
        assertEquals(EventType.PERMISSION_EVALUATED, audit.getType());
        assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
        
        // Verify target attributes
        assertNotNull(audit.getTarget());
        assertEquals(EntityType.AUTHORIZATION_ENGINE, audit.getTarget().getType());
        assertEquals(resourceId, audit.getTarget().getId());
        
        // Verify request attributes
        var requestAttr = (Map<String, Object>) audit.getTarget().getAttributes().get("request");
        assertNotNull(requestAttr);
        
        var subjectAttr = (Map<String, Object>) requestAttr.get("subject");
        assertEquals("user", subjectAttr.get("type"));
        assertEquals(subjectId, subjectAttr.get("id"));
        
        assertEquals(actionName, requestAttr.get("action"));
        
        var resourceAttr = (Map<String, Object>) requestAttr.get("resource");
        assertEquals("room", resourceAttr.get("type"));
        assertEquals(resourceId, resourceAttr.get("id"));
        
        // Verify response attributes
        var responseAttr = (Map<String, Object>) audit.getTarget().getAttributes().get("response");
        assertNotNull(responseAttr);
        assertEquals(true, responseAttr.get("result"));
        assertEquals(reason, responseAttr.get("reason"));
    }

    @Test
    void shouldBuildWithDefaultReason() {
        var request = AuthorizationEngineRequest.builder()
                .subject(AuthorizationEngineRequest.Subject.builder()
                        .id("user-1")
                        .type("user")
                        .build())
                .resource(AuthorizationEngineRequest.Resource.builder()
                        .id("resource-1")
                        .type("document")
                        .build())
                .action(AuthorizationEngineRequest.Action.builder()
                        .name("read")
                        .build())
                .build();
        
        var response = AuthorizationEngineResponse.builder()
                .decisionId("decision-granted")
                .decision(true)
                .build();
        
        var audit = AuditBuilder.builder(PermissionEvaluatedAuditBuilder.class)
                .type(EventType.PERMISSION_EVALUATED)
                .request(request)
                .response(response)
                .build(objectMapper);
        
        var responseAttr = (Map<String, Object>) audit.getTarget().getAttributes().get("response");
        assertNotNull(responseAttr);
        assertEquals(true, responseAttr.get("result"));
        assertEquals("Allow", responseAttr.get("reason"));
    }

    @Test
    void shouldBuildWithDeniedDecisionAndDefaultReason() {
        var request = AuthorizationEngineRequest.builder()
                .subject(AuthorizationEngineRequest.Subject.builder()
                        .id("user-1")
                        .type("user")
                        .build())
                .resource(AuthorizationEngineRequest.Resource.builder()
                        .id("resource-1")
                        .type("document")
                        .build())
                .action(AuthorizationEngineRequest.Action.builder()
                        .name("delete")
                        .build())
                .build();
        
        var response = AuthorizationEngineResponse.builder()
                .decisionId("decision-denied")
                .decision(false)
                .build();
        
        var audit = AuditBuilder.builder(PermissionEvaluatedAuditBuilder.class)
                .type(EventType.PERMISSION_EVALUATED)
                .request(request)
                .response(response)
                .build(objectMapper);
        
        var responseAttr = (Map<String, Object>) audit.getTarget().getAttributes().get("response");
        assertNotNull(responseAttr);
        assertEquals(false, responseAttr.get("result"));
        assertEquals("Deny", responseAttr.get("reason"));
    }

    @Test
    void shouldBuildWithDomain() {
        var domainId = "domain-123";
        var domain = new Domain();
        domain.setId(domainId);
        
        var audit = AuditBuilder.builder(PermissionEvaluatedAuditBuilder.class)
                .type(EventType.PERMISSION_EVALUATED)
                .domain(domain)
                .build(objectMapper);
        
        assertEquals(EventType.PERMISSION_EVALUATED, audit.getType());
        assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
        assertEquals(domainId, audit.getReferenceId());
    }

    @Test
    void shouldBuildWithClientActor() {
        var clientId = "client-123";
        var clientAppId = "app-id-456";
        var clientName = "My Application";
        var domainId = "domain-123";
        
        var client = new Client();
        client.setId(clientAppId);
        client.setClientId(clientId);
        client.setClientName(clientName);
        client.setDomain(domainId);
        
        var audit = AuditBuilder.builder(PermissionEvaluatedAuditBuilder.class)
                .type(EventType.PERMISSION_EVALUATED)
                .actor(client)
                .build(objectMapper);
        
        assertEquals(EventType.PERMISSION_EVALUATED, audit.getType());
        assertNotNull(audit.getActor());
        assertEquals(clientAppId, audit.getActor().getId());
        assertEquals(EntityType.APPLICATION, audit.getActor().getType());
        assertEquals(clientName, audit.getActor().getAlternativeId());
        assertEquals(clientName, audit.getActor().getDisplayName());
        
        // Also verify access point is set
        assertNotNull(audit.getAccessPoint());
        assertEquals(clientAppId, audit.getAccessPoint().getId());
        assertEquals(clientId, audit.getAccessPoint().getAlternativeId());
    }

    @Test
    void shouldBuildCompleteAuditEvent() {
        var decisionId = "decision-uuid-123";
        var domainId = "domain-123";
        var clientAppId = "app-id-456";
        var clientId = "client-123";
        var clientName = "My Application";
        var subjectId = "john";
        var actionName = "hotel.booking.create";
        var resourceId = "room-2025";
        var reason = "Tuple matched";
        
        var domain = new Domain();
        domain.setId(domainId);
        
        var client = new Client();
        client.setId(clientAppId);
        client.setClientId(clientId);
        client.setClientName(clientName);
        client.setDomain(domainId);
        
        var request = AuthorizationEngineRequest.builder()
                .subject(AuthorizationEngineRequest.Subject.builder()
                        .id(subjectId)
                        .type("user")
                        .build())
                .resource(AuthorizationEngineRequest.Resource.builder()
                        .id(resourceId)
                        .type("room")
                        .build())
                .action(AuthorizationEngineRequest.Action.builder()
                        .name(actionName)
                        .build())
                .build();
        
        var response = AuthorizationEngineResponse.builder()
                .decisionId(decisionId)
                .decision(true)
                .context(Map.of("reason", reason))
                .build();
        
        var audit = AuditBuilder.builder(PermissionEvaluatedAuditBuilder.class)
                .type(EventType.PERMISSION_EVALUATED)
                .domain(domain)
                .actor(client)
                .request(request)
                .response(response)
                .build(objectMapper);
        
        // Verify event type and status
        assertEquals(EventType.PERMISSION_EVALUATED, audit.getType());
        assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
        
        // Verify domain reference
        assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
        assertEquals(domainId, audit.getReferenceId());
        
        // Verify actor (client/application)
        assertNotNull(audit.getActor());
        assertEquals(clientAppId, audit.getActor().getId());
        assertEquals(EntityType.APPLICATION, audit.getActor().getType());
        assertEquals(clientName, audit.getActor().getAlternativeId());
        
        // Verify access point
        assertNotNull(audit.getAccessPoint());
        assertEquals(clientAppId, audit.getAccessPoint().getId());
        assertEquals(clientId, audit.getAccessPoint().getAlternativeId());
        
        // Verify target
        assertNotNull(audit.getTarget());
        assertEquals(resourceId, audit.getTarget().getId());
        
        // Verify decision ID
        assertEquals(decisionId, audit.getTarget().getAttributes().get("decisionId"));
        
        // Verify request (subject is in request attributes, not actor)
        var requestAttr = (Map<String, Object>) audit.getTarget().getAttributes().get("request");
        
        var subjectAttr = (Map<String, Object>) requestAttr.get("subject");
        assertEquals("user", subjectAttr.get("type"));
        assertEquals(subjectId, subjectAttr.get("id"));
        
        assertEquals(actionName, requestAttr.get("action"));
        
        var resourceAttr = (Map<String, Object>) requestAttr.get("resource");
        assertEquals("room", resourceAttr.get("type"));
        assertEquals(resourceId, resourceAttr.get("id"));
        
        // Verify response
        var responseAttr = (Map<String, Object>) audit.getTarget().getAttributes().get("response");
        assertEquals(true, responseAttr.get("result"));
        assertEquals(reason, responseAttr.get("reason"));
    }

    @Test
    void shouldHandleNullValues() {
        var audit = AuditBuilder.builder(PermissionEvaluatedAuditBuilder.class)
                .type(EventType.PERMISSION_EVALUATED)
                .domain(null)
                .actor(null)
                .request(null)
                .response(null)
                .build(objectMapper);
        
        assertEquals(EventType.PERMISSION_EVALUATED, audit.getType());
        assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
        // Decision ID should default to transactional ID when no response is provided
        assertNotNull(audit.getTransactionId());
    }

    @Test
    void shouldBuildWithError() {
        var errorMessage = "Authorization engine connection failed";
        var request = AuthorizationEngineRequest.builder()
                .subject(AuthorizationEngineRequest.Subject.builder()
                        .id("user-1")
                        .type("user")
                        .build())
                .resource(AuthorizationEngineRequest.Resource.builder()
                        .id("resource-1")
                        .type("document")
                        .build())
                .action(AuthorizationEngineRequest.Action.builder()
                        .name("read")
                        .build())
                .build();
        
        var audit = AuditBuilder.builder(PermissionEvaluatedAuditBuilder.class)
                .type(EventType.PERMISSION_EVALUATED)
                .request(request)
                .throwable(new Exception(errorMessage))
                .build(objectMapper);
        
        assertEquals(EventType.PERMISSION_EVALUATED, audit.getType());
        assertEquals(Status.FAILURE, audit.getOutcome().getStatus());
        assertEquals(errorMessage, audit.getOutcome().getMessage());
        // Decision ID should fall back to transactional ID when no response is provided
        assertNotNull(audit.getTransactionId());
    }
}

