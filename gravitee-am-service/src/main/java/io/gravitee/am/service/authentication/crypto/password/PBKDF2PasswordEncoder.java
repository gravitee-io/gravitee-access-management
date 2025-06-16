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

package io.gravitee.am.service.authentication.crypto.password;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.util.EncodingUtils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PBKDF2PasswordEncoder implements PasswordEncoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(PBKDF2PasswordEncoder.class);
    public static final int DEFAULT_ROUNDS = 600_000;
    public static final int DEFAULT_SALT_SIZE = 16;
    private static final String MATCH_ERROR = "An error has occurred when performing password match operation";
    private final String STATIC_SECRET = "";
    private final Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm DEFAULT_SECRET_KEY_ALG = Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256;

    private final Pbkdf2PasswordEncoder encoder;
    private final Base64.Encoder b64enc = Base64.getEncoder();
    private final Base64.Decoder b64dec = Base64.getDecoder();
    private final String algorithm;
    private final byte[] secret;
    private final int iterations;
    private int hashWidth = 256;
    private boolean encodeSaltAsBase64 = true;

    public PBKDF2PasswordEncoder() {
        this.encoder = new Pbkdf2PasswordEncoder(STATIC_SECRET, DEFAULT_SALT_SIZE, DEFAULT_ROUNDS, DEFAULT_SECRET_KEY_ALG);
        this.secret = Utf8.encode(STATIC_SECRET);
        this.algorithm = DEFAULT_SECRET_KEY_ALG.name();
        this.iterations = DEFAULT_ROUNDS;
    }

    public PBKDF2PasswordEncoder(int saltLength, int rounds, String secretKeyAlg) {
        this.encoder = new Pbkdf2PasswordEncoder(STATIC_SECRET, saltLength, rounds, Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.valueOf(secretKeyAlg));
        this.secret = Utf8.encode(STATIC_SECRET);
        this.algorithm = secretKeyAlg;
        this.iterations = rounds;
        if (Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA512.name().equals(secretKeyAlg)) {
            this.hashWidth = 512;
        }
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return this.encoder.encode(rawPassword);
    }

    @Override
    public String encode(CharSequence rawPassword, byte[] salt) {
        // the underlying encoder does not expose the encode method with the dedicated salt field, we need to duplicate it
        return (encodeSaltAsBase64) ?
                b64enc.encodeToString(encode0(rawPassword, salt)) :
                Hex.encodeHexString(encode0(rawPassword, salt));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return this.encoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword, String salt) {
        // some third identity providers stored the salt in dedicated field
        try {
            byte[] decodedSalt = (encodeSaltAsBase64) ?
                    b64dec.decode(salt) : Hex.decodeHex(salt);
            // the underlying encoder does not expose the matches method with the dedicated salt field, we need to duplicate it
            final String encodedRawPassword = (encodeSaltAsBase64) ?
                    b64enc.encodeToString(encode0(rawPassword, decodedSalt)) : Hex.encodeHexString(encode0(rawPassword, decodedSalt));
            return encodedRawPassword.equals(encodedPassword);
        } catch (Exception ex) {
            LOGGER.error(MATCH_ERROR, ex);
            return false;
        }
    }


    public void setEncodeSaltAsBase64(boolean encodeSaltAsBase64) {
       this.encodeSaltAsBase64 = encodeSaltAsBase64;
    }

    private byte[] encode0(CharSequence rawPassword, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toString().toCharArray(), EncodingUtils.concatenate(new byte[][]{salt, this.secret}), this.iterations, this.hashWidth);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(this.algorithm);
            return skf.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not create hash", ex);
        }
    }
}