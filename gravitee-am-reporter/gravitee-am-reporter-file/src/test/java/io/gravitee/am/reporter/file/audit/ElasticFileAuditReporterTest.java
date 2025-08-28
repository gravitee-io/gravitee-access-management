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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.api.audit.model.AuditOutcome;
import io.gravitee.am.reporter.file.JUnitConfiguration;
import lombok.Data;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;

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

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        for (int i = 0; i < loop; ++i) {
            ElasticsearchAuditEntry readAudit = mapper.readValue(lines.get(i), ElasticsearchAuditEntry.class);
            assertReportEqualsTo(reportables.get(i), readAudit);
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