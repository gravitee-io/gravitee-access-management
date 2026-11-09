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
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.model.SystemTaskTypes;
import io.gravitee.am.repository.common.CrudRepository;
import io.gravitee.am.repository.encryption.EncryptingRepositoryProxy;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Rewrites the stored configurations of one plugin type that are not yet encrypted with the current key:
 * the repository decrypts each value on read, with whatever key encrypted it, and encrypts it on write
 * with the current key. Values stored in clear text before encryption was enabled get encrypted too, and
 * the values already on the current key are left untouched, so running it again changes nothing.
 * <p>
 * The run is recorded as a system task whose id holds the key id and the minute of the call: a call repeated
 * within the same minute, a double submit for instance, finds the task of the first one and is refused while
 * it runs, and every call that runs picks up what was stored since the previous one. Once a run succeeded, older keys are no longer used by this
 * plugin type.
 *
 * @author GraviteeSource Team
 */
@CustomLog
public abstract class ConfigurationEncryptionTask<T> {

    static final String TASK_ID_PREFIX = "encrypt_";
    // size of the system_tasks id column
    private static final int MAX_TASK_ID_LENGTH = 64;
    private static final int HASHED_KEY_ID_LENGTH = 16;
    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);

    private final SystemTaskRepository systemTaskRepository;
    private final String plugin;
    private final CrudRepository<T, String> repository;
    private final Supplier<Flowable<T>> findAll;
    private final Clock clock;

    /**
     * @param plugin  names the plugin type in the task id and in the report
     * @param findAll every stored entity of the plugin type, read from the repository wrapped by the encryption
     */
    protected ConfigurationEncryptionTask(SystemTaskRepository systemTaskRepository,
                                          String plugin,
                                          CrudRepository<T, String> repository,
                                          Supplier<Flowable<T>> findAll) {
        this(systemTaskRepository, plugin, repository, findAll, Clock.systemUTC());
    }

    ConfigurationEncryptionTask(SystemTaskRepository systemTaskRepository,
                                String plugin,
                                CrudRepository<T, String> repository,
                                Supplier<Flowable<T>> findAll,
                                Clock clock) {
        this.systemTaskRepository = systemTaskRepository;
        this.plugin = plugin;
        this.repository = repository;
        this.findAll = findAll;
        this.clock = clock;
    }

    public String plugin() {
        return plugin;
    }

    /**
     * Encrypts the configurations with {@code keyId}, unless a call of the same minute is still running.
     * A task already finished is run again, which only reads when nothing is left to rewrite. Never fails: a failure is reported in the result and recorded on the task, so a
     * later call runs it again.
     */
    public Single<ConfigurationEncryptionResult> run(String keyId) {
        String taskId = taskId(keyId);
        String operationId = UUID.randomUUID().toString();
        return systemTaskRepository.findById(taskId)
                .switchIfEmpty(Single.defer(() -> createTask(taskId, operationId)))
                .flatMap(task -> switch (SystemTaskStatus.valueOf(task.getStatus())) {
                    case ONGOING -> Single.just(result(Status.IN_PROGRESS, 0, null));
                    case INITIALIZED, SUCCESS, FAILURE -> claim(task, operationId)
                            .flatMap(claimed -> claimed ? encryptAll(task, keyId, operationId) : Single.just(result(Status.IN_PROGRESS, 0, null)));
                })
                .onErrorReturn(err -> {
                    log.error("Unable to encrypt {} configurations with key [{}]", plugin, keyId, err);
                    return result(Status.FAILED, 0, err.getMessage());
                });
    }

    String taskId(String keyId) {
        String minute = MINUTE_FORMAT.format(clock.instant());
        String taskId = TASK_ID_PREFIX + plugin + "_" + keyId + "_" + minute;
        if (taskId.length() <= MAX_TASK_ID_LENGTH) {
            return taskId;
        }
        return TASK_ID_PREFIX + plugin + "_" + hash(keyId) + "_" + minute;
    }

    private Single<SystemTask> createTask(String taskId, String operationId) {
        SystemTask task = new SystemTask();
        task.setId(taskId);
        task.setType(SystemTaskTypes.UPGRADER.name());
        task.setStatus(SystemTaskStatus.INITIALIZED.name());
        task.setOperationId(operationId);
        task.setCreatedAt(new Date());
        task.setUpdatedAt(task.getCreatedAt());
        // another node may create it at the same time: read the one that won
        return systemTaskRepository.create(task)
                .onErrorResumeNext(err -> systemTaskRepository.findById(taskId).toSingle());
    }

    /**
     * @return true when this call owns the task: only the update matching the operation id read wins
     */
    private Single<Boolean> claim(SystemTask task, String operationId) {
        String readOperationId = task.getOperationId();
        task.setOperationId(operationId);
        task.setStatus(SystemTaskStatus.ONGOING.name());
        task.setUpdatedAt(new Date());
        return systemTaskRepository.updateIf(task, readOperationId)
                .map(updated -> operationId.equals(updated.getOperationId()));
    }

    private Single<ConfigurationEncryptionResult> encryptAll(SystemTask task, String keyId, String operationId) {
        return EncryptingRepositoryProxy.findAllPendingEncryption(findAll)
                .concatMapSingle(repository::update)
                .count()
                .flatMap(count -> complete(task, SystemTaskStatus.SUCCESS, operationId)
                        .doOnSuccess(t -> log.info("{} {} configurations encrypted with key [{}]", count, plugin, keyId))
                        .map(t -> result(Status.DONE, count, null)))
                .onErrorResumeNext(err -> {
                    log.error("Unable to encrypt {} configurations with key [{}]", plugin, keyId, err);
                    return complete(task, SystemTaskStatus.FAILURE, operationId)
                            .map(t -> result(Status.FAILED, 0, err.getMessage()));
                });
    }

    private Single<SystemTask> complete(SystemTask task, SystemTaskStatus status, String operationId) {
        task.setStatus(status.name());
        task.setUpdatedAt(new Date());
        return systemTaskRepository.updateIf(task, operationId);
    }

    private ConfigurationEncryptionResult result(Status status, long count, String message) {
        return new ConfigurationEncryptionResult(plugin, status, count, message);
    }

    private static String hash(String keyId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(keyId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASHED_KEY_ID_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
