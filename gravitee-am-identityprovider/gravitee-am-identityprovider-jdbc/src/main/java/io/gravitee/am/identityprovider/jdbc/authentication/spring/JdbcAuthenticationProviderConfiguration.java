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
package io.gravitee.am.identityprovider.jdbc.authentication.spring;

import io.gravitee.am.identityprovider.api.encoding.Base64Encoder;
import io.gravitee.am.identityprovider.api.encoding.BinaryToTextEncoder;
import io.gravitee.am.identityprovider.api.encoding.HexEncoder;
import io.gravitee.am.identityprovider.api.encoding.NoneEncoder;
import io.gravitee.am.identityprovider.jdbc.configuration.JdbcIdentityProviderConfiguration;
import io.gravitee.am.service.authentication.crypto.password.MD5PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.MessageDigestPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.NoOpPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.PBKDF2PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoderOptions;
import io.gravitee.am.service.authentication.crypto.password.SHAMD5PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.SHAPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Pattern;

import static io.gravitee.am.identityprovider.jdbc.utils.PasswordEncoder.BCRYPT;
import static io.gravitee.am.identityprovider.jdbc.utils.PasswordEncoder.MD5;
import static io.gravitee.am.identityprovider.jdbc.utils.PasswordEncoder.PBKDF2;
import static io.gravitee.am.identityprovider.jdbc.utils.PasswordEncoder.SHA;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class JdbcAuthenticationProviderConfiguration {

    public static final String BASE_64 = "Base64";
    @Autowired
    private JdbcIdentityProviderConfiguration configuration;

    @Bean
    public PasswordEncoder passwordEncoder() {
        if (configuration.getPasswordEncoder() == null) {
            return NoOpPasswordEncoder.getInstance();
        }

        if (BCRYPT.equals(configuration.getPasswordEncoder())) {
            return ofNullable(configuration.getPasswordEncoderOptions())
                    .filter(opts -> opts.getRounds() > 0)
                    .map(PasswordEncoderOptions::getRounds)
                    .map(BCryptPasswordEncoder::new)
                    .orElseGet(BCryptPasswordEncoder::new);
        }

        if (MD5.equals(configuration.getPasswordEncoder())) {
            MessageDigestPasswordEncoder passwordEncoder = new MD5PasswordEncoder();
            passwordEncoder.setEncodeSaltAsBase64(BASE_64.equals(configuration.getPasswordEncoding()));
            passwordEncoder.setSaltLength(configuration.getPasswordSaltLength());
            passwordEncoder.setPasswordSaltFormat(configuration.getPasswordSaltFormat());
            return passwordEncoder;
        }

        if (configuration.getPasswordEncoder().startsWith(SHA)) {
            MessageDigestPasswordEncoder passwordEncoder;
            if (configuration.getPasswordEncoder().endsWith("+MD5")) {
                passwordEncoder = new SHAMD5PasswordEncoder(configuration.getPasswordEncoder().split(Pattern.quote("+"))[0]);
            } else {
                passwordEncoder = new SHAPasswordEncoder(configuration.getPasswordEncoder());
            }
            passwordEncoder.setEncodeSaltAsBase64(BASE_64.equals(configuration.getPasswordEncoding()));
            passwordEncoder.setSaltLength(configuration.getPasswordSaltLength());
            passwordEncoder.setPasswordSaltFormat(configuration.getPasswordSaltFormat());
            if (configuration.getPasswordEncoderOptions() != null && configuration.getPasswordEncoderOptions().getRounds() > 0) {
                passwordEncoder.setIterationsRounds(configuration.getPasswordEncoderOptions().getRounds());
            }
            return passwordEncoder;
        }

        if (configuration.getPasswordEncoder().startsWith(PBKDF2)) {
            int saltLength = configuration.getPasswordSaltLength() > 0 ?
                    configuration.getPasswordSaltLength() :
                    PBKDF2PasswordEncoder.DEFAULT_SALT_SIZE;
            int iterations = (configuration.getPasswordEncoderOptions() != null && configuration.getPasswordEncoderOptions().getRounds() > 0) ?
                    configuration.getPasswordEncoderOptions().getRounds() :
                    PBKDF2PasswordEncoder.DEFAULT_ROUNDS;
            PBKDF2PasswordEncoder passwordEncoder = new PBKDF2PasswordEncoder(saltLength, iterations, configuration.getPasswordEncoder());
            passwordEncoder.setEncodeSaltAsBase64(BASE_64.equals(configuration.getPasswordEncoding()));
            return passwordEncoder;
        }

        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public BinaryToTextEncoder binaryToTextEncoder() {
        if (configuration.getPasswordEncoding() == null) {
            return new NoneEncoder();
        }

        if (BASE_64.equals(configuration.getPasswordEncoding())) {
            return new Base64Encoder();
        }

        return new HexEncoder();
    }
}
