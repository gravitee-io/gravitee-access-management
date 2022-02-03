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
package io.gravitee.am.common.utils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Random version-4 UUID will have 6 predetermined variant and version bits, leaving 122 bits for the randomly generated part.
 *
 * OAuth 2.0 spec says :
 *
 * The probability of an attacker guessing generated tokens (and other
 * credentials not intended for handling by end-users) MUST be less than
 * or equal to 2^(-128) and SHOULD be less than or equal to 2^(-160).
 *
 * This random generator use a 256-bit (32 byte) random value that is then URL-safe base64-encoded.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class SecureRandomString {

    public static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String DIGITS = "0123456789";
    public static final String ALPHA_NUMERIC = UPPER + UPPER.toLowerCase() + DIGITS;
    private static final char[] SYMBOLS = ALPHA_NUMERIC.toCharArray();

    private static final SecureRandom random = new SecureRandom();
    private static final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public static String generate() {
        byte[] buffer = new byte[32];
        random.nextBytes(buffer);
        return encoder.encodeToString(buffer);
    }

    public static String randomAlphaNumeric(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Requested random string length " + length + " is less than or equals to 0.");
        }

        final char[] buffer = new char[length];
        for (int i = 0; i < length; ++i) {
            buffer[i] = SYMBOLS[random.nextInt(SYMBOLS.length)];
        }

        return new String(buffer);
    }

    public static List<String> randomAlphaNumeric(int length, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Requested random string count " + count + " is less than or equals to 0.");
        }

        final ArrayList<String> stringList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            stringList.add(randomAlphaNumeric(length));
        }

        return stringList;
    }
}
