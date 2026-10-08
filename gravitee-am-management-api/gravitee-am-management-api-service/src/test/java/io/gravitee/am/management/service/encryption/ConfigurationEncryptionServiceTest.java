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
package io.gravitee.am.management.service.encryption;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.management.service.encryption.ConfigurationEncryptionResult.Status;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.encryption.EncryptionKeys;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigurationEncryptionServiceTest {

    private final AuditService auditService = mock(AuditService.class);

    @Test
    void should_use_the_selected_key() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                EncryptionKeys.KEYS_PROPERTY + "[0].id", "v1",
                EncryptionKeys.KEYS_PROPERTY + "[0].secret", "secret-1",
                EncryptionKeys.KEYS_PROPERTY + "[1].id", "v2",
                EncryptionKeys.KEYS_PROPERTY + "[1].secret", "secret-2",
                EncryptionKeys.ENABLED_PROPERTY, "true",
                EncryptionKeys.CURRENT_KEY_PROPERTY, "v1")));

        assertThat(new ConfigurationEncryptionService(List.of(), environment, auditService).currentKeyId()).contains("v1");
    }

    @Test
    void should_have_no_key_when_the_encryption_is_disabled() {
        assertThat(new ConfigurationEncryptionService(List.of(), new StandardEnvironment(), auditService).currentKeyId()).isEmpty();
    }

    @Test
    void should_run_every_task_even_after_a_failure() {
        ConfigurationEncryptionTask<?> reporter = task("reporter", Status.FAILED);
        ConfigurationEncryptionTask<?> factor = task("factor", Status.DONE);

        List<ConfigurationEncryptionResult> results = service(reporter, factor).encryptAll("v2").blockingGet();

        // sorted by plugin type, so the report reads the same on every call
        assertThat(results).extracting(ConfigurationEncryptionResult::plugin).containsExactly("factor", "reporter");
        verify(reporter).run("v2");
        verify(factor).run("v2");
    }

    @Test
    void should_audit_the_reencryption_against_the_default_organization() {
        service(task("factor", Status.DONE), task("reporter", Status.ALREADY_DONE)).encryptAll("v2").blockingGet();

        Audit audit = capturedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.PLUGIN_CONFIGURATIONS_REENCRYPTED);
        assertThat(audit.getReferenceType()).isEqualTo(ReferenceType.ORGANIZATION);
        assertThat(audit.getReferenceId()).isEqualTo(Organization.DEFAULT);
        assertThat(audit.getTarget().getId()).isEqualTo("v2");
        assertThat(audit.getTarget().getType()).isEqualTo(EntityType.ENCRYPTION_KEY);
        assertThat(audit.getActor().getId()).isEqualTo("system");
        assertThat(audit.getOutcome().getStatus()).isEqualTo(io.gravitee.am.common.audit.Status.SUCCESS);
        assertThat(audit.getOutcome().getMessage()).contains("factor", "DONE", "reporter", "ALREADY_DONE");
    }

    @Test
    void should_audit_a_failure_when_a_plugin_type_failed() {
        service(task("factor", Status.DONE), task("reporter", Status.FAILED)).encryptAll("v2").blockingGet();

        Audit audit = capturedAudit();
        assertThat(audit.getOutcome().getStatus()).isEqualTo(io.gravitee.am.common.audit.Status.FAILURE);
        assertThat(audit.getOutcome().getMessage()).contains("reporter").doesNotContain("factor");
    }

    @Test
    void should_audit_a_failure_when_the_reencryption_errors() {
        ConfigurationEncryptionTask<?> broken = mock(ConfigurationEncryptionTask.class);
        when(broken.plugin()).thenReturn("factor");
        when(broken.run("v2")).thenReturn(Single.error(new IllegalStateException("boom")));

        service(broken).encryptAll("v2").test().assertError(IllegalStateException.class);

        Audit audit = capturedAudit();
        assertThat(audit.getType()).isEqualTo(EventType.PLUGIN_CONFIGURATIONS_REENCRYPTED);
        assertThat(audit.getOutcome().getStatus()).isEqualTo(io.gravitee.am.common.audit.Status.FAILURE);
    }

    private ConfigurationEncryptionService service(ConfigurationEncryptionTask<?>... tasks) {
        return new ConfigurationEncryptionService(List.of(tasks), new StandardEnvironment(), auditService);
    }

    private Audit capturedAudit() {
        ArgumentCaptor<AuditBuilder<?>> captor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(captor.capture());
        return captor.getValue().build(new ObjectMapper());
    }

    private static ConfigurationEncryptionTask<?> task(String plugin, Status status) {
        ConfigurationEncryptionTask<?> task = mock(ConfigurationEncryptionTask.class);
        when(task.plugin()).thenReturn(plugin);
        when(task.run("v2")).thenReturn(Single.just(new ConfigurationEncryptionResult(plugin, status, 0, null)));
        return task;
    }
}
