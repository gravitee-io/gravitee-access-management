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

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.exception.authentication.InternalAuthenticationServiceException;
import io.gravitee.am.common.exception.authentication.UsernameNotFoundException;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.ldap.LdapIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.ldap.authentication.spring.LdapAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.ldap.common.utils.LdapUtils;
import io.gravitee.common.service.AbstractService;
import io.reactivex.Maybe;
import org.ldaptive.*;
import org.ldaptive.auth.AuthenticationRequest;
import org.ldaptive.auth.AuthenticationResponse;
import org.ldaptive.auth.Authenticator;
import org.ldaptive.pool.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(LdapAuthenticationProviderConfiguration.class)
public class LdapAuthenticationProvider extends AbstractService<AuthenticationProvider> implements AuthenticationProvider, InitializingBean {

    private final Logger LOGGER = LoggerFactory.getLogger(LdapAuthenticationProvider.class);

    private static final String MEMBEROF_ATTRIBUTE = "memberOf";

    @Autowired
    private IdentityProviderMapper mapper;

    @Autowired
    private IdentityProviderRoleMapper roleMapper;

    @Autowired
    private LdapIdentityProviderConfiguration configuration;

    private String identifierAttribute = "uid";

    @Autowired
    private Authenticator authenticator;

    @Autowired
    private ConnectionFactory searchConnectionFactory;

    @Autowired
    private ConnectionPool bindConnectionPool;

    @Autowired
    private ConnectionPool searchConnectionPool;

    @Autowired
    @Qualifier("userSearchExecutor")
    private SearchExecutor userSearchExecutor;

    private ScheduledExecutorService executorService;

    @Override
    public void afterPropertiesSet() {
        String searchFilter = configuration.getUserSearchFilter();
        LOGGER.debug("Looking for a LDAP user's identifier using search filter [{}]", searchFilter);

        if (searchFilter != null) {
            // Search filter can be uid={0} or mail={0}
            try {
                identifierAttribute = LdapUtils.extractAttribute(searchFilter);
            } catch (Exception e) {
                LOGGER.debug("Fail to set identifierAttribute from searchFilter : {}", searchFilter);
            }
        }

        LOGGER.info("User identifier is based on the [{}] attribute", identifierAttribute);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.executorService = Executors.newScheduledThreadPool(1);
        initConnectionPools(1);
    }

    private void initConnectionPools(int retryDelayInSec) {
        LOGGER.info("Init LDAP {} connection pools", configuration.getContextSourceUrl());
        try {
            if (bindConnectionPool != null) {
                bindConnectionPool.initialize();
            }
            if (searchConnectionPool != null) {
                searchConnectionPool.initialize();
            }
        } catch (IllegalStateException e) {
            final int nextDelay = retryDelayInSec < 60 ? retryDelayInSec * 2 : retryDelayInSec;
            this.executorService.schedule(() -> initConnectionPools(nextDelay), retryDelayInSec, TimeUnit.SECONDS);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (this.executorService != null) {
            this.executorService.shutdownNow();
        }

        LOGGER.info("Close LDAP {} connection pools", configuration.getContextSourceUrl());
        if (bindConnectionPool != null) {
            bindConnectionPool.close();
        }
        if (searchConnectionPool != null) {
            searchConnectionPool.close();
        }
    }

    @Override
    public Maybe<User> loadUserByUsername(Authentication authentication) {
        return Maybe.fromCallable(() -> {
            try {
                String username = (String) authentication.getPrincipal();
                String password = (String) authentication.getCredentials();
                // authenticate user and fetch groups if exist
                AuthenticationResponse response = authenticator.authenticate(new AuthenticationRequest(username, new Credential(password), ReturnAttributes.ALL_USER.value()));
                if (response.getResult()) { // authentication succeeded
                    LdapEntry userEntry = response.getLdapEntry();
                    return userEntry;
                } else { // authentication failed
                    LOGGER.debug("Failed to authenticate user {}", response.getMessage());
                    throw new BadCredentialsException(response.getMessage());
                }
            } catch (LdapException e) {
                LOGGER.error("An error occurs during LDAP authentication", e);
                throw new InternalAuthenticationServiceException(e.getMessage(), e);
            }
        })
        .map(ldapUser -> createUser(authentication.getContext(), ldapUser));
    }

    @Override
    public Maybe<User> loadUserByUsername(String username) {
        return Maybe.fromCallable(() -> {
            try {
                // find user
                SearchFilter searchFilter = createSearchFilter(userSearchExecutor, username);
                SearchResult userSearchResult = userSearchExecutor.search(searchConnectionFactory, searchFilter).getResult();
                LdapEntry userEntry = userSearchResult.getEntry();
                if (userEntry != null) {
                    return userEntry;
                } else { // failed to find user
                    throw new UsernameNotFoundException(username);
                }
            } catch (LdapException e) {
                LOGGER.error("An error occurs while searching for a LDAP user", e);
                throw new InternalAuthenticationServiceException(e.getMessage(), e);
            }
        })
        .map(ldapUser -> createUser(new SimpleAuthenticationContext(), ldapUser));

    }

    private User createUser(AuthenticationContext authContext, LdapEntry ldapEntry) {
        DefaultUser user = new DefaultUser(ldapEntry.getAttribute(identifierAttribute).getStringValue());
        user.setId(user.getUsername());
        // add additional information
        Map<String, Object> claims = new HashMap<>();
        claims.put(StandardClaims.SUB, user.getUsername());
        if (mapper.getMappers() != null && !mapper.getMappers().isEmpty()) {
            claims.putAll(this.mapper.apply(authContext, toMap(ldapEntry)));
        } else {
            // default values
            addClaim(claims, ldapEntry, StandardClaims.NAME, "displayname");
            addClaim(claims, ldapEntry, StandardClaims.GIVEN_NAME, "givenname");
            addClaim(claims, ldapEntry, StandardClaims.FAMILY_NAME, "sn");
            addClaim(claims, ldapEntry, StandardClaims.EMAIL, "mail");
            addClaim(claims, ldapEntry, StandardClaims.PREFERRED_USERNAME, user.getUsername());
        }
        user.setAdditionalInformation(claims);

        // set user roles
        user.setRoles(getUserRoles(authContext, ldapEntry));

        return user;
    }

    private Map<String, Object> addClaim(Map<String, Object> claims, LdapEntry ldapEntry, String claimKey, String attributeKey) {
        if (ldapEntry.getAttribute(attributeKey) != null) {
            claims.put(claimKey, ldapEntry.getAttribute(attributeKey).getStringValue());
        }
        return claims;
    }

    private List<String> getUserRoles(AuthenticationContext authContext, LdapEntry ldapEntry) {
        if (roleMapper != null && roleMapper.getRoles() != null) {
            return this.roleMapper.apply(authContext, toMap(ldapEntry));
        }
        return Collections.emptyList();
    }

    private Map<String, Object> toMap(LdapEntry ldapEntry) {
        Map<String, Object> attributes = new HashMap<>();
        ldapEntry.getAttributes().forEach(attr -> {
            if (MEMBEROF_ATTRIBUTE.equals(attr.getName())) {
                attributes.put(attr.getName(), attr.getStringValues());
            } else {
                attributes.put(attr.getName(), attr.getStringValue());
            }
        });
        return attributes;
    }

    /**
     * Constructs a new search filter using {@link SearchExecutor} as a template and
     * the username as a parameter.
     *
     * @param executor the executor
     * @param username the username
     * @return  Search filter with parameters applied.
     */
    private SearchFilter createSearchFilter(final SearchExecutor executor, final String username) {
        final SearchFilter filter = new SearchFilter();
        filter.setFilter(executor.getSearchFilter().getFilter());
        filter.setParameter(0, username);

        LOGGER.debug("Constructed LDAP search filter [{}]", filter.format());
        return filter;
    }
}
