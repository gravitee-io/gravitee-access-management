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

import io.gravitee.am.common.exception.authentication.UsernameNotFoundException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.management.handlers.management.api.authentication.manager.idp.IdentityProviderManager;
import io.gravitee.am.management.handlers.management.api.authentication.web.WebAuthenticationDetails;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Organization;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.authentication.crypto.password.bcrypt.BCryptPasswordEncoder;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ManagementAuthenticationProvider implements AuthenticationProvider {

    private final Logger logger = LoggerFactory.getLogger(ManagementAuthenticationProvider.class);

    /**
     * Constant to use when setting identity provider used to authenticate a user
     */
    private static final String SOURCE = "source";

    @Autowired
    private OrganizationService organizationService;

    @Value("${http.blockingGet.timeoutMillis:120000}")
    private long blockingGetTimeoutMillis = 120000;

    private BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

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

        // get organization identity providers
        Organization organization = null;
        try {
            Single<Organization> organizationSingle = organizationService.findById(organizationId);
            if (blockingGetTimeoutMillis > 0) {
                organizationSingle = organizationSingle.timeout(blockingGetTimeoutMillis, TimeUnit.MILLISECONDS);
            }
            organization = organizationSingle.blockingGet();
        } catch (Exception e) {
            throw new InternalAuthenticationServiceException("Unable to find organization when trying to authenticate the end-user");
        }

        if (organization == null) {
            throw new InternalAuthenticationServiceException("No organization found when trying to authenticate the end-user");
        }
        List<String> identities = organization.getIdentities() == null ? new ArrayList<>() : new ArrayList<>(organization.getIdentities());
        // We add transient providers to the list as there are not persisted
        // the gravitee provider and all providers defined into the gravitee.yaml
        // will be added.
        identities.addAll(identityProviderManager.getTransientProviders());
        Iterator<String> iter = identities.iterator();
        io.gravitee.am.identityprovider.api.User user = null;
        AuthenticationException lastException = null;

        // Create a end-user authentication for underlying providers associated to the organization
        final SimpleAuthenticationContext context = new SimpleAuthenticationContext();
        details.forEach( (k,v) -> context.setAttribute(k,v));

        io.gravitee.am.identityprovider.api.Authentication provAuthentication = new EndUserAuthentication(
                authentication.getName(),
                authentication.getCredentials(), context);
        int userNotFoundException = 0;
        int userException = 0;
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
                userException++;
                if (ex instanceof UsernameNotFoundException) {
                    userNotFoundException ++;
                }
                logger.info("Unable to authenticate user {} with provider {}", authentication.getName(), provider, ex);
                lastException = new BadCredentialsException(ex.getMessage(), ex);
            }
        }

        if (lastException != null) {
            if (userException == userNotFoundException) {
                //Didn't find user in any IDP (Mongo or JDBC), so no password encoding has been proceeded.
                doFakePasswordEncoding(authentication.getCredentials().toString());
            }
            throw lastException;
        }

        if (user != null) {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(user, provAuthentication.getCredentials(), AuthorityUtils.NO_AUTHORITIES);
            authenticationToken.setDetails(details);
            return authenticationToken;
        }
        if (userException == userNotFoundException) {
            //Didn't find user in any IDP (Mongo or JDBC), so no password encoding has been proceeded.
            doFakePasswordEncoding(authentication.getCredentials().toString());
        }
        throw new BadCredentialsException("No user found for providers " + StringUtils.collectionToDelimitedString(identities, ","));
    }

    private void doFakePasswordEncoding(String password){
        //PEN-21 Encoding password takes a while. To ensure execution time the same for not existing user, introduced fake password checking.
        bCryptPasswordEncoder.matches(password, "$2a$10$hdjt9YGrSudbIljTqAtcW.KOxNJscq00Nxv088wPy6GDKXCJe0aCm");
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
