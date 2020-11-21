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
package io.gravitee.am.identityprovider.jdbc.authentication;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.exception.authentication.UsernameNotFoundException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.jdbc.JdbcAbstractProvider;
import io.gravitee.am.identityprovider.jdbc.JdbcIdentityProviderMapper;
import io.gravitee.am.identityprovider.jdbc.JdbcIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.jdbc.authentication.spring.JdbcAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.jdbc.utils.ColumnMapRowMapper;
import io.reactivex.Maybe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(JdbcAuthenticationProviderConfiguration.class)
public class JdbcAuthenticationProvider extends JdbcAbstractProvider<AuthenticationProvider> implements AuthenticationProvider {

    @Autowired
    private JdbcIdentityProviderMapper mapper;

    @Autowired
    private JdbcIdentityProviderRoleMapper roleMapper;

    @Override
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        final String username = authentication.getPrincipal().toString();
        final String presentedPassword = authentication.getCredentials().toString();

        return selectUserByUsername(username)
                .switchIfEmpty(Maybe.error(new UsernameNotFoundException(username)))
                .map(result -> {
                    // check password
                    String password = String.valueOf(result.get(configuration.getPasswordAttribute()));
                    if (password == null) {
                        LOGGER.debug("Authentication failed: password is null");
                        throw new BadCredentialsException("Invalid account");
                    }

                    if (!passwordEncoder.matches(presentedPassword, password)) {
                        LOGGER.debug("Authentication failed: password does not match stored value");
                        throw new BadCredentialsException("Bad credentials");
                    }
                    // create the user
                    return createUser(result);
                });
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        return selectUserByUsername(username)
                .map(this::createUser);
    }

    private Maybe<Map<String, Object>> selectUserByUsername(String username) {
        final String sql = String.format(configuration.getSelectUserByUsernameQuery(), "?");

        return db.select(sql)
                .parameters(username)
                .get(ColumnMapRowMapper::mapRow)
                .firstElement();
    }

    private User createUser(Map<String, Object> claims) {
        // get username
        String username = claims.get(configuration.getUsernameAttribute()).toString();
        // get sub
        String sub = claims.containsKey(configuration.getIdentifierAttribute()) ? claims.get(configuration.getIdentifierAttribute()).toString() : username;
        // compute metadata
        computeMetadata(claims);

        // create the user
        DefaultUser user = new DefaultUser(username);
        // set technical id
        user.setId(sub);
        // set user roles
        user.setRoles(applyRoleMapping(claims));
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put(StandardClaims.SUB, sub);
        additionalInformation.put(StandardClaims.PREFERRED_USERNAME, username);
        // apply user mapping
        Map<String, Object> mappedAttributes = applyUserMapping(claims);
        additionalInformation.putAll(mappedAttributes);
        // update sub if user mapping has been changed
        if (additionalInformation.containsKey(StandardClaims.SUB)) {
            user.setId(additionalInformation.get(StandardClaims.SUB).toString());
        }
        // update username if user mapping has been changed
        if (additionalInformation.containsKey(StandardClaims.PREFERRED_USERNAME)) {
            user.setUsername(additionalInformation.get(StandardClaims.PREFERRED_USERNAME).toString());
        }
        // remove reserved claims
        additionalInformation.remove(configuration.getIdentifierAttribute());
        additionalInformation.remove(configuration.getUsernameAttribute());
        additionalInformation.remove(configuration.getPasswordAttribute());
        additionalInformation.remove(configuration.getMetadataAttribute());
        user.setAdditionalInformation(additionalInformation);

        return user;
    }

    private Map<String, Object> applyUserMapping(Map<String, Object> attributes) {
        if (!mappingEnabled()) {
            return attributes;
        }

        Map<String, Object> claims = new HashMap<>();
        this.mapper.getMappers().forEach((k, v) -> {
            if (attributes.containsKey(v)) {
                claims.put(k, attributes.get(v));
            }
        });
        return claims;
    }

    private List<String> applyRoleMapping(Map<String, Object> attributes) {
        if (!roleMappingEnabled()) {
            return Collections.emptyList();
        }

        Set<String> roles = new HashSet<>();
        roleMapper.getRoles().forEach((role, users) -> {
            Arrays.asList(users).forEach(u -> {
                // role mapping have the following syntax userAttribute=userValue
                String[] roleMapping = u.split("=", 2);
                String userAttribute = roleMapping[0];
                String userValue = roleMapping[1];
                if (attributes.containsKey(userAttribute)) {
                    Object attribute = attributes.get(userAttribute);
                    // attribute is a list
                    if (attribute instanceof Collection && ((Collection) attribute).contains(userValue)) {
                        roles.add(role);
                    } else if (userValue.equals(attributes.get(userAttribute))) {
                        roles.add(role);
                    }
                }
            });
        });
        return new ArrayList<>(roles);
    }

    private boolean mappingEnabled() {
        return this.mapper != null && this.mapper.getMappers() != null && !this.mapper.getMappers().isEmpty();
    }

    private boolean roleMappingEnabled() {
        return this.roleMapper != null && this.roleMapper.getRoles() != null && !this.roleMapper.getRoles().isEmpty();
    }
}
