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
package io.gravitee.am.identityprovider.inline.authentication;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.inline.InlineIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.inline.authentication.provisioning.InlineInMemoryUserDetailsManager;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.reactivex.Maybe;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(InlineAuthenticationProviderConfiguration.class)
public class InlineAuthenticationProvider implements AuthenticationProvider, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(InlineAuthenticationProvider.class);

    @Autowired
    private InlineIdentityProviderConfiguration configuration;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private InlineInMemoryUserDetailsManager userDetailsService;

    @Autowired
    private IdentityProviderRoleMapper roleMapper;

    @Autowired
    private IdentityProviderMapper mapper;

    @Override
    public void afterPropertiesSet() {
        if (configuration.getUsers() != null) {
            for (io.gravitee.am.identityprovider.inline.model.User user : configuration.getUsers()) {
                LOGGER.debug("Add an inline user: {}", user);
                userDetailsService.createUser(user);
            }
        }
    }

    @Override
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        return userDetailsService.loadUserByUsername((String) authentication.getPrincipal())
                .flatMap(inlineUsers -> {
                    String presentedPassword = authentication.getCredentials().toString();
                    final boolean passwordValid = passwordEncoder.matches(presentedPassword, inlineUsers.getPassword());
                    return passwordValid ?
                            Maybe.just(createUser(authentication.getContext(), inlineUsers)) :
                            Maybe.error(new BadCredentialsException("Bad credentials"));
                });
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        return userDetailsService.loadUserByUsername(username)
                .map(user -> createUser(new SimpleAuthenticationContext(), user));
    }

    private List<String> getUserRoles(AuthenticationContext authContext, io.gravitee.am.identityprovider.inline.model.User inlineUser) {
        if (roleMapper != null) {
            return roleMapper.apply(authContext, inlineUser.toMap());
        }
        return Collections.emptyList();
    }

    private User createUser(AuthenticationContext authContext, io.gravitee.am.identityprovider.inline.model.User inlineUser) {
        DefaultUser user = new DefaultUser(inlineUser.getUsername());
        user.setId(inlineUser.getUsername());

        // add additional information
        Map<String, Object> claims = new HashMap<>();
        claims.put(StandardClaims.SUB, inlineUser.getUsername());

        if (mapper != null && mapper.getMappers() != null && !mapper.getMappers().isEmpty()) {
            claims.putAll(this.mapper.apply(authContext, inlineUser.toMap()));
        } else {
            // default values
            claims.put(StandardClaims.NAME, inlineUser.getFirstname() + " " + inlineUser.getLastname());
            claims.put(StandardClaims.GIVEN_NAME, inlineUser.getFirstname());
            claims.put(StandardClaims.FAMILY_NAME, inlineUser.getLastname());
            claims.put(StandardClaims.PREFERRED_USERNAME, inlineUser.getUsername());
            if (inlineUser.getEmail() != null) {
                claims.put(StandardClaims.EMAIL, inlineUser.getEmail());
            }
        }
        user.setAdditionalInformation(claims);

        // set user roles
        user.setRoles(getUserRoles(authContext, inlineUser));

        return user;
    }
}
