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
package io.gravitee.am.common.crypto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.UUID;

@Slf4j
public class CryptoUtils {

    private final static String CIPHER = "AES/GCM/NoPadding";
    public static final int GCM_TAG_BITS = 128;
    // based on OWASP recommendations: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#pbkdf2b
    public static final int PBKDF2_ITERATION_COUNT = 600000;

    /**
     * Encrypt the string using AES with a key derived from sourceSecret using PBKDF2
     * @return string in format: <pre>base64(pbkdfSalt)$base64(iv)$base64(ciphertext)</pre>
     */
    public static String encrypt(String data, Key sourceSecret) {
        var pbkdfSalt = new byte[16];
        new SecureRandom().nextBytes(pbkdfSalt);
        var key = deriveKey(sourceSecret, pbkdfSalt);

        var encrypted = doEncrypt(data, key);
        return encodeBase64(pbkdfSalt) + "$" + encodeBase64(encrypted.iv) + "$" + encodeBase64(encrypted.ciphertext);
    }

    /**
     * Decrypt data encrypted by {@link #encrypt}
     */
    public static String decrypt(String data, Key sourceSecret) {
        var firstSeparator = data.indexOf("$");
        var secondSeparator = data.indexOf("$", firstSeparator + 1);
        if (firstSeparator == -1 || secondSeparator == -1) {
            throw new IllegalArgumentException("Cannot decrypt: malformed input");

        }
        var pbkdfSalt = decodeBase64(data.substring(0, firstSeparator));
        var iv = decodeBase64(data.substring(firstSeparator + 1, secondSeparator));
        var cipherText = decodeBase64(data.substring(secondSeparator+1));
        try {
            var cipher = Cipher.getInstance(CIPHER);
            var key = deriveKey(sourceSecret, pbkdfSalt);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            var decrypted = cipher.doFinal(cipherText);
            return new String(decrypted);
        } catch (NoSuchPaddingException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException |
                 NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Unable to decrypt data", e);
        } catch (Exception ex) {
            // something broke
            var errorId = UUID.randomUUID().toString();
            log.error("Error decrypting data (errorId={})", errorId, ex);
            throw new RuntimeException("[errorId=%s] Error decrypting data".formatted(errorId));
        }
    }

    @RequiredArgsConstructor
    private final static class EncryptedData {
        private final byte[] iv;
        private final byte[] ciphertext;
    }

    private static EncryptedData doEncrypt(String data, SecretKey key) {
        try {
            var iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            var gcmParams =  new GCMParameterSpec(GCM_TAG_BITS, iv);

            var cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmParams);
            var cipherText = cipher.doFinal(data.getBytes());
            return new EncryptedData(iv, cipherText);
        } catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException |
                 InvalidAlgorithmParameterException | BadPaddingException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to encrypt data", e);
        } catch (Exception ex) {
            var errorId = UUID.randomUUID().toString();
            log.error("Error decrypting data (errorId={})", errorId, ex);
            throw new RuntimeException("[errorId=%s] Error decrypting data".formatted(errorId));
        }
    }

    private static SecretKey deriveKey(Key sourceSecret, byte[] pbkdfSalt) {
        try {
            var keyGenerator = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            var pbkdfPass = new String(sourceSecret.getEncoded()).toCharArray();
            var key = keyGenerator.generateSecret(new PBEKeySpec(pbkdfPass, pbkdfSalt, PBKDF2_ITERATION_COUNT, 256));
            return new SecretKeySpec(key.getEncoded(), "AES");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to generate encryption key", e);
        }
    }

    private static String encodeBase64(byte[] data) {
        return Base64.getUrlEncoder().encodeToString(data);
    }

    private static byte[] decodeBase64(String encoded) {
        return Base64.getUrlDecoder().decode(encoded);
    }
}
