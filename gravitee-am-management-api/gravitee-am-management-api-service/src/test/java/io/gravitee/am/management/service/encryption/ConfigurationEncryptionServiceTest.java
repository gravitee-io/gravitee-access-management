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

import io.gravitee.am.management.service.encryption.ConfigurationEncryptionResult.Status;
import io.gravitee.am.repository.encryption.EncryptionKeys;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigurationEncryptionServiceTest {

    @Test
    void should_use_the_last_configured_key() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                EncryptionKeys.KEYS_PROPERTY + "[0].id", "v1",
                EncryptionKeys.KEYS_PROPERTY + "[0].secret", "secret-1",
                EncryptionKeys.KEYS_PROPERTY + "[1].id", "v2",
                EncryptionKeys.KEYS_PROPERTY + "[1].secret", "secret-2")));

        assertThat(new ConfigurationEncryptionService(List.of(), environment).currentKeyId()).contains("v2");
    }

    @Test
    void should_have_no_key_when_encryption_is_disabled() {
        assertThat(new ConfigurationEncryptionService(List.of(), new StandardEnvironment()).currentKeyId()).isEmpty();
    }

    @Test
    void should_run_every_task_even_after_a_failure() {
        ConfigurationEncryptionTask<?> reporter = task("reporter", Status.FAILED);
        ConfigurationEncryptionTask<?> factor = task("factor", Status.DONE);

        List<ConfigurationEncryptionResult> results = new ConfigurationEncryptionService(List.of(reporter, factor), new StandardEnvironment())
                .encryptAll("v2").blockingGet();

        // sorted by plugin type, so the report reads the same on every call
        assertThat(results).extracting(ConfigurationEncryptionResult::plugin).containsExactly("factor", "reporter");
        verify(reporter).run("v2");
        verify(factor).run("v2");
    }

    private static ConfigurationEncryptionTask<?> task(String plugin, Status status) {
        ConfigurationEncryptionTask<?> task = mock(ConfigurationEncryptionTask.class);
        when(task.plugin()).thenReturn(plugin);
        when(task.run("v2")).thenReturn(Single.just(new ConfigurationEncryptionResult(plugin, status, 0, null)));
        return task;
    }
}
