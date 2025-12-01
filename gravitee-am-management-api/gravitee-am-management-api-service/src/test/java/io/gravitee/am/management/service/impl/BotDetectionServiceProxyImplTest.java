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
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.BotDetectionPluginService;
import io.gravitee.am.management.service.exception.BotDetectionPluginSchemaNotFoundException;
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.BotDetectionService;
import io.gravitee.am.service.exception.BotDetectionNotFoundException;
import io.gravitee.am.service.model.NewBotDetection;
import io.gravitee.am.service.model.UpdateBotDetection;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotDetectionServiceProxyImplTest {

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "secret": {
                  "type": "string",
                  "x-sensitive": true
                }
              }
            }
            """;

    @Mock
    private BotDetectionService botDetectionService;
    @Mock
    private BotDetectionPluginService botDetectionPluginService;
    @Mock
    private AuditService auditService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private BotDetectionServiceProxyImpl service;

    @Test
    void shouldFindByIdAndFilterConfiguration() {
        var detection = buildDetection("bot-id", "{\"secret\":\"value\"}");
        when(botDetectionService.findById("bot-id")).thenReturn(Maybe.just(detection));
        when(botDetectionPluginService.getSchema(anyString())).thenAnswer(inv -> Maybe.just(SCHEMA));

        var result = service.findById("bot-id").blockingGet();

        assertThat(result.getId()).isEqualTo("bot-id");
        assertThat(result).isNotSameAs(detection);
        assertThat(result.getConfiguration()).isEqualTo(detection.getConfiguration());
    }

    @Test
    void shouldReturnStubWhenSchemaMissingOnFind() {
        var detection = buildDetection("bot-id", "{\"secret\":\"value\"}");
        when(botDetectionService.findById("bot-id")).thenReturn(Maybe.just(detection));
        when(botDetectionPluginService.getSchema(anyString())).thenReturn(Maybe.empty());

        var result = service.findById("bot-id").blockingGet();

        assertThat(result.getConfiguration()).isEqualTo("{}");
    }

    @Test
    void shouldListBotDetectionsByDomain() {
        when(botDetectionService.findByDomain("domain")).thenReturn(Flowable.just(
                buildDetection("bot-1", "{\"secret\":\"value\"}"),
                buildDetection("bot-2", "{\"secret\":\"value\"}")
        ));
        when(botDetectionPluginService.getSchema(anyString())).thenAnswer(inv -> Maybe.just(SCHEMA));

        var results = service.findByDomain("domain").toList().blockingGet();

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(bot -> assertThat(bot.getConfiguration()).isEqualTo("{\"secret\":\"value\"}"));
    }

    @Test
    void shouldCreateBotDetectionWithFilteredConfiguration() {
        var principal = mock(User.class);
        var newBotDetection = new NewBotDetection();
        newBotDetection.setName("bot");
        newBotDetection.setType("kaptcha");
        newBotDetection.setDetectionType("captcha");
        newBotDetection.setConfiguration("{\"secret\":\"value\"}");

        var created = buildDetection("bot-id", "{\"secret\":\"value\"}");

        when(botDetectionService.create("domain", newBotDetection, principal)).thenReturn(Single.just(created));
        when(botDetectionPluginService.getSchema(anyString())).thenAnswer(inv -> Maybe.just(SCHEMA));

        var result = service.create("domain", newBotDetection, principal).blockingGet();

        assertThat(result.getId()).isEqualTo("bot-id");
        assertThat(result.getConfiguration()).isEqualTo("{\"secret\":\"value\"}");
        verify(botDetectionService).create("domain", newBotDetection, principal);
    }

    @Test
    void shouldUpdateBotDetection() {
        var principal = mock(User.class);
        var update = new UpdateBotDetection();
        update.setName("bot");
        update.setType("kaptcha");
        update.setConfiguration("{\"secret\":\"new\"}");

        var existing = buildDetection("bot-id", "{\"secret\":\"old\"}");
        var updated = buildDetection("bot-id", "{\"secret\":\"new\"}");

        when(botDetectionService.findById("bot-id")).thenReturn(Maybe.just(existing));
        when(botDetectionPluginService.getSchema(anyString())).thenAnswer(inv -> Maybe.just(SCHEMA));
        when(botDetectionService.update("domain", "bot-id", update, principal)).thenReturn(Single.just(updated));

        var result = service.update("domain", "bot-id", update, principal).blockingGet();

        assertThat(result.getConfiguration()).isEqualTo("{\"secret\":\"new\"}");
        verify(botDetectionService).update("domain", "bot-id", update, principal);
    }

    @Test
    void shouldThrowWhenBotDetectionMissingOnUpdate() {
        var update = new UpdateBotDetection();
        update.setName("bot");
        update.setType("kaptcha");
        update.setConfiguration("{}");

        when(botDetectionService.findById("unknown")).thenReturn(Maybe.empty());

        assertThatThrownBy(() -> service.update("domain", "unknown", update, mock(User.class)).blockingGet())
                .isInstanceOf(BotDetectionNotFoundException.class);
    }

    @Test
    void shouldFailUpdateWhenSchemaMissing() {
        var principal = mock(User.class);
        var update = new UpdateBotDetection();
        update.setName("bot");
        update.setType("kaptcha");
        update.setConfiguration("{}");

        var existing = buildDetection("bot-id", "{\"secret\":\"old\"}");

        when(botDetectionService.findById("bot-id")).thenReturn(Maybe.just(existing));
        when(botDetectionPluginService.getSchema(anyString())).thenReturn(Maybe.empty());

        assertThatThrownBy(() -> service.update("domain", "bot-id", update, principal).blockingGet())
                .isInstanceOf(BotDetectionPluginSchemaNotFoundException.class);
    }

    @Test
    void shouldDeleteBotDetection() {
        var principal = mock(User.class);
        when(botDetectionService.delete("domain", "bot-id", principal)).thenReturn(Completable.complete());

        service.delete("domain", "bot-id", principal)
                .test()
                .assertComplete();

        verify(botDetectionService).delete("domain", "bot-id", principal);
    }

    private BotDetection buildDetection(String id, String configuration) {
        var detection = new BotDetection();
        detection.setId(id);
        detection.setName("bot");
        detection.setType("kaptcha");
        detection.setConfiguration(configuration);
        detection.setReferenceType(ReferenceType.DOMAIN);
        detection.setReferenceId("domain");
        detection.setCreatedAt(new java.util.Date());
        detection.setUpdatedAt(new java.util.Date());
        return detection;
    }

    @Test
    void shouldAuditOnCreateSuccess() {
        reset(auditService);
        var principal = mock(User.class);
        var request = new NewBotDetection();
        request.setName("bot");
        request.setType("kaptcha");
        request.setDetectionType("captcha");
        request.setConfiguration("{\"secret\":\"value\"}");
        var created = buildDetection("bot-id", "{\"secret\":\"value\"}");

        when(botDetectionService.create("domain", request, principal)).thenReturn(Single.just(created));
        when(botDetectionPluginService.getSchema(anyString())).thenReturn(Maybe.just(SCHEMA));

        service.create("domain", request, principal).blockingGet();

        var audit = captureAudit();
        assertThat(audit.getType()).isEqualTo("BOT_DETECTION_CREATED");
        assertThat(audit.getReferenceId()).isEqualTo("domain");
        assertThat(audit.getOutcome().getStatus().name()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldAuditOnCreateFailure() {
        reset(auditService);
        var principal = mock(User.class);
        var request = new NewBotDetection();
        request.setName("bot");
        request.setType("kaptcha");
        request.setDetectionType("captcha");
        request.setConfiguration("{}");

        when(botDetectionService.create("domain", request, principal)).thenReturn(Single.error(new RuntimeException("boom")));

        assertThatThrownBy(() -> service.create("domain", request, principal).blockingGet()).isInstanceOf(RuntimeException.class);

        var audit = captureAudit();
        assertThat(audit.getType()).isEqualTo("BOT_DETECTION_CREATED");
        assertThat(audit.getReferenceId()).isEqualTo("domain");
        assertThat(audit.getOutcome().getStatus().name()).isEqualTo("FAILURE");
    }

    @Test
    void shouldAuditOnUpdateSuccess() {
        reset(auditService);
        var principal = mock(User.class);
        var update = new UpdateBotDetection();
        update.setName("bot");
        update.setType("kaptcha");
        update.setConfiguration("{\"secret\":\"new\"}");

        var existing = buildDetection("bot-id", "{\"secret\":\"old\"}");
        var updated = buildDetection("bot-id", "{\"secret\":\"new\"}");

        when(botDetectionService.findById("bot-id")).thenReturn(Maybe.just(existing));
        when(botDetectionPluginService.getSchema(anyString())).thenReturn(Maybe.just(SCHEMA));
        when(botDetectionService.update("domain", "bot-id", update, principal)).thenReturn(Single.just(updated));

        service.update("domain", "bot-id", update, principal).blockingGet();

        var audit = captureAudit();
        assertThat(audit.getType()).isEqualTo("BOT_DETECTION_UPDATED");
        assertThat(audit.getReferenceId()).isEqualTo("domain");
        assertThat(audit.getOutcome().getStatus().name()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldAuditOnUpdateFailure() {
        reset(auditService);
        var principal = mock(User.class);
        var update = new UpdateBotDetection();
        update.setName("bot");
        update.setType("kaptcha");
        update.setConfiguration("{\"secret\":\"new\"}");

        var existing = buildDetection("bot-id", "{\"secret\":\"old\"}");

        when(botDetectionService.findById("bot-id")).thenReturn(Maybe.just(existing));
        when(botDetectionPluginService.getSchema(anyString())).thenReturn(Maybe.just(SCHEMA));
        when(botDetectionService.update("domain", "bot-id", update, principal)).thenReturn(Single.error(new RuntimeException("boom")));

        assertThatThrownBy(() -> service.update("domain", "bot-id", update, principal).blockingGet()).isInstanceOf(RuntimeException.class);

        var audit = captureAudit();
        assertThat(audit.getType()).isEqualTo("BOT_DETECTION_UPDATED");
        assertThat(audit.getReferenceId()).isEqualTo("domain");
        assertThat(audit.getOutcome().getStatus().name()).isEqualTo("FAILURE");
    }

    private Audit captureAudit() {
        ArgumentCaptor<AuditBuilder<?>> captor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(captor.capture());
        return captor.getValue().build(new ObjectMapper());
    }
}
