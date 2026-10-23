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
package io.gravitee.am.reporter.kafka.audit;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.kafka.AuditValueFactory;
import io.gravitee.am.reporter.kafka.dto.AuditMessageValueDto;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaAuditReporterTest {

    @Mock
    private KafkaProducer<String, AuditMessageValueDto> producer;

    private KafkaAuditReporter reporter;

    @BeforeEach
    void init() {
        reporter = new KafkaAuditReporter(new io.gravitee.am.reporter.kafka.KafkaReporterConfiguration(), null, null, null, null);
        ReflectionTestUtils.setField(reporter, "producer", producer);
    }

    @Test
    void Should_UseTargetId_When_OrganizationDomainAuditHasTarget() {
        var audit = organizationDomainAudit();

        reporter.report(audit);

        assertThat(capturedKey()).isEqualTo(audit.getTarget().getId());
    }

    @Test
    void Should_UseOrganizationId_When_OrganizationDomainAuditHasNoTarget() {
        var audit = organizationDomainAudit();
        audit.setTarget(null);

        reporter.report(audit);

        assertThat(capturedKey()).isEqualTo("org-id");
    }

    private String capturedKey() {
        ArgumentCaptor<KafkaProducerRecord<String, AuditMessageValueDto>> captor = ArgumentCaptor.forClass(KafkaProducerRecord.class);
        verify(producer).write(captor.capture());
        return captor.getValue().key();
    }

    private Audit organizationDomainAudit() {
        var audit = AuditValueFactory.createAudit();
        audit.setReferenceType(ReferenceType.ORGANIZATION);
        audit.setReferenceId("org-id");
        audit.setType("DOMAIN_CREATED");
        return audit;
    }
}
