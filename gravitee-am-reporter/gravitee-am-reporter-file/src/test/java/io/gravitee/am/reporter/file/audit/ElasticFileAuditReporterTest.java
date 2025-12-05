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
package io.gravitee.am.reporter.file.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import io.gravitee.am.reporter.file.JUnitConfiguration;
import lombok.Data;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = JUnitConfiguration.class, loader = AnnotationConfigContextLoader.class)
public class ElasticFileAuditReporterTest extends FileAuditReporterTest {

    static {
        System.setProperty(FileAuditReporter.REPORTERS_FILE_ENABLED, "true");
        System.setProperty(FileAuditReporter.REPORTERS_FILE_OUTPUT, "ELASTICSEARCH");
        System.setProperty(FileAuditReporter.REPORTERS_FILE_DIRECTORY, "target");
    }

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * DTO that matches the exact Elasticsearch template output structure
     */
    @Data
    public static class ElasticsearchAuditEntry {
        @JsonProperty("_type")
        private String type;
        
        @JsonProperty("@timestamp")
        private String timestamp;
        
        private String date;
        
        @JsonProperty("event_id")
        private String eventId;
        
        @JsonProperty("event_type")
        private String eventType;
        
        private String organizationId;
        private String environmentId;
        private String transactionId;
        private String nodeId;
        private String nodeHostname;
        private String referenceType;
        private String referenceId;
        private String status;
        private AuditOutcome outcome;
        private AuditAccessPoint accessPoint;
        private AuditEntity actor;
        private AuditEntity target;
    }

    @Override
    protected void checkAuditLogs(List<Audit> reportables, int loop) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(buildAuditLogsFilename()));
        assertEquals(10, lines.size());
        
        for (int i = 0; i < loop; ++i) {
            ElasticsearchAuditEntry readAudit = OBJECT_MAPPER.readValue(lines.get(i), ElasticsearchAuditEntry.class);
            assertReportEqualsTo(reportables.get(i), readAudit);
            
            validateJsonStructure(lines.get(i), OBJECT_MAPPER);
        }
    }
    
    /**
     * Test case specifically for outcome with missing status (to catch comma issue)
     */
    @Test
    public void testOutcomeWithMissingStatus() throws Exception {
        // Create an audit with outcome that has message but no status
        Audit auditWithMessageOnly = buildRandomAudit(ReferenceType.DOMAIN, "testReporter");
        AuditOutcome outcome = new AuditOutcome();
        // Don't set status - this will test the template's handling of missing status
        outcome.setMessage("Only message present");
        auditWithMessageOnly.setOutcome(outcome);
        
        // Report the audit
        auditReporter.report(auditWithMessageOnly);
        waitBulkLoadFlush();
        
        List<String> lines = Files.readAllLines(Paths.get(buildAuditLogsFilename()));
        String lastLine = lines.get(lines.size() - 1);
        
        JsonNode jsonNode = OBJECT_MAPPER.readTree(lastLine);
        
        // Validate the outcome structure
        JsonNode outcomeNode = jsonNode.get("outcome");
        assertEquals("Outcome should have message", "Only message present", outcomeNode.get("message").asText());
        
        // Validate no extra commas in outcome object
        String outcomeJson = outcomeNode.toString();
        assertFalse("Outcome should not start with comma", outcomeJson.trim().startsWith("{ ,"));
        
        // The status field should not be present
        assertNull("Status should not be present", outcomeNode.get("status"));
    }
    
    /**
     * Test case for MFA_CHALLENGE events where message is a JSON array string
     * The message should be output as a JSON array, not as a string
     */
    @Test
    public void testOutcomeWithJsonArrayMessage() throws Exception {
        // Given: Audit with JSON array string message (MFA_CHALLENGE format)
        String jsonArrayMessage = "[{\"op\":\"add\",\"path\":\"/target\",\"value\":\"test@example.com\"},{\"op\":\"add\",\"path\":\"/type\",\"value\":\"EMAIL\"}]";
        Audit audit = createAuditWithMessage("MFA_CHALLENGE", jsonArrayMessage, io.gravitee.am.common.audit.Status.SUCCESS);
        
        // When: Audit is reported
        auditReporter.report(audit);
        waitBulkLoadFlush();
        
        // Then: JSON should be valid and parseable
        JsonNode outcomeNode = readLastAuditOutcome();
        JsonNode messageNode = outcomeNode.get("message");
        
        assertNotNull("Message should be present", messageNode);
        assertTrue("Message should be a JSON array", messageNode.isArray());
        assertEquals("Array should have 2 elements", 2, messageNode.size());
        
        JsonNode firstElement = messageNode.get(0);
        assertEquals("First element should have op=add", "add", firstElement.get("op").asText());
        assertEquals("First element should have path=/target", "/target", firstElement.get("path").asText());
        assertEquals("First element should have value=test@example.com", "test@example.com", firstElement.get("value").asText());
        
        String outcomeJson = outcomeNode.toString();
        assertFalse("Message should not be a string containing JSON", outcomeJson.contains("\"message\":\"["));
        assertTrue("Message should be a JSON array", outcomeJson.contains("\"message\":["));
    }
    
    /**
     * Test case for USER_CREATED events where message is a JSON object string
     * The message should be output as a JSON object, not as a string
     */
    @Test
    public void testOutcomeWithJsonObjectMessage() throws Exception {
        String jsonObjectMessage = "{\"token\":{\"id\":\"token-id\",\"name\":\"token-name\"}}";
        Audit audit = createAuditWithMessage("USER_CREATED", jsonObjectMessage, io.gravitee.am.common.audit.Status.SUCCESS);
        
        auditReporter.report(audit);
        waitBulkLoadFlush();
        
        // Then: JSON should be valid and parseable
        JsonNode outcomeNode = readLastAuditOutcome();
        JsonNode messageNode = outcomeNode.get("message");
        
        assertNotNull("Message should be present", messageNode);
        assertTrue("Message should be a JSON object", messageNode.isObject());
        
        JsonNode tokenNode = messageNode.get("token");
        assertNotNull("Token should be present", tokenNode);
        assertEquals("Token id should match", "token-id", tokenNode.get("id").asText());
        assertEquals("Token name should match", "token-name", tokenNode.get("name").asText());
        
        // Validate JSON structure (no double-quoted object)
        String outcomeJson = outcomeNode.toString();
        assertFalse("Message should not be a string containing JSON", outcomeJson.contains("\"message\":\"{"));
        assertTrue("Message should be a JSON object", outcomeJson.contains("\"message\":{"));
    }
    
    /**
     * Test case for plain string messages (error messages, etc.)
     * The message should be output as a quoted string
     */
    @Test
    public void testOutcomeWithPlainStringMessage() throws Exception {
        // Given: Audit with plain string message
        String plainMessage = "error-message";
        Audit audit = createAuditWithMessage("AUTHENTICATION", plainMessage, io.gravitee.am.common.audit.Status.FAILURE);
        
        // When: Audit is reported
        auditReporter.report(audit);
        waitBulkLoadFlush();
        
        // Then: JSON should be valid and parseable
        ElasticsearchAuditEntry entry = readLastAuditEntry();
        assertNotNull("Audit entry should be present", entry);
        assertNotNull("Outcome should be present", entry.getOutcome());
        assertEquals("Message should match", plainMessage, entry.getOutcome().getMessage());
        
        // Validate message structure from raw JSON (string)
        JsonNode outcomeNode = readLastAuditOutcome();
        JsonNode messageNode = outcomeNode.get("message");
        
        assertNotNull("Message should be present", messageNode);
        assertTrue("Message should be a string", messageNode.isTextual());
        assertEquals("Message content should match", plainMessage, messageNode.asText());
    }
    
    /**
     * Test case for string containing '[' but not a valid JSON array
     * The message should be output as a quoted string (edge case protection)
     */
    @Test
    public void testOutcomeWithStringContainingBracket() throws Exception {
        // Given: Audit with string containing '[' but not valid JSON array
        String invalidJsonMessage = "Error: [invalid json";
        verifyPlainStringMessage(invalidJsonMessage);
    }
    
    /**
     * Test case for string containing '{' but not a valid JSON object
     * The message should be output as a quoted string (edge case protection)
     */
    @Test
    public void testOutcomeWithStringContainingBrace() throws Exception {
        // Given: Audit with string containing '{' but not valid JSON object
        String invalidJsonMessage = "Error: {invalid json";
        
        // When/Then: Verify message is output as quoted string
        verifyPlainStringMessage(invalidJsonMessage);
    }
    
    /**
     * Test case for a string that looks like a JSON object but is not valid JSON.
     * The message should be output as a quoted string (not raw JSON).
     * This tests the edge case where a string starts with '{' and ends with '}' but is not valid JSON.
     */
    @Test
    public void testOutcomeWithInvalidJsonObjectString() throws Exception {
        String invalidJsonMessage = "{not-a-json-object}";
        verifyPlainStringMessage(invalidJsonMessage);
    }
    
    /**
     * Test case for a string that looks like a JSON array but is not valid JSON.
     * The message should be output as a quoted string (not raw JSON).
     * This tests the edge case where a string starts with '[' and ends with ']' but is not valid JSON.
     */
    @Test
    public void testOutcomeWithInvalidJsonArrayString() throws Exception {
        String invalidJsonMessage = "[not-a-json-array]";
        verifyPlainStringMessage(invalidJsonMessage);
    }
    
    /**
     * Test case for JSON array message with leading/trailing whitespace
     * The message should be trimmed before output as raw JSON
     */
    @Test
    public void testOutcomeWithJsonArrayMessageWithWhitespace() throws Exception {
        String jsonArrayMessage = " [{\"op\":\"add\",\"path\":\"/target\",\"value\":\"test\"}] ";
        Audit audit = createAuditWithMessage("MFA_CHALLENGE", jsonArrayMessage, io.gravitee.am.common.audit.Status.SUCCESS);
        
        auditReporter.report(audit);
        waitBulkLoadFlush();
        
        JsonNode outcomeNode = readLastAuditOutcome();
        JsonNode messageNode = outcomeNode.get("message");
        
        assertNotNull("Message should be present", messageNode);
        assertTrue("Message should be a JSON array", messageNode.isArray());
        assertEquals("Array should have 1 element", 1, messageNode.size());
        
        // Verify the output JSON is valid (no whitespace outside the array)
        String outcomeJson = outcomeNode.toString();
        assertTrue("Message should be a JSON array without leading/trailing whitespace", outcomeJson.contains("\"message\":["));
        assertFalse("Message should not have whitespace before array", outcomeJson.contains("\"message\": ["));
        assertFalse("Message should not have whitespace after array", outcomeJson.contains("] "));
    }
    
    /**
     * Test case for empty/null message
     * Should handle gracefully without errors
     */
    @Test
    public void testOutcomeWithEmptyMessage() throws Exception {
        Audit audit = buildRandomAudit(ReferenceType.DOMAIN, "testReporter");
        AuditOutcome outcome = new AuditOutcome();
        outcome.setStatus(io.gravitee.am.common.audit.Status.SUCCESS);
        outcome.setMessage(null);
        audit.setOutcome(outcome);
        
        // When: Audit is reported
        auditReporter.report(audit);
        waitBulkLoadFlush();
        
        // Then: JSON should be valid and parseable
        ElasticsearchAuditEntry entry = readLastAuditEntry();
        assertNotNull("Audit entry should be present", entry);
        assertNotNull("Outcome should be present", entry.getOutcome());
        
        // Validate message field handling from raw JSON (may be absent or null)
        JsonNode outcomeNode = readLastAuditOutcome();
        // Message field may be absent or null - both are valid
        if (outcomeNode.has("message")) {
            assertTrue("Message should be null if present", outcomeNode.get("message").isNull());
        }
    }
    
    /**
     * Creates an audit with a specific message format
     */
    private Audit createAuditWithMessage(String eventType, String message, io.gravitee.am.common.audit.Status status) {
        Audit audit = buildRandomAudit(ReferenceType.DOMAIN, "testReporter");
        audit.setType(eventType);
        AuditOutcome outcome = new AuditOutcome();
        outcome.setStatus(status);
        outcome.setMessage(message);
        audit.setOutcome(outcome);
        return audit;
    }
    
    /**
     * Reads the last audit log line and returns the ElasticsearchAuditEntry.
     * Only works when message is a string (not JSON array/object).
     */
    private ElasticsearchAuditEntry readLastAuditEntry() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(buildAuditLogsFilename()));
        String lastLine = lines.get(lines.size() - 1);
        
        ElasticsearchAuditEntry entry = OBJECT_MAPPER.readValue(lastLine, ElasticsearchAuditEntry.class);
        assertNotNull("Audit entry should be present", entry);
        return entry;
    }
    
    /**
     * Reads the last audit log line and returns the outcome node.
     * Used for validating message structure (array/object vs string).
     */
    private JsonNode readLastAuditOutcome() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(buildAuditLogsFilename()));
        String lastLine = lines.get(lines.size() - 1);
        
        JsonNode jsonNode = OBJECT_MAPPER.readTree(lastLine);
        JsonNode outcomeNode = jsonNode.get("outcome");
        assertNotNull("Outcome should be present", outcomeNode);
        return outcomeNode;
    }
    
    /**
     * Helper method to verify that a message is output as a plain quoted string
     * (not parsed as JSON array or object).
     */
    private void verifyPlainStringMessage(String message) throws Exception {
        // Given: Audit with plain string message
        Audit audit = createAuditWithMessage("AUTHENTICATION", message, io.gravitee.am.common.audit.Status.FAILURE);
        
        // When: Audit is reported
        auditReporter.report(audit);
        waitBulkLoadFlush();
        
        // Then: JSON should be valid and parseable
        ElasticsearchAuditEntry entry = readLastAuditEntry();
        assertNotNull("Audit entry should be present", entry);
        assertNotNull("Outcome should be present", entry.getOutcome());
        assertEquals("Message should match", message, entry.getOutcome().getMessage());
        
        JsonNode outcomeNode = readLastAuditOutcome();
        JsonNode messageNode = outcomeNode.get("message");
        
        assertNotNull("Message should be present", messageNode);
        assertTrue("Message should be a string", messageNode.isTextual());
        assertEquals("Message content should match", message, messageNode.asText());
    }
    
    /**
     * Validate JSON structure to catch template issues like extra commas
     */
    private void validateJsonStructure(String jsonLine, ObjectMapper mapper) throws IOException {
        JsonNode jsonNode = mapper.readTree(jsonLine);
        
        // Check outcome structure for comma issues
        if (jsonNode.has("outcome")) {
            JsonNode outcome = jsonNode.get("outcome");
            String outcomeJson = outcome.toString();
            
            // Check for leading comma in outcome object
            assertFalse("Outcome should not start with comma", outcomeJson.trim().startsWith("{ ,"));
            
            // Check for trailing comma before closing brace
            assertFalse("Outcome should not have trailing comma", outcomeJson.trim().endsWith(",}"));
        }
    }
    
    /**
     * Custom assertion method that maps ElasticsearchAuditEntry back to expected Audit format
     */
    private void assertReportEqualsTo(Audit expected, ElasticsearchAuditEntry actual) {
        assertEquals("ID should match", expected.getId(), actual.getEventId());
        assertEquals("Type should match", expected.getType(), actual.getEventType());
        assertEquals("Transaction ID should match", expected.getTransactionId(), actual.getTransactionId());
        assertEquals("Reference type should match",
                expected.getReferenceType() != null ? expected.getReferenceType().name() : null,
                actual.getReferenceType());
        assertEquals("Reference ID should match", expected.getReferenceId(), actual.getReferenceId());
        assertEquals("Organization ID should match", "DEFAULT", actual.getOrganizationId());
        assertEquals("Environment ID should match", "DEFAULT", actual.getEnvironmentId());
        
        // Check outcome
        if (expected.getOutcome() != null) {
            assertEquals("Outcome status should match", expected.getOutcome().getStatus().name(), actual.getOutcome().getStatus().name());
            assertEquals("Outcome message should match", expected.getOutcome().getMessage(), actual.getOutcome().getMessage());
        }
        
        // Check access point
        if (expected.getAccessPoint() != null) {
            assertEquals("Access point ID should match", expected.getAccessPoint().getId(), actual.getAccessPoint().getId());
            assertEquals("Access point IP should match", expected.getAccessPoint().getIpAddress(), actual.getAccessPoint().getIpAddress());
            assertEquals("Access point user agent should match", expected.getAccessPoint().getUserAgent(), actual.getAccessPoint().getUserAgent());
        }
        
        // Check actor
        if (expected.getActor() != null) {
            assertEquals("Actor ID should match", expected.getActor().getId(), actual.getActor().getId());
            assertEquals("Actor alternative ID should match", expected.getActor().getAlternativeId(), actual.getActor().getAlternativeId());
            assertEquals("Actor type should match", expected.getActor().getType(), actual.getActor().getType());
        }
        
        // Check target
        if (expected.getTarget() != null) {
            assertEquals("Target ID should match", expected.getTarget().getId(), actual.getTarget().getId());
            assertEquals("Target alternative ID should match", expected.getTarget().getAlternativeId(), actual.getTarget().getAlternativeId());
            assertEquals("Target type should match", expected.getTarget().getType(), actual.getTarget().getType());
        }
    }
}