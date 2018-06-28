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
package io.gravitee.am.identityprovider.ldap.authentication;

import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.ldap.LdapIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.ldap.LdapIdentityProviderMapper;
import io.gravitee.am.identityprovider.ldap.LdapIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.ldap.authentication.spring.LdapAuthenticationProviderConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ldap.NamingException;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.ldap.authentication.LdapAuthenticator;
import org.springframework.security.ldap.search.LdapUserSearch;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(LdapAuthenticationProviderConfiguration.class)
public class LdapAuthenticationProvider implements AuthenticationProvider, InitializingBean {

    private final Logger LOGGER = LoggerFactory.getLogger(LdapAuthenticationProvider.class);

    private static final String MEMBEROF_ATTRIBUTE = "memberOf";

    @Autowired
    private LdapAuthenticator authenticator;

    @Autowired
    private LdapAuthoritiesPopulator authoritiesPopulator;

    @Autowired
    private LdapIdentityProviderMapper mapper;

    @Autowired
    private LdapIdentityProviderRoleMapper roleMapper;

    @Autowired
    private LdapUserSearch userSearch;

    @Autowired
    private LdapIdentityProviderConfiguration configuration;

    private String identifierAttribute = "uid";

    @Override
    public void afterPropertiesSet() throws Exception {
        String searchFilter = configuration.getUserSearchFilter();
        LOGGER.debug("Looking for a LDAP user's identifier using search filter [{}]", searchFilter);

        if (searchFilter != null) {
            // Search filter can be uid={0} or mail={0}
            try {
                identifierAttribute = searchFilter.replaceAll("[()]", "").split("=")[0];
            } catch (Exception e) {
                LOGGER.debug("Fail to set identifierAttribute from searchFilter : {}", searchFilter);
            }
        }

        LOGGER.info("User identifier is based on the [{}] attribute", identifierAttribute);
    }

    @Override
    public User loadUserByUsername(Authentication authentication) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            DirContextOperations authenticate;
            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

                // authenticate user
                authenticate = authenticator.authenticate(new UsernamePasswordAuthenticationToken(
                        authentication.getPrincipal(), authentication.getCredentials()));

                // fetch user groups
                try {
                    List<String> groups = authoritiesPopulator
                            .getGrantedAuthorities(authenticate, authenticate.getNameInNamespace()).stream()
                            .map(a -> a.getAuthority())
                            .collect(Collectors.toList());
                    authenticate.setAttributeValue(MEMBEROF_ATTRIBUTE, groups);
                } catch (Exception e) {
                    LOGGER.warn("No group found for user {}", authenticate.getNameInNamespace(), e);
                }
            } finally {
                Thread.currentThread().setContextClassLoader(classLoader);
            }

            return createUser(authenticate);
        } catch (UsernameNotFoundException notFound) {
            throw notFound;
        } catch (NamingException ldapAccessFailure) {
            throw new InternalAuthenticationServiceException(
                    ldapAccessFailure.getMessage(), ldapAccessFailure);
        }
    }

    @Override
    public User loadUserByUsername(String username) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            DirContextOperations authenticate;
            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

                // find user
                authenticate = userSearch.searchForUser(username);
                ((DirContextAdapter) authenticate).setUpdateMode(false);

                // fetch user groups
                try {
                    List<String> groups = authoritiesPopulator
                            .getGrantedAuthorities(authenticate, authenticate.getNameInNamespace()).stream()
                            .map(a -> a.getAuthority())
                            .collect(Collectors.toList());
                    authenticate.setAttributeValue(MEMBEROF_ATTRIBUTE, groups);
                } catch (Exception e) {
                    LOGGER.warn("No group found for user {}", authenticate.getNameInNamespace(), e);
                }
            } finally {
                Thread.currentThread().setContextClassLoader(classLoader);
            }

            return createUser(authenticate);
        } catch (UsernameNotFoundException notFound) {
            throw notFound;
        } catch (NamingException ldapAccessFailure) {
            throw new InternalAuthenticationServiceException(
                    ldapAccessFailure.getMessage(), ldapAccessFailure);
        }
    }

    private User createUser(DirContextOperations authenticate) {
        DefaultUser user = new DefaultUser(authenticate.getStringAttribute(identifierAttribute));

        // add additional information
        Map<String, Object> claims = new HashMap<>();
        if (mapper.getMappers() != null) {
            mapper.getMappers().forEach((k, v) -> claims.put(k, authenticate.getObjectAttribute(v)));
        } else {
            // default values
            claims.put("sub", authenticate.getStringAttribute("uid"));
            claims.put("email", authenticate.getStringAttribute("mail"));
            claims.put("name", authenticate.getStringAttribute("displayname"));
            claims.put("given_name", authenticate.getStringAttribute("givenname"));
            claims.put("family_name", authenticate.getStringAttribute("sn"));
        }
        user.setAdditonalInformation(claims);

        // set user roles
        user.setRoles(getUserRoles(authenticate));

        return user;
    }

    private List<String> getUserRoles(DirContextOperations authenticate) {
        Set<String> roles = new HashSet();
        if (roleMapper != null && roleMapper.getRoles() != null) {
            roleMapper.getRoles().forEach((role, users) -> {
                Arrays.asList(users).forEach(u -> {
                    // user/group have the following syntax userAttribute=userValue
                    String[] attributes = u.split("=");
                    String userAttribute = attributes[0];
                    String userValue = attributes[1];

                    // group
                    if (MEMBEROF_ATTRIBUTE.equals(userAttribute) && authenticate.attributeExists(MEMBEROF_ATTRIBUTE)) {
                        if (((List) authenticate.getObjectAttribute(MEMBEROF_ATTRIBUTE)).contains(userValue)) {
                            roles.add(role);
                        }
                    // user
                    } else {
                        if (authenticate.attributeExists(userAttribute) &&
                                authenticate.getStringAttribute(userAttribute).equals(userValue)) {
                            roles.add(role);
                        }
                    }
                });
            });
        }
        return new ArrayList<>(roles);
    }
}
