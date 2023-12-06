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

package io.gravitee.am.service.impl;

import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.service.authentication.crypto.password.NoOpPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.SHAPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.bcrypt.BCryptPasswordEncoder;
import io.gravitee.am.service.spring.application.SecretHashAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static io.gravitee.am.service.spring.application.SecretHashAlgorithm.PropertyKeys.BCRYPT_ROUNDS;
import static java.util.Objects.isNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ApplicationClientSecretService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private Map<String, PasswordEncoder> encoders = new ConcurrentHashMap<>();

    public PasswordEncoder getOrCreateNoOpPasswordEncoder() {
        return getOrCreatePasswordEncoder(null);
    }

    public PasswordEncoder getOrCreatePasswordEncoder(ApplicationSecretSettings settings) {
        var pwdEncoder = NoOpPasswordEncoder.getInstance();
        if (isNull(settings)) {
            logger.trace("SecretSettings are null, return NoOp encoder");
            return pwdEncoder;
        }

        if (encoders.containsKey(settings.getId())) {
            logger.trace("SecretSettings {} found", settings.getId());
            pwdEncoder = encoders.get(settings.getId());
        } else {
            logger.trace("SecretSettings {} not found, generate new instance of {} encoder", settings.getId(), settings.getAlgorithm());
            var algorithm = SecretHashAlgorithm.valueOf(settings.getAlgorithm());
            switch (algorithm) {
                case BCRYPT:
                    pwdEncoder = new BCryptPasswordEncoder((int)settings.getProperties().get(BCRYPT_ROUNDS.getKey()));
                    break;
                case SHA_512, SHA_256:
                    pwdEncoder = new SHAPasswordEncoder(algorithm.getAlgorithm());
                    break;
                default:
                    logger.warn("No PasswordEncoder with id '{}' found to decode client secret, fallback to NoOpEncoder", settings.getId());
            }
            this.encoders.put(settings.getId(), pwdEncoder);
        }

        return pwdEncoder;
    }

    public ClientSecret generateClientSecret(String rawSecret, ApplicationSecretSettings settings) {
        ClientSecret clientSecret = new ClientSecret();
        clientSecret.setId(UUID.randomUUID().toString());
        clientSecret.setSecret(this.getOrCreatePasswordEncoder(settings).encode(rawSecret));
        clientSecret.setCreatedAt(new Date());
        clientSecret.setSettingsId(settings.getId());
        clientSecret.setName(clientSecret.getId());
        return clientSecret;
    }
}