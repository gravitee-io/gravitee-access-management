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

import io.gravitee.am.identityprovider.jdbc.configuration.JdbcIdentityProviderConfiguration;
import io.gravitee.am.service.authentication.crypto.password.NoOpPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class JdbcAuthenticationProviderConfiguration {

    @Autowired
    private JdbcIdentityProviderConfiguration configuration;

    @Bean
    public PasswordEncoder passwordEncoder() {
        if (configuration.getPasswordEncoder() != null &&
                io.gravitee.am.identityprovider.jdbc.utils.PasswordEncoder.BCRYPT.getValue().equals(configuration.getPasswordEncoder())) {
            return new BCryptPasswordEncoder();
        }
        return NoOpPasswordEncoder.getInstance();
    }
}
