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
package io.gravitee.am.management.handlers.management.api.resources.enhancer;

import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.User;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class UserEnhancer {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserEnhancer.class);

    @Autowired
    private IdentityProviderService identityProviderService;

    public Function<User, User> enhance() {
        return user -> {
            if (user.getSource() != null) {
                try {
                    IdentityProvider idP = identityProviderService.findById(user.getSource());
                    user.setSource(idP.getName());
                } catch (IdentityProviderNotFoundException e) {
                    LOGGER.info("No identity provider found with id : {}", user.getSource());
                }
            }
            return user;
        };
    }
}
