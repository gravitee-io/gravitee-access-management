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

import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PBKDF2PasswordEncoder implements PasswordEncoder {

    private final String STATIC_SALT = "";
    private final int DEFAULT_ROUNDS = 600_000;
    private final int DEFAULT_SALT_SIZE = 16;
    private final Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm DEFAULT_SECRET_KEY_ALG = Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256;

    private final Pbkdf2PasswordEncoder encoder;

    public PBKDF2PasswordEncoder() {
        this.encoder = new Pbkdf2PasswordEncoder(STATIC_SALT, DEFAULT_SALT_SIZE, DEFAULT_ROUNDS, DEFAULT_SECRET_KEY_ALG);
    }

    public PBKDF2PasswordEncoder(int saltLength, int rounds, String secretKeyAlg) {
        this.encoder = new Pbkdf2PasswordEncoder(STATIC_SALT, saltLength, rounds, Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.valueOf(secretKeyAlg));
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return this.encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return this.encoder.matches(rawPassword, encodedPassword);
    }
}