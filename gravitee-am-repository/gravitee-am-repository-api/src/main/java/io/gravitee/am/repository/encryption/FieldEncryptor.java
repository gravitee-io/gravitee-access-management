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

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts a string field with AES-256-GCM before it is stored, and decrypts it when it is read.
 * <p>
 * The stored value is {@code enc:v1:<b64(iv)>:<b64(ciphertext)>}. A value without the prefix is
 * treated as plain text, so data written before the encryption was enabled stays readable, and a
 * value that already carries the prefix is never encrypted twice.
 * <p>
 * The key is derived once from the configured secret: deriving it for each call would make every
 * repository read pay for the PBKDF2 iterations.
 *
 * @author GraviteeSource Team
 */
public final class FieldEncryptor {

    static final String PREFIX = "enc:v1:";

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int ITERATIONS = 600_000;
    // A fixed salt keeps the derived key identical on every node sharing the same secret.
    private static final byte[] SALT = "gravitee-am-repository-field-encryption".getBytes(StandardCharsets.UTF_8);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    private FieldEncryptor(SecretKey key) {
        this.key = key;
    }

    public static FieldEncryptor fromSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("The encryption secret must not be empty");
        }
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), SALT, ITERATIONS, KEY_LENGTH_BITS);
            try {
                byte[] derived = factory.generateSecret(spec).getEncoded();
                return new FieldEncryptor(new SecretKeySpec(derived, "AES"));
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to derive the repository encryption key", e);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String value) {
        if (value == null || isEncrypted(value)) {
            return value;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getEncoder();
            return PREFIX + encoder.encodeToString(iv) + ":" + encoder.encodeToString(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt repository field", e);
        }
    }

    public String decrypt(String value) {
        if (!isEncrypted(value)) {
            return value;
        }
        String payload = value.substring(PREFIX.length());
        int separator = payload.indexOf(':');
        if (separator < 0) {
            throw new IllegalStateException("Malformed encrypted repository field");
        }
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] iv = decoder.decode(payload.substring(0, separator));
            byte[] encrypted = decoder.decode(payload.substring(separator + 1));
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // A wrong secret ends here: failing is safer than handing ciphertext to a plugin.
            throw new IllegalStateException("Unable to decrypt repository field, check the configured encryption secret", e);
        }
    }
}
