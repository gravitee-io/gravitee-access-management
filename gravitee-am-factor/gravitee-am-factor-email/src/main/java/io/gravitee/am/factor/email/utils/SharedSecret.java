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
package io.gravitee.am.factor.email.utils;

import com.google.common.io.BaseEncoding;

import java.security.SecureRandom;

public final class SharedSecret {

    private static final SecureRandom random = new SecureRandom();

    public static String generate() {
        byte[] buffer = new byte[20];
        random.nextBytes(buffer);
        return BaseEncoding.base32().encode(buffer);
    }

    public static byte[] base32Str2Hex(String secret) {
        return BaseEncoding.base32().decode(secret);
        //return BaseEncoding.base16().encode(bytes);
    }

    public static void main(String[] args) {
        System.out.println(SharedSecret.generate());
    }
}
