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
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.oidc.Client;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.gravitee.am.common.audit.EntityType.APPLICATION;
import static io.gravitee.am.common.audit.EntityType.MFA_FACTOR;
import static io.gravitee.am.common.audit.EntityType.USER;
import static io.gravitee.am.common.audit.EventType.MFA_REMEMBER_DEVICE;
import static io.gravitee.am.common.audit.Status.FAILURE;
import static io.gravitee.am.common.audit.Status.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MFAAuditBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldBuildDefault() {
        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .build(objectMapper);
        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
    }

    @Test
    void shouldBuildWithFactor() {
        var factor = new Factor();
        factor.setId("factor-id");
        factor.setName("factor-name");

        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .factor(factor)
                .build(objectMapper);

        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals("factor-id", audit.getTarget().getId());
        assertEquals("factor-name", audit.getTarget().getDisplayName());
        assertEquals(MFA_FACTOR, audit.getTarget().getType());
    }

    @Test
    void shouldBuildWithChannel() {
        var channel = new EnrolledFactorChannel();
        channel.setType(EnrolledFactorChannel.Type.EMAIL);
        channel.setTarget("test@test.com");

        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .channel(channel)
                .build(objectMapper);

        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
    }

    @Test
    void shouldBuildWithNullChannel() {
        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .channel(null)
                .build(objectMapper);

        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
    }

    @Test
    void shouldBuildWithApplication() {
        var client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setDomain("domain-id");
        client.setClientName("client-name");

        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .application(client)
                .build(objectMapper);

        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals("client-id", audit.getTarget().getId());
        assertEquals("client-name", audit.getTarget().getDisplayName());
        assertEquals(APPLICATION, audit.getTarget().getType());
        assertEquals("domain-id", audit.getTarget().getReferenceId());
        assertEquals(ReferenceType.DOMAIN, audit.getTarget().getReferenceType());
    }

    @Test
    void shouldBuildWithNullApplication() {
        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .application(null)
                .build(objectMapper);

        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertNull(audit.getTarget());
    }

    @Test
    void shouldBuildWithApplicationNullId() {
        var client = new Client();
        client.setClientId("client-id");
        client.setDomain("domain-id");
        client.setClientName("client-name");

        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .application(client)
                .build(objectMapper);

        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertNull(audit.getTarget());
    }

    @Test
    void shouldBuildWithThrowable() {
        var throwable = new Exception("error-message");
        var channel = new EnrolledFactorChannel();
        channel.setType(EnrolledFactorChannel.Type.EMAIL);
        channel.setTarget("test@test.com");

        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .throwable(throwable, channel)
                .build(objectMapper);

        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(FAILURE, audit.getOutcome().getStatus());
        assertTrue(audit.getOutcome().getMessage().contains("error-message"));
        assertTrue(audit.getOutcome().getMessage().contains("EMAIL"));
        assertTrue(audit.getOutcome().getMessage().contains("test@test.com"));
    }

    @Test
    void shouldBuildWithNullThrowable() {
        var channel = new EnrolledFactorChannel();
        channel.setType(EnrolledFactorChannel.Type.EMAIL);
        channel.setTarget("test@test.com");

        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .throwable(null, channel)
                .build(objectMapper);

        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertNull(audit.getOutcome().getMessage());
    }

    @Test
    void shouldBuildWithThrowableAndNullChannel() {
        var throwable = new Exception("error-message");

        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .throwable(throwable, null)
                .build(objectMapper);

        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(FAILURE, audit.getOutcome().getStatus());
        assertEquals("error-message", audit.getOutcome().getMessage());
    }

    @Test
    void shouldBuildWithUser() {
        var user = new User();
        user.setId("user-id");
        user.setUsername("username");
        user.setDisplayName("display-name");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domain-id");
        user.setExternalId("external-id");
        user.setSource("source");

        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .user(user)
                .build(objectMapper);

        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals("user-id", audit.getActor().getId());
        assertEquals("username", audit.getActor().getAlternativeId());
        assertEquals("display-name", audit.getActor().getDisplayName());
        assertEquals(USER, audit.getActor().getType());
        assertEquals("domain-id", audit.getActor().getReferenceId());
        assertEquals(ReferenceType.DOMAIN, audit.getActor().getReferenceType());
    }

    @Test
    void shouldBuildWithUserWithAdditionalInfo() {
        var user = new User();
        user.setId("user-id");
        user.setUsername("username");
        user.setAdditionalInformation(Map.of(Claims.IP_ADDRESS, "127.0.0.1", Claims.USER_AGENT, "test-agent"));

        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .user(user)
                .build(objectMapper);

        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals("user-id", audit.getActor().getId());
        assertEquals("127.0.0.1", audit.getAccessPoint().getIpAddress());
        assertEquals("test-agent", audit.getAccessPoint().getUserAgent());
    }

    @Test
    void shouldBuildWithNullUser() {
        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .user(null)
                .build(objectMapper);

        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertNull(audit.getActor().getId());
    }

    @Test
    void shouldBuildWithDomainFrom() {
        var client = new Client();
        client.setDomain("domain-id");

        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .domainFrom(client)
                .build(objectMapper);

        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertEquals("domain-id", audit.getReferenceId());
        assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
    }

    @Test
    void shouldBuildWithDomainFromNullClient() {
        var audit = AuditBuilder.builder(MFAAuditBuilder.class)
                .type(MFA_REMEMBER_DEVICE)
                .domainFrom(null)
                .build(objectMapper);

        assertEquals(MFA_REMEMBER_DEVICE, audit.getType());
        assertEquals(SUCCESS, audit.getOutcome().getStatus());
        assertNull(audit.getReferenceId());
        assertNull(audit.getReferenceType());
    }
}