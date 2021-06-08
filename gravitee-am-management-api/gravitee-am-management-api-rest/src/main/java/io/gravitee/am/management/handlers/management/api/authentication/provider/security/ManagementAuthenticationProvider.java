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
package io.gravitee.am.management.handlers.management.api.authentication.provider.security;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.management.handlers.management.api.authentication.manager.idp.IdentityProviderManager;
import io.gravitee.am.management.handlers.management.api.authentication.web.WebAuthenticationDetails;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Organization;
import io.gravitee.am.service.OrganizationService;
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

import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ManagementAuthenticationProvider implements AuthenticationProvider {

    private final Logger logger = LoggerFactory.getLogger(ManagementAuthenticationProvider.class);

    /**
     * Constant to use while setting identity provider used to authenticate a user
     */
    private static final String SOURCE = "source";

    @Autowired
    private OrganizationService organizationService;

    private IdentityProviderManager identityProviderManager;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        WebAuthenticationDetails webAuthenticationDetails = (WebAuthenticationDetails) authentication.getDetails();
        Map<String, String> details = new HashMap<>();

        if (webAuthenticationDetails != null) {
            details.put(Claims.ip_address, webAuthenticationDetails.getRemoteAddress());
            details.put(Claims.user_agent, webAuthenticationDetails.getUserAgent());
            details.put(Claims.organization, webAuthenticationDetails.getOrganizationId());
        }

        details.putIfAbsent(Claims.organization, Organization.DEFAULT);
        String organizationId = details.get(Claims.organization);

        List<String> identities = identityProviderManager.getAuthenticationProviderFor(organizationId);
        Organization organization = organizationService.findById(organizationId).blockingGet();
        if (organization.getIdentities() != null) {
            identities.addAll(organization.getIdentities());
        }

        Iterator<String> iter = identities.iterator();
        io.gravitee.am.identityprovider.api.User user = null;
        AuthenticationException lastException = null;

        // Create a end-user authentication for underlying providers associated to the organization
        final SimpleAuthenticationContext context = new SimpleAuthenticationContext();
        details.forEach( (k,v) -> context.setAttribute(k,v));

        io.gravitee.am.identityprovider.api.Authentication provAuthentication = new EndUserAuthentication(
                authentication.getName(),
                authentication.getCredentials(), context);

        while (iter.hasNext() && user == null) {
            String provider = iter.next();

            IdentityProvider identityProvider = identityProviderManager.getIdentityProvider(provider);

            if(identityProvider == null || identityProvider.isExternal()) {
                // Skip external identity provider for authentication with credentials.
                continue;
            }

            io.gravitee.am.identityprovider.api.AuthenticationProvider authenticationProvider =
                    identityProviderManager.get(provider);

            if (authenticationProvider == null) {
                lastException = new BadCredentialsException("Unable to load authentication provider " + provider + ", an error occurred during the initialization stage");
                continue;
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
