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
package io.gravitee.am.repository.encryption;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.MaybeSource;
import io.reactivex.rxjava3.functions.Function;

/**
 * The stored configuration of an entity could not be decrypted (key no longer configured or corrupted value).
 * <p>
 * Reads stay strict, so a plugin is never handed ciphertext. The entity, with its configuration still
 * encrypted, travels with the exception so that a flow which does not need the configuration, such as a
 * deletion, can carry on with it.
 */
public class PluginDecryptConfigException extends IllegalStateException {

    private final transient Object entity;

    public PluginDecryptConfigException(Object entity, String entityId, Throwable cause) {
        super("Unable to decrypt the configuration of " + entity.getClass().getSimpleName() + " '" + entityId + "': " + cause.getMessage(), cause);
        this.entity = entity;
    }

    /**
     * @return the entity as stored, its configuration left encrypted
     */
    public <T> T getEntity(Class<T> type) {
        return type.cast(entity);
    }

    /**
     * Continues with the stored entity when its configuration cannot be decrypted. To be used by the flows
     * that never read the configuration, like the deletions, with {@code onErrorResumeNext}.
     */
    public static <T> Function<Throwable, MaybeSource<T>> recoverEntity(Class<T> type) {
        return error -> error instanceof PluginDecryptConfigException e ? Maybe.just(e.getEntity(type)) : Maybe.error(error);
    }
}
