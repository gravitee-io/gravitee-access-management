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
package io.gravitee.am.factor.otp.utils;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;

import java.security.SecureRandom;

/**
 * The shared secret key K is a Base32 string — randomly generated or derived —
 * known only to the client and the server and different and unique for each token.
 *
 * The algorithm MUST use a strong shared secret.
 * The length of the shared secret MUST be at least 128 bits. This document RECOMMENDs a shared secret length of 160 bits.
 * — RFC-4226
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class SharedSecret {

    private static final SecureRandom random = new SecureRandom();
    private static final Base32 encoder = new Base32();

    public static String generate() {
        byte[] buffer = new byte[20];
        random.nextBytes(buffer);
        return encoder.encodeToString(buffer);
    }

    public static String base32Str2Hex(String secret) {
        byte[] bytes = encoder.decode(secret);
        return Hex.encodeHexString(bytes);
    }
}
