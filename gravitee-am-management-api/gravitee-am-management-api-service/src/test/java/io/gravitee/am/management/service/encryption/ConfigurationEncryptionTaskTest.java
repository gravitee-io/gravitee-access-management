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
import io.gravitee.am.model.AuthenticationDeviceNotifier;
import io.gravitee.am.model.BotDetection;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.DataPlaneDefinition;
import io.gravitee.am.model.DeviceIdentifier;
import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.encryption.EncryptedField;
import io.gravitee.am.repository.encryption.EncryptedFieldRegistry;
import io.gravitee.am.repository.encryption.EncryptingRepositoryProxy;
import io.gravitee.am.repository.encryption.FieldEncryptor;
import io.gravitee.am.repository.encryption.FieldEncryptor.EncryptionKey;
import io.gravitee.am.repository.management.api.AlertNotifierRepository;
import io.gravitee.am.repository.management.api.AuthenticationDeviceNotifierRepository;
import io.gravitee.am.repository.management.api.BotDetectionRepository;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.DataPlaneDefinitionRepository;
import io.gravitee.am.repository.management.api.DeviceIdentifierRepository;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.gravitee.am.repository.management.api.FactorRepository;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.repository.management.api.ReporterRepository;
import io.gravitee.am.repository.management.api.ServiceResourceRepository;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationEncryptionTaskTest {

    private static final String IDP_TASK_ID = "encrypt_identity_provider_v2_202611091015";
    private static final String CLEAR_CONFIGURATION = "{\"password\":\"s3cr3t\"}";
    private static final FieldEncryptor ENCRYPTOR = FieldEncryptor.withKeys(List.of(new EncryptionKey("v2", "a-secret")), "v2");

    @Mock
    private SystemTaskRepository systemTaskRepository;

    @Mock
    private IdentityProviderRepository identityProviderRepository;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-09T10:15:07Z"));

    private final List<String> taskStatuses = new ArrayList<>();

    static Stream<Arguments> tasks() {
        return Stream.of(
                task("reporter", ReporterRepository.class, Reporter::new, ReporterConfigurationEncryptionTask::new),
                task("identity_provider", IdentityProviderRepository.class, IdentityProvider::new, IdentityProviderConfigurationEncryptionTask::new),
                task("alert_notifier", AlertNotifierRepository.class, AlertNotifier::new, AlertNotifierConfigurationEncryptionTask::new),
                task("auth_device_notifier", AuthenticationDeviceNotifierRepository.class, AuthenticationDeviceNotifier::new,
                        AuthenticationDeviceNotifierConfigurationEncryptionTask::new),
                task("bot_detection", BotDetectionRepository.class, BotDetection::new, BotDetectionConfigurationEncryptionTask::new),
                task("certificate", CertificateRepository.class, Certificate::new, CertificateConfigurationEncryptionTask::new),
                task("data_plane", DataPlaneDefinitionRepository.class, DataPlaneDefinition::new, DataPlaneDefinitionConfigurationEncryptionTask::new),
                task("device_identifier", DeviceIdentifierRepository.class, DeviceIdentifier::new, DeviceIdentifierConfigurationEncryptionTask::new),
                task("extension_grant", ExtensionGrantRepository.class, ExtensionGrant::new, ExtensionGrantConfigurationEncryptionTask::new),
                task("factor", FactorRepository.class, Factor::new, FactorConfigurationEncryptionTask::new),
                task("service_resource", ServiceResourceRepository.class, ServiceResource::new, ServiceResourceConfigurationEncryptionTask::new)
        );
    }

    private static <R, T> Arguments task(String plugin, Class<R> repositoryType, Supplier<T> entity,
                                         BiFunction<SystemTaskRepository, R, ConfigurationEncryptionTask<T>> factory) {
        return Arguments.of(plugin, repositoryType, entity, factory);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tasks")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void should_rewrite_every_configuration_of_its_plugin(String plugin, Class repositoryType, Supplier entity, BiFunction factory) throws Exception {
        CrudRepository stored = (CrudRepository) mock(repositoryType);
        Object first = clearText(repositoryType, entity.get());
        Object second = clearText(repositoryType, entity.get());
        when(repositoryType.getMethod("findAll").invoke(stored)).thenReturn(Flowable.just(first, second));
        EncryptedField field = EncryptedFieldRegistry.forRepository(repositoryType).orElseThrow();
        List<String> storedConfigurations = new ArrayList<>();
        when(stored.update(any())).thenAnswer(invocation -> {
            // the encryption decrypts the returned entity in place, so the stored value is read at call time
            storedConfigurations.add((String) field.getter().apply(invocation.getArgument(0)));
            return Single.just(invocation.getArgument(0));
        });
        stubNewTask();
        ConfigurationEncryptionTask<?> task = (ConfigurationEncryptionTask<?>) factory.apply(systemTaskRepository, encrypted(repositoryType, stored));

        ConfigurationEncryptionResult result = task.run("v2").blockingGet();

        assertThat(result).isEqualTo(new ConfigurationEncryptionResult(plugin, Status.DONE, 2, null));
        assertThat(task.taskId("v2")).matches("encrypt_" + plugin + "_v2_\\d{12}");
        verify(systemTaskRepository).findById(startsWith("encrypt_" + plugin + "_v2_"));
        assertThat(storedConfigurations).hasSize(2).allSatisfy(configuration -> assertThat(configuration).startsWith("enc:v2:"));
        assertThat(taskStatuses).containsExactly(SystemTaskStatus.ONGOING.name(), SystemTaskStatus.SUCCESS.name());
    }

    @Test
    void should_not_rewrite_the_configurations_already_encrypted_with_the_current_key() throws Exception {
        IdentityProvider upToDate = new IdentityProvider();
        upToDate.setConfiguration(ENCRYPTOR.encrypt(CLEAR_CONFIGURATION));
        IdentityProvider clear = clearText(IdentityProviderRepository.class, new IdentityProvider());
        when(identityProviderRepository.findAll()).thenReturn(Flowable.just(upToDate, clear));
        when(identityProviderRepository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        stubNewTask();

        ConfigurationEncryptionResult result = identityProviderTask().run("v2").blockingGet();

        assertThat(result).isEqualTo(new ConfigurationEncryptionResult("identity_provider", Status.DONE, 1, null));
        verify(identityProviderRepository, times(1)).update(any());
    }

    @Test
    void should_not_run_again_for_the_same_key_in_the_same_minute() {
        when(systemTaskRepository.findById(IDP_TASK_ID)).thenReturn(Maybe.just(task(SystemTaskStatus.SUCCESS, "previous")));

        ConfigurationEncryptionResult result = identityProviderTask().run("v2").blockingGet();

        assertThat(result.status()).isEqualTo(Status.ALREADY_DONE);
        verify(identityProviderRepository, never()).findAll();
    }

    @Test
    void should_not_run_while_another_call_runs_it() {
        when(systemTaskRepository.findById(IDP_TASK_ID)).thenReturn(Maybe.just(task(SystemTaskStatus.ONGOING, "other")));

        ConfigurationEncryptionResult result = identityProviderTask().run("v2").blockingGet();

        assertThat(result.status()).isEqualTo(Status.IN_PROGRESS);
        verify(identityProviderRepository, never()).findAll();
    }

    @Test
    void should_not_run_when_another_call_claimed_the_task_first() {
        when(systemTaskRepository.findById(IDP_TASK_ID)).thenReturn(Maybe.just(task(SystemTaskStatus.FAILURE, "previous")));
        // the conditional update lost: the repository returns the task as the winner stored it
        when(systemTaskRepository.updateIf(any(), anyString())).thenReturn(Single.just(task(SystemTaskStatus.ONGOING, "winner")));

        ConfigurationEncryptionResult result = identityProviderTask().run("v2").blockingGet();

        assertThat(result.status()).isEqualTo(Status.IN_PROGRESS);
        verify(identityProviderRepository, never()).findAll();
    }

    @Test
    void should_run_again_after_a_failure() throws Exception {
        when(systemTaskRepository.findById(IDP_TASK_ID)).thenReturn(Maybe.just(task(SystemTaskStatus.FAILURE, "previous")));
        stubUpdateIf();
        when(identityProviderRepository.findAll()).thenReturn(Flowable.just(clearText(IdentityProviderRepository.class, new IdentityProvider())));
        when(identityProviderRepository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        ConfigurationEncryptionResult result = identityProviderTask().run("v2").blockingGet();

        assertThat(result).isEqualTo(new ConfigurationEncryptionResult("identity_provider", Status.DONE, 1, null));
        verify(systemTaskRepository).updateIf(any(), eq("previous"));
    }

    @Test
    void should_record_the_failure_when_a_configuration_cannot_be_rewritten() throws Exception {
        IdentityProvider provider = clearText(IdentityProviderRepository.class, new IdentityProvider());
        when(identityProviderRepository.findAll()).thenReturn(Flowable.just(provider));
        when(identityProviderRepository.update(any())).thenReturn(Single.error(new IllegalStateException("key [v1] is not configured")));
        stubNewTask();

        ConfigurationEncryptionResult result = identityProviderTask().run("v2").blockingGet();

        assertThat(result).isEqualTo(new ConfigurationEncryptionResult("identity_provider", Status.FAILED, 0, "key [v1] is not configured"));
        assertThat(taskStatuses).containsExactly(SystemTaskStatus.ONGOING.name(), SystemTaskStatus.FAILURE.name());
    }

    @Test
    void should_use_another_task_after_a_rotation() {
        var task = identityProviderTask();

        assertThat(task.taskId("v1")).isEqualTo("encrypt_identity_provider_v1_202611091015");
        assertThat(task.taskId("v2")).isEqualTo(IDP_TASK_ID);
    }

    @Test
    void should_use_the_same_task_within_the_same_minute() {
        var task = identityProviderTask();
        String first = task.taskId("v2");

        clock.advance(Duration.ofSeconds(44));

        assertThat(task.taskId("v2")).isEqualTo(first);
    }

    @Test
    void should_use_another_task_the_next_minute() {
        var task = identityProviderTask();

        clock.advance(Duration.ofMinutes(1));

        assertThat(task.taskId("v2")).isEqualTo("encrypt_identity_provider_v2_202611091016");
    }

    @Test
    void should_not_mix_up_the_tasks_of_two_keys_whose_ids_share_a_prefix() {
        var task = identityProviderTask();

        assertThat(task.taskId("v2_x")).isEqualTo("encrypt_identity_provider_v2_x_202611091015").isNotEqualTo(task.taskId("v2"));
    }

    @Test
    void should_run_again_in_a_later_minute_after_a_success() {
        when(systemTaskRepository.findById(IDP_TASK_ID)).thenReturn(Maybe.just(task(SystemTaskStatus.SUCCESS, "previous")));
        var task = identityProviderTask();
        assertThat(task.run("v2").blockingGet().status()).isEqualTo(Status.ALREADY_DONE);
        clock.advance(Duration.ofMinutes(1));
        stubNewTask();
        when(identityProviderRepository.findAll()).thenReturn(Flowable.empty());

        ConfigurationEncryptionResult result = task.run("v2").blockingGet();

        assertThat(result).isEqualTo(new ConfigurationEncryptionResult("identity_provider", Status.DONE, 0, null));
        verify(systemTaskRepository).findById("encrypt_identity_provider_v2_202611091016");
    }

    @Test
    void should_keep_the_task_id_within_the_column_size_for_a_long_key_id() {
        String longKeyId = "a-very-long-key-identifier-chosen-by-the-operator-2026-10";
        var task = identityProviderTask();

        assertThat(task.taskId(longKeyId))
                .hasSizeLessThanOrEqualTo(64)
                .startsWith("encrypt_identity_provider_")
                .endsWith("_202611091015")
                .isEqualTo(identityProviderTask().taskId(longKeyId))
                .isNotEqualTo(task.taskId(longKeyId + "-bis"));
    }

    @Test
    void should_keep_the_task_id_within_the_column_size_for_the_longest_plugin_name() {
        var task = new ConfigurationEncryptionTask<>(systemTaskRepository, "auth_device_notifier", identityProviderRepository,
                identityProviderRepository::findAll, clock) {
        };

        assertThat(task.taskId("a-very-long-key-identifier-chosen-by-the-operator-2026-10")).hasSizeLessThanOrEqualTo(64);
    }

    private ConfigurationEncryptionTask<IdentityProvider> identityProviderTask() {
        IdentityProviderRepository wrapped = encrypted(IdentityProviderRepository.class, identityProviderRepository);
        return new ConfigurationEncryptionTask<>(systemTaskRepository, "identity_provider", wrapped, wrapped::findAll, clock) {
        };
    }

    private static <R> R encrypted(Class<R> repositoryType, R repository) {
        EncryptedField<?> field = EncryptedFieldRegistry.forRepository(repositoryType).orElseThrow();
        return repositoryType.cast(EncryptingRepositoryProxy.wrap(repository, field, ENCRYPTOR));
    }

    /**
     * @return {@code entity} as a repository returns it: with a clear text configuration and its dates, which
     * the encryption needs to copy it
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> T clearText(Class<?> repositoryType, T entity) throws ReflectiveOperationException {
        EncryptedField field = EncryptedFieldRegistry.forRepository(repositoryType).orElseThrow();
        field.setter().accept(entity, CLEAR_CONFIGURATION);
        for (Method method : entity.getClass().getMethods()) {
            if ((method.getName().equals("setCreatedAt") || method.getName().equals("setUpdatedAt"))
                    && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Date.class) {
                method.invoke(entity, new Date());
            }
        }
        return entity;
    }

    private static SystemTask task(SystemTaskStatus status, String operationId) {
        SystemTask task = new SystemTask();
        task.setId(IDP_TASK_ID);
        task.setStatus(status.name());
        task.setOperationId(operationId);
        return task;
    }

    private void stubNewTask() {
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.empty());
        when(systemTaskRepository.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        stubUpdateIf();
    }

    private static class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private void stubUpdateIf() {
        when(systemTaskRepository.updateIf(any(), anyString())).thenAnswer(invocation -> {
            SystemTask task = invocation.getArgument(0);
            // the task instance is mutated by each update, so its status is recorded at call time
            taskStatuses.add(task.getStatus());
            return Single.just(task);
        });
    }
}
