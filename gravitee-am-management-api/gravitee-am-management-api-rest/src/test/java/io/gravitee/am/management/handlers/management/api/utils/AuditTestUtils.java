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
package io.gravitee.am.management.handlers.management.api.utils;

import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.CertificateCredentialAuditBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.argThat;

/**
 * Utility class for audit logging verification in tests.
 *
 * @author GraviteeSource Team
 */
public final class AuditTestUtils {

    private AuditTestUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Create an argument matcher for verifying CertificateCredentialAuditBuilder with a specific event type.
     *
     * @param eventType the expected event type (String constant from EventType interface)
     * @return an argument matcher that can be used with Mockito.verify()
     */
    @SuppressWarnings("rawtypes")
    public static AuditBuilder auditBuilderMatcher(String eventType) {
        return argThat(builder -> {
            if (builder instanceof CertificateCredentialAuditBuilder) {
                String actualEventType = (String) ReflectionTestUtils.getField(builder, "type");
                return eventType.equals(actualEventType);
            }
            return false;
        });
    }
}

