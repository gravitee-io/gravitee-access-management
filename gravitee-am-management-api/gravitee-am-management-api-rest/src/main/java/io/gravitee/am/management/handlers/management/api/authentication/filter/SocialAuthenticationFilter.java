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
package io.gravitee.am.management.handlers.management.api.authentication.filter;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.authentication.http.JettyHttpServerRequest;
import io.gravitee.am.management.handlers.management.api.authentication.manager.idp.IdentityProviderManager;
import io.gravitee.am.management.handlers.management.api.authentication.provider.generator.JWTGenerator;
import io.gravitee.am.management.handlers.management.api.authentication.provider.security.EndUserAuthentication;
import io.gravitee.am.management.handlers.management.api.authentication.service.AuthenticationService;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SocialAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    private final Logger logger = LoggerFactory.getLogger(SocialAuthenticationFilter.class);

    /**
     * Constant to use while setting identity provider used to authenticate a user
     */
    private static final String SOURCE = "source";
    private static final String PROVIDER_PARAMETER = "provider";
    private static final String errorPage = "/auth/access/error";
    private static final String REDIRECT_URI = "redirect_uri";

    private AuthenticationEventPublisher authenticationEventPublisher;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private JWTGenerator jwtGenerator;

    @Autowired
    private AuthenticationSuccessHandler successHandler;

    public SocialAuthenticationFilter(String defaultFilterProcessesUrl) {
        super(defaultFilterProcessesUrl);
        setAuthenticationManager(new NoopAuthenticationManager());
        SimpleUrlAuthenticationFailureHandler failureHandler = new SimpleUrlAuthenticationFailureHandler(errorPage);
        failureHandler.setAllowSessionCreation(false);
        setAuthenticationFailureHandler(failureHandler);
        setAllowSessionCreation(false);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        // get oauth2 provider
        String providerId = request.getParameter(PROVIDER_PARAMETER);
        final IdentityProvider identityProvider = identityProviderManager.getIdentityProvider(providerId);
        final AuthenticationProvider authenticationProvider = identityProviderManager.get(providerId);

        if (authenticationProvider == null || identityProvider == null || identityProvider.getReferenceType() != ReferenceType.ORGANIZATION) {
            throw new ProviderNotFoundException("Social Provider " + providerId + " not found");
        }

        SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(new JettyHttpServerRequest(request));
        authenticationContext.set(REDIRECT_URI, buildRedirectUri(request));
        EndUserAuthentication provAuthentication = new EndUserAuthentication("__social__", "__social__", authenticationContext);

        try {
            User user = authenticationProvider.loadUserByUsername(provAuthentication).blockingGet();
            if (user == null) {
                logger.error("User is null, fail to authenticate user");
                throw new BadCredentialsException("User is null after authentication process");
            }

            // set user identity provider source
            Map<String, String> details = new LinkedHashMap<>();
            details.put(SOURCE, providerId);
            details.put(Claims.organization, identityProvider.getReferenceId());
            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(user, provAuthentication.getCredentials(), AuthorityUtils.NO_AUTHORITIES);
            usernamePasswordAuthenticationToken.setDetails(details);
            return usernamePasswordAuthenticationToken;
        } catch (Exception ex) {
            logger.error("Unable to authenticate with oauth2 provider {}", providerId, ex);
            throw new BadCredentialsException(ex.getMessage(), ex);
        }
    }

    @Override
    protected final void successfulAuthentication(HttpServletRequest request,
                                                  HttpServletResponse response, FilterChain chain, Authentication authResult)
            throws IOException, ServletException {

        if (logger.isDebugEnabled()) {
            logger.debug("Authentication success. Updating SecurityContextHolder to contain: "
                    + authResult);
        }

        SecurityContextHolder.getContext().setAuthentication(authResult);

        successHandler.onAuthenticationSuccess(request, response, authResult);
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        super.setApplicationEventPublisher(eventPublisher);
        this.authenticationEventPublisher = new DefaultAuthenticationEventPublisher(eventPublisher);
    }

    @Override
    protected boolean requiresAuthentication(HttpServletRequest request, HttpServletResponse response) {
        return super.requiresAuthentication(request, response) && !authenticated() && request.getParameter(PROVIDER_PARAMETER) != null;
    }

    /**
     * Determines if a user is already authenticated.
     * @return
     */
    private boolean authenticated() {
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String buildRedirectUri(HttpServletRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(request.getRequestURL().toString());
        // append provider query param to avoid redirect mismatch exception
        builder.queryParam("provider", request.getParameter("provider"));

        return builder.build(false).toUriString();
    }


    private static class NoopAuthenticationManager implements AuthenticationManager {

        @Override
        public Authentication authenticate(Authentication authentication)
                throws AuthenticationException {
            throw new UnsupportedOperationException("No authentication should be done with this AuthenticationManager");
        }

    }

}
