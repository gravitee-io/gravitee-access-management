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

import io.gravitee.am.repository.encryption.FieldEncryptor.EncryptionKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldEncryptorTest {

    private static final EncryptionKey OLD_KEY = new EncryptionKey("v1", "a-secret");
    private static final EncryptionKey NEW_KEY = new EncryptionKey("prod-2026", "another-secret");
    private static final FieldEncryptor ENCRYPTOR = FieldEncryptor.withKeys(List.of(OLD_KEY));
    private static final FieldEncryptor ROTATED = FieldEncryptor.withKeys(List.of(OLD_KEY, NEW_KEY));
    private static final String CONFIGURATION = "{\"password\":\"s3cr3t\",\"url\":\"ldap://localhost\"}";

    @Test
    void should_decrypt_what_it_encrypted() {
        String encrypted = ENCRYPTOR.encrypt(CONFIGURATION);

        assertThat(encrypted).startsWith("enc:v1:").doesNotContain("s3cr3t");
        assertThat(ENCRYPTOR.decrypt(encrypted)).isEqualTo(CONFIGURATION);
    }

    @Test
    void should_return_clear_text_stored_before_encryption_as_is() {
        assertThat(ENCRYPTOR.decrypt(CONFIGURATION)).isEqualTo(CONFIGURATION);
    }

    @Test
    void should_not_encrypt_twice_with_the_current_key() {
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
    void should_encrypt_with_the_last_key() {
        assertThat(ROTATED.encrypt(CONFIGURATION)).startsWith("enc:prod-2026:");
    }

    @Test
    void should_decrypt_a_value_encrypted_with_an_older_key() {
        String encryptedWithOldKey = ENCRYPTOR.encrypt(CONFIGURATION);

        assertThat(ROTATED.decrypt(encryptedWithOldKey)).isEqualTo(CONFIGURATION);
    }

    @Test
    void should_encrypt_again_with_the_last_key_a_value_encrypted_with_an_older_key() {
        String encryptedWithOldKey = ENCRYPTOR.encrypt(CONFIGURATION);

        String reEncrypted = ROTATED.encrypt(encryptedWithOldKey);

        assertThat(reEncrypted).startsWith("enc:prod-2026:");
        assertThat(ROTATED.decrypt(reEncrypted)).isEqualTo(CONFIGURATION);
    }

    @Test
    void should_fail_when_the_key_of_a_value_is_no_longer_configured() {
        String encryptedWithOldKey = ENCRYPTOR.encrypt(CONFIGURATION);
        FieldEncryptor withoutOldKey = FieldEncryptor.withKeys(List.of(NEW_KEY));

        assertThatThrownBy(() -> withoutOldKey.decrypt(encryptedWithOldKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[v1]")
                .hasMessageContaining("not configured");
        assertThatThrownBy(() -> withoutOldKey.encrypt(encryptedWithOldKey))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_fail_when_the_secret_of_a_key_changed() {
        String encrypted = ENCRYPTOR.encrypt(CONFIGURATION);
        FieldEncryptor changedSecret = FieldEncryptor.withKeys(List.of(new EncryptionKey("v1", "changed-secret")));

        assertThatThrownBy(() -> changedSecret.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_fail_on_malformed_value() {
        assertThatThrownBy(() -> ENCRYPTOR.decrypt("enc:v1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_reject_invalid_keys() {
        assertThatThrownBy(() -> FieldEncryptor.withKeys(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptionKey("v1", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptionKey(" ", "a-secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptionKey("v:1", "a-secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FieldEncryptor.withKeys(List.of(OLD_KEY, new EncryptionKey("v1", "other"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[v1]");
    }
}
