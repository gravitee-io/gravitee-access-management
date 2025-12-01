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
package io.gravitee.am.management.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.ReporterPluginService;
import io.gravitee.am.management.service.exception.ReporterPluginSchemaNotFoundException;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.ReporterNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewReporter;
import io.gravitee.am.service.model.UpdateReporter;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporterServiceProxyImplTest {

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "secret": {
                  "type": "string",
                  "sensitive": true
                }
              }
            }
            """;

    @Mock
    private ReporterPluginService reporterPluginService;
    @Mock
    private io.gravitee.am.service.ReporterService reporterService;
    @Mock
    private AuditService auditService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ReporterServiceProxyImpl service;

    @Test
    void shouldCreateReporterAndMaskConfiguration() {
        reset(auditService);
        var principal = mock(User.class);
        var reference = Reference.domain("domain");
        var newReporter = new NewReporter();
        newReporter.setName("reporter");
        newReporter.setType("console");
        newReporter.setConfiguration("{\"secret\":\"value\"}");

        var created = buildReporter("{\"secret\":\"value\"}");
        when(reporterService.create(reference, newReporter, principal, false)).thenReturn(Single.just(created));
        when(reporterPluginService.getSchema(anyString())).thenReturn(Maybe.just(SCHEMA));

        var result = service.create(reference, newReporter, principal, false).blockingGet();

        assertThat(result.getId()).isEqualTo("rep-id");
        assertThat(maskedSecret(result.getConfiguration())).isEqualTo("********");
    }

    @Test
    void shouldFindReporterByIdAndMaskConfiguration() {
        var reporter = buildReporter("{\"secret\":\"value\"}");
        when(reporterService.findById("rep-id")).thenReturn(Maybe.just(reporter));
        when(reporterPluginService.getSchema(anyString())).thenReturn(Maybe.just(SCHEMA));

        service.findById("rep-id")
                .test()
                .assertValue(rep -> maskedSecret(rep.getConfiguration()).equals("********"));
    }

    @Test
    void shouldUpdateReporterAndMaskConfiguration() {
        reset(auditService);
        var principal = mock(User.class);
        var reference = Reference.domain("domain");
        var update = new UpdateReporter();
        update.setName("reporter");
        update.setType("console");
        update.setConfiguration("{\"secret\":\"new\"}");

        var existing = buildReporter("{\"secret\":\"old\"}");
        var updated = buildReporter("{\"secret\":\"new\"}");

        when(reporterService.findById("rep-id")).thenReturn(Maybe.just(existing));
        when(reporterPluginService.getSchema(anyString())).thenReturn(Maybe.just(SCHEMA));
        when(reporterService.update(reference, "rep-id", update, principal, false)).thenReturn(Single.just(updated));

        var result = service.update(reference, "rep-id", update, principal, false).blockingGet();

        assertThat(maskedSecret(result.getConfiguration())).isEqualTo("********");
    }

    @Test
    void shouldFailUpdateWhenSchemaMissing() {
        var principal = mock(User.class);
        var reference = Reference.domain("domain");
        var update = new UpdateReporter();
        update.setName("reporter");
        update.setType("console");
        update.setConfiguration("{\"secret\":\"new\"}");

        var existing = buildReporter("{\"secret\":\"old\"}");

        when(reporterService.findById("rep-id")).thenReturn(Maybe.just(existing));
        when(reporterPluginService.getSchema(anyString())).thenReturn(Maybe.empty());

        service.update(reference, "rep-id", update, principal, false)
                .test()
                .assertError(ReporterPluginSchemaNotFoundException.class);
    }

    @Test
    void shouldDeleteReporter() {
        var principal = mock(User.class);
        when(reporterService.delete("rep-id", principal, false)).thenReturn(io.reactivex.rxjava3.core.Completable.complete());

        service.delete("rep-id", principal, false)
                .test()
                .assertComplete();

        verify(reporterService).delete("rep-id", principal, false);
    }

    @Test
    void shouldAuditWhenUpdateFailsBeforeFiltering() {
        var principal = mock(User.class);
        var reference = Reference.domain("domain");
        var update = new UpdateReporter();
        update.setName("reporter");
        update.setType("console");
        update.setConfiguration("{}");

        var reporter = new Reporter();
        reporter.setId("rep-id");
        reporter.setName("reporter");
        reporter.setType("console");
        reporter.setConfiguration("{}");
        reporter.setReference(reference);
        reporter.setCreatedAt(new Date());
        reporter.setUpdatedAt(new Date());

        when(reporterService.findById("rep-id")).thenReturn(Maybe.just(reporter));
        when(reporterPluginService.getSchema(anyString())).thenReturn(Maybe.just("{}"));
        when(reporterService.update(reference, "rep-id", update, principal, false)).thenReturn(Single.error(new TechnicalManagementException("fail")));

        service.update(reference, "rep-id", update, principal, false)
                .test()
                .assertError(TechnicalManagementException.class);

        var audit = captureAudit();
        assertThat(audit.getType()).isEqualTo(EventType.REPORTER_UPDATED);
        assertThat(audit.getReferenceId()).isEqualTo("domain");
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.FAILURE);
    }

    @Test
    void shouldAuditSuccessfulUpdate() {
        var principal = mock(User.class);
        var reference = Reference.domain("domain");
        var update = new UpdateReporter();
        update.setName("reporter");
        update.setType("console");
        update.setConfiguration("{\"foo\":\"bar\"}");

        var reporter = new Reporter();
        reporter.setId("rep-id");
        reporter.setName("reporter");
        reporter.setType("console");
        reporter.setConfiguration("{\"foo\":\"bar\"}");
        reporter.setReference(reference);
        reporter.setCreatedAt(new Date());
        reporter.setUpdatedAt(new Date());

        var updated = new Reporter(reporter);
        updated.setConfiguration("{\"foo\":\"baz\"}");

        when(reporterService.findById("rep-id")).thenReturn(Maybe.just(reporter));
        when(reporterPluginService.getSchema(anyString())).thenReturn(Maybe.just("{\"type\":\"object\"}"));
        when(reporterService.update(reference, "rep-id", update, principal, false)).thenReturn(Single.just(updated));

        service.update(reference, "rep-id", update, principal, false)
                .test()
                .assertValue(updated)
                .assertComplete();

        var audit = captureAudit();
        assertThat(audit.getType()).isEqualTo(EventType.REPORTER_UPDATED);
        assertThat(audit.getReferenceId()).isEqualTo("domain");
        assertThat(audit.getOutcome().getStatus()).isEqualTo(Status.SUCCESS);
    }

    @Test
    void shouldThrowWhenReporterMissing() {
        var principal = mock(User.class);
        var reference = Reference.domain("domain");
        var update = new UpdateReporter();
        update.setName("reporter");
        update.setType("console");
        update.setConfiguration("{}");

        when(reporterService.findById("missing")).thenReturn(Maybe.empty());

        service.update(reference, "missing", update, principal, false)
                .test()
                .assertError(ReporterNotFoundException.class);
    }

    @Test
    void shouldReturnStubWhenSchemaMissingOnRead() {
        var reporter = new Reporter();
        reporter.setId("rep-id");
        reporter.setType("console");
        reporter.setReference(Reference.domain("domain"));
        reporter.setConfiguration("{\"secret\":\"value\"}");
        reporter.setCreatedAt(new Date());
        reporter.setUpdatedAt(new Date());

        when(reporterService.findById("rep-id")).thenReturn(Maybe.just(reporter));
        when(reporterPluginService.getSchema(anyString())).thenReturn(Maybe.empty());

        service.findById("rep-id")
                .test()
                .assertValue(rep -> "{}".equals(rep.getConfiguration()));
    }

    private Audit captureAudit() {
        ArgumentCaptor<AuditBuilder<?>> captor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(captor.capture());
        return captor.getValue().build(objectMapper);
    }

    private Reporter buildReporter(String configuration) {
        var reporter = new Reporter();
        reporter.setId("rep-id");
        reporter.setName("reporter");
        reporter.setType("console");
        reporter.setConfiguration(configuration);
        reporter.setReference(Reference.domain("domain"));
        reporter.setCreatedAt(new Date());
        reporter.setUpdatedAt(new Date());
        return reporter;
    }

    private String maskedSecret(String configuration) {
        try {
            return objectMapper.readTree(configuration).get("secret").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
