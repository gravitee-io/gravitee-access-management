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
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.reporter.builder.management.UserAuditBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.common.audit.EventType.USER_LOGIN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class AuditBuilderEnrichmentContextTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static User user() {
        User user = new User();
        user.setId("user-1");
        user.setUsername("jsmith");
        user.setEmail("jsmith@example.com");
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domain-1");
        Map<String, Object> additional = new HashMap<>();
        additional.put("employeeId", "E-4471");
        user.setAdditionalInformation(additional);
        return user;
    }

    private static Client client() {
        Client client = new Client();
        client.setId("client-internal-id");
        client.setClientId("my-app");
        client.setClientName("My Application");
        return client;
    }

    @Test
    void capturesTheUserBehindALogin() {
        var audit = AuditBuilder.builder(AuthenticationAuditBuilder.class)
                .type(USER_LOGIN)
                .user(user())
                .build(objectMapper);

        assertThat(audit.getEnrichmentContext()).isNotNull();
        assertThat(audit.getEnrichmentContext().user().getEmail()).isEqualTo("jsmith@example.com");
        assertThat(audit.getEnrichmentContext().user().getAdditionalInformation()).containsEntry("employeeId", "E-4471");
    }

    @Test
    void capturesTheClientBehindALogin() {
        var audit = AuditBuilder.builder(AuthenticationAuditBuilder.class)
                .type(USER_LOGIN)
                .client(client())
                .build(objectMapper);

        assertThat(audit.getEnrichmentContext()).isNotNull();
        assertThat(audit.getEnrichmentContext().client().getClientId()).isEqualTo("my-app");
        assertThat(audit.getEnrichmentContext().client().getClientName()).isEqualTo("My Application");
    }

    @Test
    void capturesTheUserOnAManagementEvent() {
        var audit = AuditBuilder.builder(UserAuditBuilder.class)
                .type(USER_LOGIN)
                .user(user())
                .build(objectMapper);

        assertThat(audit.getEnrichmentContext()).isNotNull();
        assertThat(audit.getEnrichmentContext().user().getUsername()).isEqualTo("jsmith");
    }

    @Test
    void thereIsNoContextWhenNeitherWasSupplied() {
        var audit = AuditBuilder.builder(AuthenticationAuditBuilder.class)
                .type(USER_LOGIN)
                .build(objectMapper);

        assertThat(audit.getEnrichmentContext()).isNull();
    }

    @Test
    void capturesTheUserATokenIsIssuedFor() {
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .tokenActor(client())
                .tokenTarget(user())
                .build(objectMapper);

        assertThat(audit.getEnrichmentContext()).isNotNull();
        assertThat(audit.getEnrichmentContext().user().getEmail()).isEqualTo("jsmith@example.com");
        assertThat(audit.getEnrichmentContext().client().getClientId()).isEqualTo("my-app");
    }

    @Test
    void capturesTheUserActingOnATokenEvent() {
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .tokenActor(user())
                .build(objectMapper);

        assertThat(audit.getEnrichmentContext()).isNotNull();
        assertThat(audit.getEnrichmentContext().user().getUsername()).isEqualTo("jsmith");
    }

    @Test
    void capturesTheUserAnIdTokenIsMintedFor() {
        var audit = AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .idTokenFor(user())
                .build(objectMapper);

        assertThat(audit.getEnrichmentContext()).isNotNull();
        assertThat(audit.getEnrichmentContext().user().getUsername()).isEqualTo("jsmith");
    }

    @Test
    void theCapturedUserIsTheSanitizedProjection() {
        User user = user();
        user.setPassword("s3cret");

        var audit = AuditBuilder.builder(AuthenticationAuditBuilder.class)
                .type(USER_LOGIN)
                .user(user)
                .build(objectMapper);

        assertThat(audit.getEnrichmentContext().user())
                .isInstanceOf(io.gravitee.am.model.safe.UserProperties.class);
        assertThat(audit.getEnrichmentContext().user().getClass().getDeclaredFields())
                .noneMatch(f -> f.getName().equals("password"));
    }

    @Test
    void theFlattenedFieldsAreUnchanged() {
        var audit = AuditBuilder.builder(AuthenticationAuditBuilder.class)
                .type(USER_LOGIN)
                .user(user())
                .client(client())
                .build(objectMapper);

        assertThat(audit.getActor().getId()).isEqualTo("user-1");
        assertThat(audit.getActor().getAlternativeId()).isEqualTo("jsmith");
        assertThat(audit.getAccessPoint().getAlternativeId()).isEqualTo("my-app");
        assertThat(audit.getCustomAttributes()).isNull();
    }
}
