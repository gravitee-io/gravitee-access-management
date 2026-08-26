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
package io.gravitee.am.gateway.handler.oauth2.service.device;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;

/**
 * End-user codes for the device authorization grant, per RFC 8628 section 6.1.
 *
 * The alphabet, the length and the hyphenation are fixed by design: a shorter code or a wider
 * charset weakens the entropy the flow relies on.
 *
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserCodeGenerator {

    public static final String ALPHABET = "BCDFGHJKLMNPQRSTVWXZ";
    public static final int LENGTH = 8;
    private static final int GROUP_SIZE = 4;
    private static final char SEPARATOR = '-';

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * @return a new code, in its stored form: {@value #LENGTH} characters, no separator
     */
    public static String generate() {
        final StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * @return the code as displayed to the end user, in groups of {@value #GROUP_SIZE}
     */
    public static String format(String userCode) {
        if (userCode == null) {
            return null;
        }
        final StringBuilder formatted = new StringBuilder(userCode.length() + userCode.length() / GROUP_SIZE);
        for (int i = 0; i < userCode.length(); i++) {
            if (i > 0 && i % GROUP_SIZE == 0) {
                formatted.append(SEPARATOR);
            }
            formatted.append(userCode.charAt(i));
        }
        return formatted.toString();
    }

    /**
     * @return the stored form of a code typed by the end user, whatever the case and separators used
     */
    public static String normalize(String userCode) {
        if (userCode == null) {
            return null;
        }
        final StringBuilder normalized = new StringBuilder(userCode.length());
        for (char c : userCode.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                normalized.append(Character.toUpperCase(c));
            }
        }
        return normalized.toString();
    }
}
