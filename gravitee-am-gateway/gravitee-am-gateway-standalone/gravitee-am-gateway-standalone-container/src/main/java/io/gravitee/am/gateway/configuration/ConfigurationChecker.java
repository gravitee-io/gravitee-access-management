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
package io.gravitee.am.gateway.configuration;

import io.gravitee.node.api.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import static io.gravitee.am.common.utils.ConstantKeys.DEFAULT_JWT_OR_CSRF_SECRET;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ConfigurationChecker implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationChecker.class);

    @Autowired
    private Configuration configuration;

    void check() {
        //Warning if the secret is still the default one
        if (DEFAULT_JWT_OR_CSRF_SECRET.equals(csrfSecret())) {
            LOGGER.warn("");
            LOGGER.warn("##############################################################");
            LOGGER.warn("#                      SECURITY WARNING                      #");
            LOGGER.warn("##############################################################");
            LOGGER.warn("");
            LOGGER.warn("You still use the default CSRF secret.");
            LOGGER.warn("This known secret can reduce the protection against cross-site request forgery attacks.");
            LOGGER.warn("Please change this value, or ask your administrator to do it !");
            LOGGER.warn("");
            LOGGER.warn("##############################################################");
            LOGGER.warn("");
        }
    }

    @Override
    public void afterPropertiesSet() {
        check();
    }

    private String csrfSecret() {
        return configuration.getProperty("http.csrf.secret", DEFAULT_JWT_OR_CSRF_SECRET);
    }
}
