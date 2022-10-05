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

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * Wraps the Spring argon2 encoder.
 */
public class Argon2IdPasswordEncoder implements PasswordEncoder {

    private static final int MEMORY_MIB = 15360;
    private static final int SALT_LENGTH = 128;
    private static final int HASH_LENGTH = 128;
    private static final int PARALLELISM = 1;
    private static final int ITERATIONS = 2;
    private final Argon2PasswordEncoder argon2PasswordEncoder = new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_MIB, ITERATIONS);

    @Override
    public String encode(CharSequence rawPassword) {
        return argon2PasswordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return argon2PasswordEncoder.matches(rawPassword, encodedPassword);
    }
}
