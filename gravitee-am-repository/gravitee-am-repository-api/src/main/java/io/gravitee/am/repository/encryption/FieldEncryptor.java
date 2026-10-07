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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encrypts a string field with AES-256-GCM before it is stored, and decrypts it when it is read.
 * <p>
 * The stored value is {@code enc:<key id>:<b64(iv)>:<b64(ciphertext)>}. The key id names the key that
 * encrypted the value, so keys can be rotated: the last key of the list encrypts, and every key of the
 * list decrypts the values it encrypted. A value encrypted with a key that is no longer configured
 * cannot be read and fails.
 * <p>
 * A value without the prefix is treated as plain text, so data written before the encryption was
 * enabled stays readable.
 * <p>
 * Keys are derived once from the configured secrets: deriving them for each call would make every
 * repository read pay for the PBKDF2 iterations.
 *
 * @author GraviteeSource Team
 */
public final class FieldEncryptor {

    static final String PREFIX = "enc:";

    private static final char SEPARATOR = ':';
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int ITERATIONS = 600_000;
    // A fixed salt keeps the derived key identical on every node sharing the same secret.
    private static final byte[] SALT = "gravitee-am-repository-field-encryption".getBytes(StandardCharsets.UTF_8);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, SecretKey> keys;
    private final String currentKeyId;

    private FieldEncryptor(Map<String, SecretKey> keys, String currentKeyId) {
        this.keys = keys;
        this.currentKeyId = currentKeyId;
    }

    /**
     * @param keys the configured keys, oldest first: the last one encrypts
     */
    public static FieldEncryptor withKeys(List<EncryptionKey> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("At least one encryption key is required");
        }
        Map<String, SecretKey> derived = new HashMap<>();
        for (EncryptionKey key : keys) {
            if (derived.put(key.id(), derive(key.secret())) != null) {
                throw new IllegalArgumentException("Encryption key [" + key.id() + "] is declared twice");
            }
        }
        return new FieldEncryptor(Map.copyOf(derived), keys.getLast().id());
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * Encrypts {@code value} with the current key. A value already encrypted with an older key is
     * encrypted again with the current one.
     */
    public String encrypt(String value) {
        if (value == null) {
            return null;
        }
        if (isEncrypted(value)) {
            if (currentKeyId.equals(parse(value).keyId())) {
                return value;
            }
            value = decrypt(value);
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(currentKeyId), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getEncoder();
            return PREFIX + currentKeyId + SEPARATOR + encoder.encodeToString(iv) + SEPARATOR + encoder.encodeToString(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt repository field", e);
        }
    }

    public String decrypt(String value) {
        if (!isEncrypted(value)) {
            return value;
        }
        Encrypted encrypted = parse(value);
        SecretKey key = keys.get(encrypted.keyId());
        if (key == null) {
            throw new IllegalStateException("Repository field is encrypted with key [" + encrypted.keyId() + "], which is not configured");
        }
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, decoder.decode(encrypted.iv())));
            return new String(cipher.doFinal(decoder.decode(encrypted.ciphertext())), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // A wrong secret ends here: failing is safer than handing ciphertext to a plugin.
            throw new IllegalStateException("Unable to decrypt repository field with key [" + encrypted.keyId() + "], check its configured secret", e);
        }
    }

    private static Encrypted parse(String value) {
        String payload = value.substring(PREFIX.length());
        int keyEnd = payload.indexOf(SEPARATOR);
        int ivEnd = keyEnd < 0 ? -1 : payload.indexOf(SEPARATOR, keyEnd + 1);
        if (keyEnd <= 0 || ivEnd < 0) {
            throw new IllegalStateException("Malformed encrypted repository field");
        }
        return new Encrypted(payload.substring(0, keyEnd), payload.substring(keyEnd + 1, ivEnd), payload.substring(ivEnd + 1));
    }

    private static SecretKey derive(String secret) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), SALT, ITERATIONS, KEY_LENGTH_BITS);
            try {
                return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to derive the repository encryption key", e);
        }
    }

    private record Encrypted(String keyId, String iv, String ciphertext) {
    }

    /**
     * A key used to encrypt repository fields.
     *
     * @param id stored with each value it encrypts, so it must never be reused for another secret
     * @param secret the secret the key is derived from
     */
    public record EncryptionKey(String id, String secret) {

        public EncryptionKey {
            if (id == null || id.isBlank() || id.indexOf(SEPARATOR) >= 0) {
                throw new IllegalArgumentException("Encryption key id [" + id + "] must be set and must not contain '" + SEPARATOR + "'");
            }
            if (secret == null || secret.isBlank()) {
                throw new IllegalArgumentException("Encryption key [" + id + "] has no secret");
            }
        }
    }
}
