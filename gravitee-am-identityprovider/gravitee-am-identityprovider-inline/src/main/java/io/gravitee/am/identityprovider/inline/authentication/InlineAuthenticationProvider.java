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

import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.inline.InlineIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.inline.InlineIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.inline.authentication.provisioning.InlineInMemoryUserDetailsManager;
import io.gravitee.am.identityprovider.inline.authentication.userdetails.InlineUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
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

    @Override
    public void afterPropertiesSet() throws Exception {
        for(io.gravitee.am.identityprovider.inline.model.User user : configuration.getUsers()) {
            List<GrantedAuthority> authorities = AuthorityUtils.NO_AUTHORITIES; //createAuthorityList(user.getRoles());
            InlineUser newUser = new InlineUser(user.getUsername(), user.getPassword(), authorities);
            newUser.setFirstname(user.getFirstname());
            newUser.setLastname(user.getLastname());

            LOGGER.debug("Add an inline user: {}", newUser);
            userDetailsService.createUser(newUser);
        }
    }

    @Override
    public io.gravitee.am.identityprovider.api.User loadUserByUsername(Authentication authentication) {
        final UserDetails userDetails = userDetailsService.loadUserByUsername((String) authentication.getPrincipal());

        String presentedPassword = authentication.getCredentials().toString();

        if (!passwordEncoder.matches(presentedPassword, userDetails.getPassword())) {
            LOGGER.debug("Authentication failed: password does not match stored value");
            throw new BadCredentialsException("Bad getCredentials");
        }

        return createUser(userDetails);
    }

    @Override
    public User loadUserByUsername(String username) {
        final UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        return createUser(userDetails);
    }

    private List<String> getUserRoles(InlineUser inlineUser) {
        Set<String> roles = new HashSet();
        if (roleMapper != null && roleMapper.getRoles() != null) {
            roleMapper.getRoles().forEach((role, users) -> {
                Arrays.asList(users).forEach(u -> {
                    // user/group have the following syntax userAttribute=userValue
                    String[] attributes = u.split("=");
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

    private User createUser(UserDetails userDetails) {
        InlineUser inlineUser = (InlineUser) userDetails;
        DefaultUser user = new DefaultUser(inlineUser.getUsername());

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", inlineUser.getUsername());
        claims.put("given_name", inlineUser.getFirstname());
        claims.put("family_name", inlineUser.getLastname());
        user.setAdditonalInformation(claims);

        // set user roles
        user.setRoles(getUserRoles(inlineUser));

        return user;
    }
}
