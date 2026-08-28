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
package io.gravitee.am.reporter.file.formatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import io.gravitee.am.reporter.file.audit.AuditEntry;
import io.gravitee.am.reporter.file.formatter.csv.AuditFormatter;
import io.gravitee.am.reporter.file.formatter.json.JsonFormatter;
import io.gravitee.am.reporter.file.formatter.msgpack.MsgPackFormatter;
import org.junit.Test;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.time.Instant;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author GraviteeSource Team
 */
public class CustomAttributesFormatterTest {

    private static AuditEntry entry(Map<String, Object> customAttributes) {
        AuditEntry entry = new AuditEntry();
        entry.setId("audit-1");
        entry.setType("USER_LOGIN");
        entry.setTransactionId("txn-1");
        entry.setReferenceType(ReferenceType.DOMAIN);
        entry.setReferenceId("domain-1");
        entry.setTimestamp(Instant.now());
        AuditOutcome outcome = new AuditOutcome();
        outcome.setStatus(Status.SUCCESS);
        entry.setOutcome(outcome);
        entry.setCustomAttributes(customAttributes);
        return entry;
    }

    @Test
    public void jsonCarriesThem() {
        String out = new JsonFormatter<AuditEntry>().format(entry(Map.of("employee_id", "E-4471"))).toString();

        assertTrue(out, out.contains("\"customAttributes\":{\"employee_id\":\"E-4471\"}"));
    }

    @Test
    public void jsonOmitsThemWhenThereAreNone() {
        String out = new JsonFormatter<AuditEntry>().format(entry(null)).toString();

        assertFalse(out, out.contains("customAttributes"));
    }

    @Test
    public void messagePackCarriesThem() throws Exception {
        var buffer = new MsgPackFormatter<AuditEntry>().format(entry(Map.of("employee_id", "E-4471")));

        var decoded = new ObjectMapper(new MessagePackFactory()).readTree(buffer.getBytes());
        assertEquals("E-4471", decoded.get("customAttributes").get("employee_id").asText());
    }

    /** Message pack sets no global null-inclusion policy, so the field carries its own. */
    @Test
    public void messagePackOmitsThemWhenThereAreNone() throws Exception {
        var buffer = new MsgPackFormatter<AuditEntry>().format(entry(null));

        var decoded = new ObjectMapper(new MessagePackFactory()).readTree(buffer.getBytes());
        assertFalse(decoded.has("customAttributes"));
    }

    @Test
    public void csvDoesNotCarryThem() {
        String out = new AuditFormatter().format(entry(Map.of("employee_id", "E-4471"))).toString();

        assertFalse(out, out.contains("employee_id"));
    }
}
