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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a repository method whose results are returned even when the encrypted field of some of them cannot
 * be decrypted. Such an entity keeps its stored (still encrypted) value, which tells it apart from a readable
 * one through {@link FieldEncryptor#isEncrypted(String)}, and the other entities are not lost.
 * <p>
 * Only for the methods that never hand the field to a plugin, like the ones that list entities for display.
 * Every other method stays strict and fails with {@link PluginDecryptConfigException}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface KeepEncryptedOnFailure {
}
