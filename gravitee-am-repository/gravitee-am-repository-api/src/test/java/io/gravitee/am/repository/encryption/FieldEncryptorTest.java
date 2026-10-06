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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldEncryptorTest {

    private static final FieldEncryptor ENCRYPTOR = FieldEncryptor.fromSecret("a-secret");
    private static final String CONFIGURATION = "{\"password\":\"s3cr3t\",\"url\":\"ldap://localhost\"}";

    @Test
    void should_decrypt_what_it_encrypted() {
        String encrypted = ENCRYPTOR.encrypt(CONFIGURATION);

        assertThat(encrypted).startsWith(FieldEncryptor.PREFIX).doesNotContain("s3cr3t");
        assertThat(ENCRYPTOR.decrypt(encrypted)).isEqualTo(CONFIGURATION);
    }

    @Test
    void should_return_clear_text_stored_before_encryption_as_is() {
        assertThat(ENCRYPTOR.decrypt(CONFIGURATION)).isEqualTo(CONFIGURATION);
    }

    @Test
    void should_not_encrypt_twice() {
        String encrypted = ENCRYPTOR.encrypt(CONFIGURATION);

        assertThat(ENCRYPTOR.encrypt(encrypted)).isEqualTo(encrypted);
    }

    @Test
    void should_use_a_new_iv_for_each_encryption() {
        assertThat(ENCRYPTOR.encrypt(CONFIGURATION)).isNotEqualTo(ENCRYPTOR.encrypt(CONFIGURATION));
    }

    @Test
    void should_keep_null() {
        assertThat(ENCRYPTOR.encrypt(null)).isNull();
        assertThat(ENCRYPTOR.decrypt(null)).isNull();
    }

    @Test
    void should_fail_with_another_secret() {
        String encrypted = ENCRYPTOR.encrypt(CONFIGURATION);
        FieldEncryptor other = FieldEncryptor.fromSecret("another-secret");

        assertThatThrownBy(() -> other.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_reject_empty_secret() {
        assertThatThrownBy(() -> FieldEncryptor.fromSecret(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
