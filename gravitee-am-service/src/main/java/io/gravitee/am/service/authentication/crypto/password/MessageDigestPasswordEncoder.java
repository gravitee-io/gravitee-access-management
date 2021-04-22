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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class MessageDigestPasswordEncoder implements PasswordEncoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageDigestPasswordEncoder.class);
    private static final String PREFIX = "{";
    private static final String SUFFIX = "}";
    private final Base64.Encoder b64enc = Base64.getEncoder();
    private final Base64.Decoder b64dec = Base64.getDecoder();
    private String algorithm;
    private boolean encodeSaltAsBase64 = true;
    private int saltLength = 32;


    public MessageDigestPasswordEncoder(String algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        // if the salt is not defined as a specific field, we suppose that it lies in the encodedPassword with the following format :
        // s = salt == null ? "" : "{" + salt + "}"
        // s + digest(password + s)
        try {
            SecureRandom random = new SecureRandom();
            byte[] bytes = new byte[saltLength];
            random.nextBytes(bytes);

            final String encodedSalt = encodeSaltAsBase64 ? b64enc.encodeToString(bytes) : Hex.encodeHexString(bytes);
            final String salt = PREFIX + encodedSalt + SUFFIX;
            final String saltedPassword = rawPassword + salt;
            final byte[] digest = MessageDigest.getInstance(algorithm).digest(saltedPassword.getBytes(StandardCharsets.UTF_8));
            final String rawPasswordEncoded = salt + (encodeSaltAsBase64 ? b64enc.encodeToString(digest) : Hex.encodeHexString(digest));
            return rawPasswordEncoded;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encode raw password", ex);
        }
    }

    @Override
    public String encode(CharSequence rawPassword, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            md.update(salt);
            byte[] hashedPassword = md.digest(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
            return (encodeSaltAsBase64) ? b64enc.encodeToString(hashedPassword) : Hex.encodeHexString(hashedPassword);
        } catch(Exception ex) {
            throw new IllegalStateException("Unable to encode raw password", ex);
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        // if there is no salt provided, we suppose that it lies in the encodedPassword with the following format :
        // s = salt == null ? "" : "{" + salt + "}"
        // s + digest(password + s)
        try {
            final String salt = extractSalt(encodedPassword);
            final String saltedPassword = rawPassword + salt;
            final byte[] digest = MessageDigest.getInstance(algorithm).digest(saltedPassword.getBytes(StandardCharsets.UTF_8));
            final String rawPasswordEncoded = salt + (encodeSaltAsBase64 ? b64enc.encodeToString(digest) : Hex.encodeHexString(digest));
            return encodedPassword.equals(rawPasswordEncoded);
        } catch (Exception ex) {
            LOGGER.error("An error has occurred when performing password match operation", ex);
            return false;
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword, byte[] salt) {
        if (salt == null) {
            return false;
        }
        try {
            MessageDigest md = MessageDigest.getInstance(this.algorithm);
            md.update(salt);

            byte[] digest = md.digest(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
            String presentedPassword = encodeSaltAsBase64 ? b64enc.encodeToString(digest) : Hex.encodeHexString(digest);

            return encodedPassword.equals(presentedPassword);
        } catch (Exception ex) {
            LOGGER.error("An error has occurred when performing password match operation", ex);
            return false;
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword, String salt) {
        try {
            return matches(rawPassword, encodedPassword, encodeSaltAsBase64 ? b64dec.decode(salt) : Hex.decodeHex(salt));
        } catch (Exception ex) {
            LOGGER.error("An error has occurred when performing password match operation", ex);
            return false;
        }
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public void setEncodeSaltAsBase64(boolean encodeSaltAsBase64) {
        this.encodeSaltAsBase64 = encodeSaltAsBase64;
    }

    public void setSaltLength(int saltLength) {
        this.saltLength = saltLength;
    }

    private static String extractSalt(String prefixEncodedPassword) {
        int start = prefixEncodedPassword.indexOf(PREFIX);
        if (start != 0) {
            return "";
        }
        int end = prefixEncodedPassword.indexOf(SUFFIX, start);
        if (end < 0) {
            return "";
        }
        return prefixEncodedPassword.substring(start, end + 1);
    }

}
