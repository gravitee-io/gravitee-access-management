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

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.inline.InlineIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.inline.InlineIdentityProviderMapper;
import io.gravitee.am.identityprovider.inline.InlineIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.inline.authentication.provisioning.InlineInMemoryUserDetailsManager;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.am.service.exception.authentication.BadCredentialsException;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(InlineAuthenticationProviderConfiguration.class)
public class InlineAuthenticationProvider implements AuthenticationProvider, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(InlineAuthenticationProvider.class);
    private static final String USERNAME = "username";

    @Autowired
    private InlineIdentityProviderConfiguration configuration;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private InlineInMemoryUserDetailsManager userDetailsService;

    @Autowired
    private InlineIdentityProviderRoleMapper roleMapper;

    @Autowired
    private InlineIdentityProviderMapper mapper;

    @Override
    public void afterPropertiesSet() {
        for(io.gravitee.am.identityprovider.inline.model.User user : configuration.getUsers()) {
            LOGGER.debug("Add an inline user: {}", user);
            userDetailsService.createUser(user);
        }
    }

    @Override
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        return userDetailsService.loadUserByUsername((String) authentication.getPrincipal())
                .map(user -> {
                    String presentedPassword = authentication.getCredentials().toString();
                    if (!passwordEncoder.matches(presentedPassword, user.getPassword())) {
                        LOGGER.debug("Authentication failed: password does not match stored value");
                        throw new BadCredentialsException("Bad credentials");
                    }
                    return createUser(user);
                });
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        return userDetailsService.loadUserByUsername(username)
                .map(user -> createUser(user));
    }

    private List<String> getUserRoles(io.gravitee.am.identityprovider.inline.model.User inlineUser) {
        Set<String> roles = new HashSet();
        if (roleMapper != null && roleMapper.getRoles() != null) {
            roleMapper.getRoles().forEach((role, users) -> {
                Arrays.asList(users).forEach(u -> {
                    // user/group have the following syntax userAttribute=userValue
                    String[] attributes = u.split("=", 2);
                    String userAttribute = attributes[0];
                    String userValue = attributes[1];

                    // for inline provider we only find by username
                    if (USERNAME.equals(userAttribute) && inlineUser.getUsername().equals(userValue)) {
                        roles.add(role);
                    }
                });
            });
        }
        return new ArrayList<>(roles);
    }

    private User createUser(io.gravitee.am.identityprovider.inline.model.User inlineUser) {
        DefaultUser user = new DefaultUser(inlineUser.getUsername());
        user.setId(inlineUser.getUsername());

        // add additional information
        Map<String, Object> claims = new HashMap<>();
        claims.put(StandardClaims.SUB, inlineUser.getUsername());

        if (mapper != null && mapper.getMappers() != null && !mapper.getMappers().isEmpty()) {
            mapper.getMappers().forEach((k, v) -> {
                Object attributeValue = inlineUser.getAttributeValue(v);
                if (attributeValue != null) {
                    claims.put(k, attributeValue);
                }
            });
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
        user.setRoles(getUserRoles(inlineUser));

        return user;
    }
}
