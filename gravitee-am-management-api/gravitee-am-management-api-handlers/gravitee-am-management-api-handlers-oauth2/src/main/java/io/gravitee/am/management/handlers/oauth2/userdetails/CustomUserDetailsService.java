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
package io.gravitee.am.management.handlers.oauth2.userdetails;

import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.management.handlers.oauth2.security.IdentityProviderManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collections;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomUserDetailsService.class);

    @Autowired
    private UserService userService;

    @Autowired
    private Domain domain;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // use to find a pre-authenticated user
        // The user should be present in gravitee repository and should be retrieved from the user last identity provider
        try {
            User user;
            try {
                // TODO async call
                user = userService.loadUserByUsernameAndDomain(domain.getId(), username).blockingGet();
            } catch (UserNotFoundException e) {
                LOGGER.info("User with username : {} and for domain : {} not found", username, domain.getId(), e);
                throw new UsernameNotFoundException(username);
            } catch (TechnicalManagementException e) {
                LOGGER.error("Failed to find user by username {} and domain {}", username, domain.getId(), e);
                throw new UsernameNotFoundException(username);
            }

            AuthenticationProvider authenticationprovider = identityProviderManager.get(user.getSource());

            if (authenticationprovider == null) {
                LOGGER.info("Registered identity provider : {} not found for username : {}", user.getSource(), username);
                throw new UsernameNotFoundException("Registered identity provider : " + user.getSource() + " not found for username : " + username);
            }

            io.gravitee.am.identityprovider.api.User idpUser;
            try {
                idpUser = authenticationprovider.loadUserByUsername(username);
            } catch (Exception e) {
                LOGGER.info("User with username : {} and for domain : {} and identity provider : {} not found", username, domain.getId(), user.getSource(), e);
                throw new UsernameNotFoundException(username);
            }

            return new CustomUserDetails(user, idpUser);
        } catch (UsernameNotFoundException e) {
            LOGGER.info("User not found while obtaining a renewed access token, return default user");
            return new org.springframework.security.core.userdetails.User(username, "", Collections.emptyList());
        }
    }

}
