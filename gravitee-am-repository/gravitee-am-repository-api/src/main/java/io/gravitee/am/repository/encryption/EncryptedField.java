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

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Describes the string field of an entity that is stored encrypted.
 *
 * @param type the entity class
 * @param getter reads the field
 * @param setter writes the field
 * @param copier copies the entity, so the caller's instance is never modified before it is stored
 * @author GraviteeSource Team
 */
public record EncryptedField<T>(Class<T> type,
                                Function<T, String> getter,
                                BiConsumer<T, String> setter,
                                UnaryOperator<T> copier) {

    /**
     * @return a copy of {@code value} whose field is encrypted, or {@code value} itself when it is not of the managed type
     */
    Object encryptedCopy(Object value, FieldEncryptor encryptor) {
        if (!type.isInstance(value)) {
            return value;
        }
        T copy = copier.apply(type.cast(value));
        setter.accept(copy, encryptor.encrypt(getter.apply(copy)));
        return copy;
    }

    /**
     * Decrypts the field of {@code value} in place, when it is of the managed type.
     */
    Object decryptInPlace(Object value, FieldEncryptor encryptor) {
        if (type.isInstance(value)) {
            T entity = type.cast(value);
            String stored = getter.apply(entity);
            if (FieldEncryptor.isEncrypted(stored)) {
                setter.accept(entity, encryptor.decrypt(stored));
            }
        }
        return value;
    }
}
