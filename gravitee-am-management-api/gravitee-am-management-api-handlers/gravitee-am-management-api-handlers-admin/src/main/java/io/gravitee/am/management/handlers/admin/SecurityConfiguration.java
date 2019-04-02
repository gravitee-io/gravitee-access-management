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
package io.gravitee.am.management.handlers.admin;

import io.gravitee.am.management.handlers.admin.authentication.CustomAuthenticationFailureHandler;
import io.gravitee.am.management.handlers.admin.authentication.CustomSavedRequestAwareAuthenticationSuccessHandler;
import io.gravitee.am.management.handlers.admin.authentication.LoginUrlAuthenticationEntryPoint;
import io.gravitee.am.management.handlers.admin.filter.CheckAuthenticationCookieFilter;
import io.gravitee.am.management.handlers.admin.filter.OAuth2ClientAuthenticationFilter;
import io.gravitee.am.management.handlers.admin.handler.CookieClearingLogoutHandler;
import io.gravitee.am.management.handlers.admin.handler.CustomLogoutSuccessHandler;
import io.gravitee.am.management.handlers.admin.provider.jwt.JWTGenerator;
import io.gravitee.am.management.handlers.admin.provider.security.DomainBasedAuthenticationProvider;
import io.gravitee.am.management.handlers.admin.security.IdentityProviderManager;
import io.gravitee.am.management.handlers.admin.security.impl.IdentityProviderManagerImpl;
import io.gravitee.am.management.handlers.admin.security.listener.AuthenticationSuccessListener;
import io.gravitee.am.management.handlers.admin.security.web.XForwardedAwareRedirectStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.*;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;

@Configuration
@ComponentScan("io.gravitee.am.management.handlers.admin.controller")
@EnableWebSecurity
public class SecurityConfiguration {

    private final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    protected void configureGlobal(AuthenticationManagerBuilder auth)  {
        logger.info("Loading identity providers to handle user authentication");

        // By default we are associating users added to the domain
        auth.authenticationProvider(userAuthenticationProvider());
        auth.authenticationEventPublisher(new DefaultAuthenticationEventPublisher(applicationEventPublisher));
    }

    @Bean
    public DomainBasedAuthenticationProvider userAuthenticationProvider() {
        DomainBasedAuthenticationProvider domainBasedAuthenticationProvider = new DomainBasedAuthenticationProvider();
        domainBasedAuthenticationProvider.setIdentityProviderManager(identityProviderManager());

        return domainBasedAuthenticationProvider;
    }

    @Bean
    public IdentityProviderManager identityProviderManager() {
        return new IdentityProviderManagerImpl();
    }

    @Bean
    public AuthenticationSuccessListener authenticationSuccessListener() {
        return new AuthenticationSuccessListener();
    }

    @Bean
    public JWTGenerator jwtCookieGenerator() {
        return new JWTGenerator();
    }

    @Configuration
    @Order(1)
    public static class LoginFormSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

        @Autowired
        private ApplicationEventPublisher applicationEventPublisher;

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http
                .requestMatchers()
                    .antMatchers("/authorize", "/login", "/login/callback", "/logout")
                    .and()
                .authorizeRequests()
                    .antMatchers("/login").permitAll()
                    .anyRequest().authenticated()
                    .and()
                .formLogin()
                    .loginPage("/login")
                    .authenticationDetailsSource(authenticationDetailsSource())
                    .successHandler(authenticationSuccessHandler())
                    .failureHandler(authenticationFailureHandler())
                    .permitAll()
                    .and()
                .logout()
                    .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                    .logoutSuccessHandler(new CustomLogoutSuccessHandler())
                    .invalidateHttpSession(true)
                    .addLogoutHandler(cookieClearingLogoutHandler())
                    .and()
                .exceptionHandling()
                    .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                    .and()
                .addFilterBefore(clientOAuth2Filter(), AbstractPreAuthenticatedProcessingFilter.class)
                .addFilterBefore(checkAuthCookieFilter(), AbstractPreAuthenticatedProcessingFilter.class);
        }

        @Bean
        public Filter clientOAuth2Filter() {
            OAuth2ClientAuthenticationFilter oAuth2ClientAuthenticationFilter = new OAuth2ClientAuthenticationFilter("/login/callback");
            oAuth2ClientAuthenticationFilter.setApplicationEventPublisher(applicationEventPublisher);
            return oAuth2ClientAuthenticationFilter;
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
        public AuthenticationDetailsSource<HttpServletRequest, io.gravitee.am.management.handlers.admin.authentication.WebAuthenticationDetails> authenticationDetailsSource() {
            return new io.gravitee.am.management.handlers.admin.authentication.WebAuthenticationDetailsSource();
        }

        @Bean
        public AuthenticationSuccessHandler authenticationSuccessHandler() {
            CustomSavedRequestAwareAuthenticationSuccessHandler successHandler = new CustomSavedRequestAwareAuthenticationSuccessHandler();
            successHandler.setRedirectStrategy(redirectStrategy());
            return successHandler;
        }

        @Bean
        public AuthenticationFailureHandler authenticationFailureHandler() {
            SimpleUrlAuthenticationFailureHandler authenticationFailureHandler = new CustomAuthenticationFailureHandler("/login?error");
            authenticationFailureHandler.setRedirectStrategy(redirectStrategy());
            return authenticationFailureHandler;
        }

        @Bean
        public RedirectStrategy redirectStrategy() {
            return new XForwardedAwareRedirectStrategy();
        }
    }

    @Configuration

    public static class BasicSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http
                .antMatcher("/token")
                    .authorizeRequests()
                        .anyRequest().authenticated()
                    .and()
                        .httpBasic()
                            .realmName("Gravitee.io AM Management API")
                    .and()
                        .sessionManagement()
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                        .csrf().disable();
        }
    }
}
