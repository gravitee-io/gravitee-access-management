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
package io.gravitee.am.identityprovider.mongo.authentication.spring;

import io.gravitee.am.identityprovider.api.encoding.Base64Encoder;
import io.gravitee.am.identityprovider.api.encoding.BinaryToTextEncoder;
import io.gravitee.am.identityprovider.api.encoding.HexEncoder;
import io.gravitee.am.identityprovider.api.encoding.NoneEncoder;
import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderConfiguration;
import io.gravitee.am.service.authentication.crypto.password.MD5PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.MessageDigestPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.NoOpPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.SHAMD5PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.SHAPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Pattern;

import static io.gravitee.am.identityprovider.mongo.utils.PasswordEncoder.BCRYPT;
import static io.gravitee.am.identityprovider.mongo.utils.PasswordEncoder.MD5;
import static io.gravitee.am.identityprovider.mongo.utils.PasswordEncoder.SHA;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class MongoAuthenticationProviderConfiguration {

    @Autowired
    private MongoIdentityProviderConfiguration configuration;

    @Bean
    public PasswordEncoder passwordEncoder() {
        if (configuration.getPasswordEncoder() == null) {
            return NoOpPasswordEncoder.getInstance();
        }

        if (BCRYPT.equals(configuration.getPasswordEncoder())) {
            return new BCryptPasswordEncoder();
        }

        if (MD5.equals(configuration.getPasswordEncoder())) {
            MessageDigestPasswordEncoder passwordEncoder = new MD5PasswordEncoder();
            passwordEncoder.setEncodeSaltAsBase64("Base64".equals(configuration.getPasswordEncoding()));
            passwordEncoder.setSaltLength(configuration.getPasswordSaltLength());
            return passwordEncoder;
        }

        if (configuration.getPasswordEncoder().startsWith(SHA)) {
            MessageDigestPasswordEncoder passwordEncoder;
            if (configuration.getPasswordEncoder().endsWith("+MD5")) {
                passwordEncoder = new SHAMD5PasswordEncoder(configuration.getPasswordEncoder().split(Pattern.quote("+"))[0]);
            } else {
                passwordEncoder = new SHAPasswordEncoder(configuration.getPasswordEncoder());
            }
            passwordEncoder.setEncodeSaltAsBase64("Base64".equals(configuration.getPasswordEncoding()));
            passwordEncoder.setSaltLength(configuration.getPasswordSaltLength());
            return passwordEncoder;
        }

        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public BinaryToTextEncoder binaryToTextEncoder() {
        if (configuration.getPasswordEncoding() == null) {
            return new NoneEncoder();
        }

        if ("Base64".equals(configuration.getPasswordEncoding())) {
            return new Base64Encoder();
        }

        return new HexEncoder();
    }
}
