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
package io.gravitee.am.management.handlers.admin.provider.security;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.management.handlers.admin.authentication.WebAuthenticationDetails;
import io.gravitee.am.management.handlers.admin.security.IdentityProviderManager;
import io.gravitee.am.model.Domain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainBasedAuthenticationProvider implements AuthenticationProvider {

    private final Logger logger = LoggerFactory.getLogger(DomainBasedAuthenticationProvider.class);

    /**
     * Constant to use while setting identity provider used to authenticate a user
     */
    private static final String SOURCE = "source";

    @Autowired
    private Domain domain;

    private IdentityProviderManager identityProviderManager;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        WebAuthenticationDetails webAuthenticationDetails = (WebAuthenticationDetails) authentication.getDetails();
        Map<String, String> details = new HashMap();
        details.put(Claims.ip_address, webAuthenticationDetails.getRemoteAddress());
        details.put(Claims.user_agent, webAuthenticationDetails.getUserAgent());
        details.put(Claims.domain, domain.getId());
        Set<String> identities = domain.getIdentities();
        Iterator<String> iter = identities.iterator();
        io.gravitee.am.identityprovider.api.User user = null;
        AuthenticationException lastException = null;

        // Create a end-user authentication for underlying providers associated to the domain
        io.gravitee.am.identityprovider.api.Authentication provAuthentication = new EndUserAuthentication(
                authentication.getName(),
                authentication.getCredentials(), new ManagementAuthenticationContext());

        while (iter.hasNext() && user == null) {
            String provider = iter.next();
            io.gravitee.am.identityprovider.api.AuthenticationProvider authenticationProvider =
                    identityProviderManager.get(provider);

            if (authenticationProvider == null) {
                throw new BadCredentialsException("Unable to load authentication provider " + provider + ", an error occurred during the initialization stage");
            }

            try {
                user = authenticationProvider.loadUserByUsername(provAuthentication).blockingGet();
                // set user identity provider source
                details.put(SOURCE, provider);
                lastException = null;
            } catch (Exception ex) {
                logger.info("Unable to authenticate user {} with provider {}", authentication.getName(), provider, ex);
                lastException = new BadCredentialsException(ex.getMessage(), ex);
            }
        }

        if (lastException != null) {
            throw lastException;
        }

        if (user != null) {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(user, provAuthentication.getCredentials(), AuthorityUtils.NO_AUTHORITIES);
            authenticationToken.setDetails(details);
            return authenticationToken;
        }

        throw new BadCredentialsException("No user found for providers " + StringUtils.collectionToDelimitedString(identities, ","));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(
                UsernamePasswordAuthenticationToken.class);
    }

    public void setIdentityProviderManager(IdentityProviderManager identityProviderManager) {
        this.identityProviderManager = identityProviderManager;
    }
}
