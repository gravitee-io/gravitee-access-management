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
package io.gravitee.am.management.handlers.management.api.spring.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.handlers.management.api.authentication.filter.CheckAuthenticationCookieFilter;
import io.gravitee.am.management.handlers.management.api.authentication.filter.JWTAuthenticationFilter;
import io.gravitee.am.management.handlers.management.api.authentication.filter.RecaptchaFilter;
import io.gravitee.am.management.handlers.management.api.authentication.filter.SocialAuthenticationFilter;
import io.gravitee.am.management.handlers.management.api.authentication.handler.CookieClearingLogoutHandler;
import io.gravitee.am.management.handlers.management.api.authentication.handler.CustomAuthenticationFailureHandler;
import io.gravitee.am.management.handlers.management.api.authentication.handler.CustomAuthenticationSuccessHandler;
import io.gravitee.am.management.handlers.management.api.authentication.handler.CustomLogoutSuccessHandler;
import io.gravitee.am.management.handlers.management.api.authentication.manager.idp.IdentityProviderManager;
import io.gravitee.am.management.handlers.management.api.authentication.provider.jwt.JWTGenerator;
import io.gravitee.am.management.handlers.management.api.authentication.provider.security.ManagementAuthenticationProvider;
import io.gravitee.am.management.handlers.management.api.authentication.web.*;
import io.gravitee.am.management.handlers.management.api.authentication.web.LoginUrlAuthenticationEntryPoint;
import io.gravitee.am.management.handlers.management.api.authentication.web.WebAuthenticationDetails;
import io.gravitee.am.management.handlers.management.api.authentication.web.WebAuthenticationDetailsSource;
import io.gravitee.am.service.ReCaptchaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.*;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@EnableWebSecurity
@ComponentScan("io.gravitee.am.management.handlers.management.api.authentication")
public class SecurityConfiguration {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private Http401UnauthorizedEntryPoint http401UnauthorizedEntryPoint;

    @Autowired
    private ReCaptchaService reCaptchaService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(userAuthenticationProvider());
    }

    @Order(100)
    @Configuration
    protected class AuthSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {

            http.requestMatchers()
                .antMatchers("/auth/authorize", "/auth/login", "/auth/login/callback", "/auth/logout", "/auth/assets/**")
                .and()
            .authorizeRequests()
                .antMatchers("/auth/login", "/auth/assets/**").permitAll()
                .anyRequest().authenticated()
                .and()
            .formLogin()
                .loginPage("/auth/login")
                .authenticationDetailsSource(authenticationDetailsSource())
                .successHandler(authenticationSuccessHandler())
                .failureHandler(authenticationFailureHandler())
                .permitAll()
                .and()
            .logout()
                .logoutRequestMatcher(new AntPathRequestMatcher("/auth/logout"))
                .logoutSuccessHandler(new CustomLogoutSuccessHandler())
                .invalidateHttpSession(true)
                .addLogoutHandler(cookieClearingLogoutHandler())
                .and()
            .exceptionHandling()
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/auth/login"))
                .and()
            .cors()
                .configurationSource(corsConfigurationSource())
                .and()
            .addFilterBefore(new RecaptchaFilter(reCaptchaService, objectMapper), AbstractPreAuthenticatedProcessingFilter.class)
            .addFilterBefore(socialAuthFilter(), AbstractPreAuthenticatedProcessingFilter.class)
            .addFilterBefore(checkAuthCookieFilter(), AbstractPreAuthenticatedProcessingFilter.class);
        }
    }

    @Order(101)
    @Configuration
    protected class TokenSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {

            http
                .antMatcher("/auth/token")
                    .authorizeRequests()
                        .anyRequest().authenticated()
                    .and()
                        .httpBasic()
                            .realmName("Gravitee.io AM Management API")
                            .authenticationDetailsSource(authenticationDetailsSource())
                    .and()
                        .sessionManagement()
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                        .csrf().disable();
        }
    }

    @Order(102)
    @Configuration
    protected class ManagementSecurityConfiguration extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {

            http.requestMatchers()
                    .antMatchers("/organizations/**", "/user", "/platform/**")
                    .and()
                .sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                    .cors()
                .and()
                    .authorizeRequests()
                        .anyRequest().authenticated()
                .and()
                    .httpBasic()
                        .disable()
                    .csrf()
                        .disable()
                .exceptionHandling()
                    .authenticationEntryPoint(http401UnauthorizedEntryPoint)
                    .and()
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        }

        @Override
        public void configure(WebSecurity web) {
            web.ignoring().antMatchers("/swagger.json");
        }
    }

    @Bean
    public ManagementAuthenticationProvider userAuthenticationProvider() {
        ManagementAuthenticationProvider authenticationProvider = new ManagementAuthenticationProvider();
        authenticationProvider.setIdentityProviderManager(identityProviderManager);

        return authenticationProvider;
    }

    @Bean
    public JWTGenerator jwtCookieGenerator() {
        return new JWTGenerator();
    }

    @Bean
    public Filter socialAuthFilter() {
        SocialAuthenticationFilter socialAuthenticationFilter = new SocialAuthenticationFilter("/auth/login/callback");
        socialAuthenticationFilter.setApplicationEventPublisher(applicationEventPublisher);
        return socialAuthenticationFilter;
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
    public AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> authenticationDetailsSource() {
        return new WebAuthenticationDetailsSource();
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
    public Filter jwtAuthenticationFilter() {
        return new JWTAuthenticationFilter(new AntPathRequestMatcher("/**"));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(getPropertiesAsList("http.cors.allow-origin", "*"));
        config.setAllowedHeaders(getPropertiesAsList("http.cors.allow-headers", "Cache-Control, Pragma, Origin, Authorization, Content-Type, X-Requested-With, If-Match, x-xsrf-token"));
        config.setAllowedMethods(getPropertiesAsList("http.cors.allow-methods", "OPTIONS, GET, POST, PUT, PATCH, DELETE"));
        config.setMaxAge(environment.getProperty("http.cors.max-age", Long.class, 1728000L));

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private List<String> getPropertiesAsList(final String propertyKey, final String defaultValue) {
        String property = environment.getProperty(propertyKey);
        if (property == null) {
            property = defaultValue;
        }
        return asList(property.replaceAll("\\s+","").split(","));
    }
}
