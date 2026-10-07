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

import io.gravitee.am.common.scope.ManagementRepositoryScope;
import io.gravitee.am.repository.encryption.EncryptionKeys;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Encrypts the stored configurations of every plugin type with the current key, on demand. It is not
 * run on startup: after a rotation, the new key has to be declared on every Management API and Gateway
 * node before the configurations move to it, otherwise the nodes without it can no longer read them.
 *
 * @author GraviteeSource Team
 */
@Component
@ManagementRepositoryScope
public class ConfigurationEncryptionService {

    private final List<ConfigurationEncryptionTask<?>> tasks;
    private final Environment environment;

    public ConfigurationEncryptionService(List<ConfigurationEncryptionTask<?>> tasks, Environment environment) {
        this.tasks = tasks.stream().sorted(Comparator.comparing(ConfigurationEncryptionTask::plugin)).toList();
        this.environment = environment;
    }

    /**
     * @return the id of the key configurations are encrypted with, or empty when encryption is disabled
     */
    public Optional<String> currentKeyId() {
        return EncryptionKeys.currentKeyId(environment);
    }

    /**
     * Runs the task of each plugin type one after the other; a failing plugin type does not stop the others.
     *
     * @param keyId the current key id, see {@link #currentKeyId()}
     */
    public Single<List<ConfigurationEncryptionResult>> encryptAll(String keyId) {
        return Flowable.fromIterable(tasks)
                .concatMapSingle(task -> task.run(keyId))
                .toList();
    }
}
