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
import io.gravitee.am.service.exception.authentication.BadCredentialsException;
import io.gravitee.am.service.exception.authentication.InternalAuthenticationServiceException;
import io.gravitee.am.service.exception.authentication.UsernameNotFoundException;
import io.reactivex.Maybe;
import org.ldaptive.*;
import org.ldaptive.auth.AuthenticationRequest;
import org.ldaptive.auth.AuthenticationResponse;
import org.ldaptive.auth.Authenticator;
import org.ldaptive.auth.SearchDnResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(LdapAuthenticationProviderConfiguration.class)
public class LdapAuthenticationProvider implements AuthenticationProvider, InitializingBean {

    private final Logger LOGGER = LoggerFactory.getLogger(LdapAuthenticationProvider.class);

    private static final String MEMBEROF_ATTRIBUTE = "memberOf";

    @Autowired
    private LdapIdentityProviderMapper mapper;

    @Autowired
    private LdapIdentityProviderRoleMapper roleMapper;

    @Autowired
    private LdapIdentityProviderConfiguration configuration;

    private String identifierAttribute = "uid";

    @Autowired
    private Authenticator authenticator;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    @Qualifier("groupSearchExecutor")
    private SearchExecutor groupSearchExecutor;

    @Autowired
    @Qualifier("userSearchExecutor")
    private SearchExecutor userSearchExecutor;

    @Override
    public void afterPropertiesSet() {
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
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        Maybe<User> userSource = Maybe.create(emitter -> {
            try {
                String username = (String) authentication.getPrincipal();
                String password = (String) authentication.getCredentials();
                // authenticate user
                // unable *={0} authentication filter (ldaptive use *={user})
                ((SearchDnResolver) authenticator.getDnResolver()).setUserFilterParameters(Collections.singleton(username).toArray());
                AuthenticationResponse response = authenticator.authenticate(
                        new AuthenticationRequest(username, new Credential(password), ReturnAttributes.ALL_USER.value()));
                if (response.getResult()) { // authentication succeeded
                    LdapEntry userEntry = response.getLdapEntry();
                    // fetch user groups
                    try {
                        groupSearchExecutor.getSearchFilter().setParameter(0, userEntry.getDn());
                        SearchResult searchResult = groupSearchExecutor.search(connectionFactory).getResult();
                        Collection<LdapEntry> groupEntries = searchResult.getEntries();
                        String[] groups = groupEntries.stream()
                                .map(groupEntry -> groupEntry.getAttributes()
                                        .stream()
                                        .map(ldapAttribute -> ldapAttribute.getStringValue())
                                        .collect(Collectors.toList()))
                                .flatMap(List::stream)
                                .toArray(size -> new String[size]);
                        userEntry.addAttribute(new LdapAttribute(MEMBEROF_ATTRIBUTE, groups));
                    } catch (Exception e) {
                        LOGGER.warn("No group found for user {}", userEntry.getDn(), e);
                    }
                    // return user
                    emitter.onSuccess(createUser(userEntry));
                } else { // authentication failed
                    emitter.onError(new BadCredentialsException(response.getMessage()));
                }
            } catch (LdapException e) {
                emitter.onError(new InternalAuthenticationServiceException(e.getMessage(), e));
            }
        });

        return userSource;
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        Maybe<User> userSource = Maybe.create(emitter -> {
            try {
                // find user
                userSearchExecutor.getSearchFilter().setParameter(0, username);
                SearchResult userSearchResult = userSearchExecutor.search(connectionFactory).getResult();
                LdapEntry userEntry = userSearchResult.getEntry();
                if (userEntry != null) {
                    // fetch user groups
                    try {
                        groupSearchExecutor.getSearchFilter().setParameter(0, userEntry.getDn());
                        SearchResult searchResult = groupSearchExecutor.search(connectionFactory).getResult();
                        Collection<LdapEntry> groupEntries = searchResult.getEntries();
                        String[] groups = groupEntries.stream()
                                .map(groupEntry -> groupEntry.getAttributes()
                                        .stream()
                                        .map(ldapAttribute -> ldapAttribute.getStringValue())
                                        .collect(Collectors.toList()))
                                .flatMap(List::stream)
                                .toArray(size -> new String[size]);
                        userEntry.addAttribute(new LdapAttribute(MEMBEROF_ATTRIBUTE, groups));
                    } catch (Exception e) {
                        LOGGER.warn("No group found for user {}", userEntry.getDn(), e);
                    }
                    // return user
                    emitter.onSuccess(createUser(userEntry));
                } else { // failed to find user
                    emitter.onError(new UsernameNotFoundException(username));
                }
            } catch (LdapException e) {
                emitter.onError(new InternalAuthenticationServiceException(e.getMessage(), e));
            }
        });

        return userSource;
    }

    private User createUser(LdapEntry ldapEntry) {
        DefaultUser user = new DefaultUser(ldapEntry.getAttribute(identifierAttribute).getStringValue());

        // add additional information
        Map<String, Object> claims = new HashMap<>();
        if (mapper.getMappers() != null && !mapper.getMappers().isEmpty()) {
            mapper.getMappers().forEach((k, v) -> {
                LdapAttribute ldapAttribute = ldapEntry.getAttribute(v);
                if (ldapAttribute != null) {
                    Collection<String> ldapValues = ldapAttribute.getStringValues();
                    if (ldapValues != null) {
                        if (ldapValues.size() == 1) {
                            claims.put(k, ldapValues.iterator().next());
                        } else {
                            claims.put(k, ldapValues);
                        }
                    }
                }
            });
        } else {
            // default values
            claims.put("sub", user.getUsername());
            addClaim(claims, ldapEntry, "email", "mail");
            addClaim(claims, ldapEntry, "name", "displayname");
            addClaim(claims, ldapEntry, "given_name", "givenname");
            addClaim(claims, ldapEntry, "family_name", "sn");
        }
        user.setAdditonalInformation(claims);

        // set user roles
        user.setRoles(getUserRoles(ldapEntry));

        return user;
    }

    private Map<String, Object> addClaim(Map<String, Object> claims, LdapEntry ldapEntry, String claimKey, String attributeKey) {
        if (ldapEntry.getAttribute(attributeKey) != null) {
            claims.put(claimKey, ldapEntry.getAttribute(attributeKey).getStringValue());
        }
        return claims;
    }

    private List<String> getUserRoles(LdapEntry ldapEntry) {
        Set<String> roles = new HashSet();
        if (roleMapper != null && roleMapper.getRoles() != null) {
            roleMapper.getRoles().forEach((role, users) -> {
                Arrays.asList(users).forEach(u -> {
                    // user/group have the following syntax userAttribute=userValue
                    String[] attributes = u.split("=");
                    String userAttribute = attributes[0];
                    String userValue = attributes[1];

                    // group
                    if (MEMBEROF_ATTRIBUTE.equals(userAttribute) && ldapEntry.getAttribute(MEMBEROF_ATTRIBUTE) != null) {
                        if (ldapEntry.getAttribute(MEMBEROF_ATTRIBUTE).getStringValues().contains(userValue)) {
                            roles.add(role);
                        }
                    // user
                    } else {
                        if (ldapEntry.getAttribute(userAttribute) != null &&
                                ldapEntry.getAttribute(userAttribute).getStringValue().equals(userValue)) {
                            roles.add(role);
                        }
                    }
                });
            });
        }
        return new ArrayList<>(roles);
    }
}
