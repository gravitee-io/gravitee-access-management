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
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.model.Template;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailAuditBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({
            "RESET_PASSWORD, RESET_PASSWORD_EMAIL_SENT",
            "BLOCKED_ACCOUNT, BLOCKED_ACCOUNT_EMAIL_SENT",
            "VERIFY_ATTEMPT, VERIFY_ATTEMPT_EMAIL_SENT",
            "REGISTRATION_VERIFY, REGISTRATION_VERIFY_EMAIL_SENT",
            "REGISTRATION_CONFIRMATION, REGISTRATION_CONFIRMATION_EMAIL_SENT",
    })
    void shouldMapTemplateToFilterableEventType(Template template, String expectedType) {
        var audit = AuditBuilder.builder(EmailAuditBuilder.class)
                .template(template)
                .build(objectMapper);

        assertEquals(expectedType, audit.getType());
        assertTrue(EventType.types().contains(audit.getType()));
    }

    @Test
    void shouldNotSetTypeForNonEmailTemplate() {
        var audit = AuditBuilder.builder(EmailAuditBuilder.class)
                .template(Template.LOGIN)
                .build(objectMapper);

        assertNull(audit.getType());
    }

    @Test
    void shouldIgnoreNullTemplate() {
        var audit = AuditBuilder.builder(EmailAuditBuilder.class)
                .template(null)
                .build(objectMapper);

        assertNull(audit.getType());
    }

    @Test
    void shouldDeriveTypeFromCustomTemplateName() {
        var audit = AuditBuilder.builder(EmailAuditBuilder.class)
                .customTemplate("my_policy_template.html")
                .build(objectMapper);

        assertEquals("MY_POLICY_TEMPLATE_EMAIL_SENT", audit.getType());
    }
}
