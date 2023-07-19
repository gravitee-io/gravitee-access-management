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
package io.gravitee.am.management.handlers.management.api.spring.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.management.handlers.management.api.authentication.csrf.CookieCsrfSignedTokenRepository;
import io.gravitee.am.management.handlers.management.api.authentication.filter.*;
import io.gravitee.am.management.handlers.management.api.authentication.handler.CookieClearingLogoutHandler;
import io.gravitee.am.management.handlers.management.api.authentication.handler.CustomAuthenticationFailureHandler;
import io.gravitee.am.management.handlers.management.api.authentication.handler.CustomAuthenticationSuccessHandler;
import io.gravitee.am.management.handlers.management.api.authentication.handler.CustomLogoutSuccessHandler;
import io.gravitee.am.management.handlers.management.api.authentication.web.LoginUrlAuthenticationEntryPoint;
import io.gravitee.am.management.handlers.management.api.authentication.web.WebAuthenticationDetails;
import io.gravitee.am.management.handlers.management.api.authentication.web.XForwardedAwareRedirectStrategy;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.ReCaptchaService;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static java.util.Arrays.asList;

@Configuration
public class AuthSecurityConfiguration extends CsrfAwareConfiguration {

    private static final String AUTH_LOGOUT = "/auth/logout";
    private static final String[] PERMITTED_ROUTES = {"/auth/login", "/auth/assets/**", "/auth/cockpit"};
    private final ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    public AuthSecurityConfiguration(
            Environment environment,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        super(environment);
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Bean
    @Order(100)
    public SecurityFilterChain authSecurityFilterChain(
            HttpSecurity http,
            AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> authenticationDetailsSource,
            AuditService auditService,
            JWTParser jwtParser,
            OrganizationUserService userService,
            ReCaptchaService reCaptchaService,
            ObjectMapper objectMapper,
            CookieCsrfSignedTokenRepository csrfTokenRepository
    ) throws Exception {
        http.authorizeHttpRequests(
                        authorizeHttpRequests -> authorizeHttpRequests
                                .requestMatchers(PERMITTED_ROUTES)
                                .permitAll()
                                .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin.loginPage("/auth/login")
                        .authenticationDetailsSource(authenticationDetailsSource)
                        .successHandler(authenticationSuccessHandler())
                        .failureHandler(authenticationFailureHandler())
                        .permitAll()
                )
                .logout(logout -> logout.logoutRequestMatcher(new AntPathRequestMatcher(AUTH_LOGOUT))
                        .logoutSuccessHandler(new CustomLogoutSuccessHandler(auditService, environment, jwtParser, userService))
                        .addLogoutHandler(cookieClearingLogoutHandler()))
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(loginUrlAuthenticationEntryPoint()))
                .cors(cors -> {})
                .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(cockpitAuthenticationFilter(), AbstractPreAuthenticatedProcessingFilter.class)
                .addFilterBefore(new RecaptchaFilter(reCaptchaService, objectMapper), AbstractPreAuthenticatedProcessingFilter.class)
                .addFilterBefore(new CheckRedirectionCookieFilter(), AbstractPreAuthenticatedProcessingFilter.class)
                .addFilterBefore(checkLoginRedirectUriFilter(), AbstractPreAuthenticatedProcessingFilter.class)
                .addFilterBefore(checkLogoutRedirectUriFilter(), LogoutFilter.class)
                .addFilterBefore(builtInAuthFilter(), AbstractPreAuthenticatedProcessingFilter.class)
                .addFilterBefore(socialAuthFilter(), AbstractPreAuthenticatedProcessingFilter.class)
                .addFilterBefore(checkAuthCookieFilter(), AbstractPreAuthenticatedProcessingFilter.class);

        return applyCsrf(http, csrfTokenRepository).build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        CustomAuthenticationSuccessHandler successHandler = new CustomAuthenticationSuccessHandler();
        successHandler.setRedirectStrategy(redirectStrategy());
        return successHandler;
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        SimpleUrlAuthenticationFailureHandler authenticationFailureHandler = new CustomAuthenticationFailureHandler("/auth/login?error");
        authenticationFailureHandler.setRedirectStrategy(redirectStrategy());
        return authenticationFailureHandler;
    }

    @Bean
    public RedirectStrategy redirectStrategy() {
        return new XForwardedAwareRedirectStrategy();
    }

    @Bean
    public CockpitAuthenticationFilter cockpitAuthenticationFilter() {
        return new CockpitAuthenticationFilter();
    }

    @Bean
    public Filter checkAuthCookieFilter() {
        return new CheckAuthenticationCookieFilter();
    }

    @Bean
    public LogoutHandler cookieClearingLogoutHandler() {
        return new CookieClearingLogoutHandler();
    }

    @Bean
    public Filter checkLoginRedirectUriFilter() {
        CheckRedirectUriFilter checkRedirectUriFilter = new CheckRedirectUriFilter("/authorize");
        checkRedirectUriFilter.setParamName("redirect_uri");
        checkRedirectUriFilter.setAllowedUrls(getPropertiesAsList("http.login.allow-redirect-urls", "*"));
        return checkRedirectUriFilter;
    }

    @Bean
    public Filter checkLogoutRedirectUriFilter() {
        CheckRedirectUriFilter checkRedirectUriFilter = new CheckRedirectUriFilter("/logout");
        checkRedirectUriFilter.setParamName("target_url");
        checkRedirectUriFilter.setAllowedUrls(getPropertiesAsList("http.logout.allow-redirect-urls", "*"));
        return checkRedirectUriFilter;
    }

    @Bean
    public Filter builtInAuthFilter() {
        return new BuiltInAuthenticationFilter(new AntPathRequestMatcher("/auth/authorize"));
    }

    @Bean
    public LoginUrlAuthenticationEntryPoint loginUrlAuthenticationEntryPoint() {
        return new LoginUrlAuthenticationEntryPoint("/auth/login");
    }

    @Bean
    public Filter socialAuthFilter() {
        SocialAuthenticationFilter socialAuthenticationFilter = new SocialAuthenticationFilter("/auth/login/callback");
        socialAuthenticationFilter.setApplicationEventPublisher(applicationEventPublisher);
        return socialAuthenticationFilter;
    }

    private List<String> getPropertiesAsList(final String propertyKey, final String defaultValue) {
        final String property = environment.getProperty(propertyKey, defaultValue);
        return asList(property.replaceAll("\\s+", "").split(","));
    }
}