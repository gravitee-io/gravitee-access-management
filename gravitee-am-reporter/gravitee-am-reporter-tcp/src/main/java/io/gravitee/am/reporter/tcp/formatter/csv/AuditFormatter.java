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
package io.gravitee.am.reporter.tcp.formatter.csv;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.gravitee.am.reporter.tcp.audit.AuditEntry;
import io.vertx.core.buffer.Buffer;
import lombok.extern.slf4j.Slf4j;

/**
 * CSV formatter for {@link AuditEntry}.
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
class AuditFormatter extends SingleValueFormatter<AuditEntry> {

    private final ObjectMapper mapper = new ObjectMapper();

    {
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
    }

    @Override
    public Buffer format0(AuditEntry entry) {
        final Buffer buffer = Buffer.buffer();

        appendLong(buffer, entry.getTimestamp().toEpochMilli());
        appendString(buffer, entry.getId());
        appendString(buffer, entry.getType());
        appendString(buffer, entry.getOrganizationId());
        appendString(buffer, entry.getEnvironmentId());
        appendString(buffer, entry.getTransactionId());
        appendString(buffer, entry.getNodeId());
        appendString(buffer, entry.getNodeHostname());
        appendString(buffer, entry.getReferenceType().name());
        appendString(buffer, entry.getReferenceId());

        try {
            appendString(buffer, mapper.writeValueAsString(entry.getAccessPoint()));
        } catch (JsonProcessingException e) {
            log.warn("Unable to serialize accessPoint for audit entry: {}", entry.getId(), e);
        }
        try {
            appendString(buffer, mapper.writeValueAsString(entry.getActor()));
        } catch (JsonProcessingException e) {
            log.warn("Unable to serialize actor for audit entry: {}", entry.getId(), e);
        }
        try {
            appendString(buffer, mapper.writeValueAsString(entry.getTarget()));
        } catch (JsonProcessingException e) {
            log.warn("Unable to serialize target for audit entry: {}", entry.getId(), e);
        }
        try {
            appendString(buffer, mapper.writeValueAsString(entry.getOutcome()), true);
        } catch (JsonProcessingException e) {
            log.warn("Unable to serialize outcome for audit entry: {}", entry.getId(), e);
        }

        return buffer;
    }
}
