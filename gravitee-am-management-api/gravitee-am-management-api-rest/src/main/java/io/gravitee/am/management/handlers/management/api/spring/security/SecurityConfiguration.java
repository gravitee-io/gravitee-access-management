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
import io.gravitee.am.management.handlers.management.api.authentication.csrf.CookieCsrfSignedTokenRepository;
import io.gravitee.am.management.handlers.management.api.authentication.csrf.CsrfRequestMatcher;
import io.gravitee.am.management.handlers.management.api.authentication.filter.*;
import io.gravitee.am.management.handlers.management.api.authentication.handler.CookieClearingLogoutHandler;
import io.gravitee.am.management.handlers.management.api.authentication.handler.CustomAuthenticationFailureHandler;
import io.gravitee.am.management.handlers.management.api.authentication.handler.CustomAuthenticationSuccessHandler;
import io.gravitee.am.management.handlers.management.api.authentication.handler.CustomLogoutSuccessHandler;
import io.gravitee.am.management.handlers.management.api.authentication.manager.idp.IdentityProviderManager;
import io.gravitee.am.management.handlers.management.api.authentication.provider.generator.JWTGenerator;
import io.gravitee.am.management.handlers.management.api.authentication.provider.generator.RedirectCookieGenerator;
import io.gravitee.am.management.handlers.management.api.authentication.provider.security.ManagementAuthenticationProvider;
import io.gravitee.am.management.handlers.management.api.authentication.web.*;
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
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static io.gravitee.am.management.handlers.management.api.authentication.csrf.CookieCsrfSignedTokenRepository.DEFAULT_CSRF_HEADER_NAME;
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
                .antMatchers("/auth/authorize", "/auth/login", "/auth/login/callback", "/auth/logout", "/auth/completeProfile", "/auth/assets/**")
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
                .addLogoutHandler(cookieClearingLogoutHandler())
                .and()
            .exceptionHandling()
                .authenticationEntryPoint(loginUrlAuthenticationEntryPoint())
                .and()
            .cors()
            .and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .addFilterBefore(new RecaptchaFilter(reCaptchaService, objectMapper), AbstractPreAuthenticatedProcessingFilter.class)
            .addFilterBefore(new CheckRedirectionCookieFilter(), AbstractPreAuthenticatedProcessingFilter.class)
            .addFilterBefore(builtInAuthFilter(), AbstractPreAuthenticatedProcessingFilter.class)
            .addFilterBefore(socialAuthFilter(), AbstractPreAuthenticatedProcessingFilter.class)
            .addFilterBefore(checkAuthCookieFilter(), AbstractPreAuthenticatedProcessingFilter.class);

            csrf(http);
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
                .exceptionHandling()
                    .authenticationEntryPoint(http401UnauthorizedEntryPoint)
                    .and()
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

            csrf(http);
        }

        @Override
        public void configure(WebSecurity web) {
            web.ignoring().antMatchers("/swagger.json");
        }
    }

    private HttpSecurity csrf(HttpSecurity security) throws Exception {

        if(environment.getProperty("http.csrf.enabled", Boolean.class, true)) {
            return security.csrf()
                    .csrfTokenRepository(cookieCsrfSignedTokenRepository())
                    .requireCsrfProtectionMatcher(new CsrfRequestMatcher(environment.getProperty("jwt.cookie-name", "Auth-Graviteeio-AM")))
                    .and()
                    .addFilterAfter(new CsrfIncludeFilter(), CsrfFilter.class);
        }else {
            return security.csrf().disable();
        }
    }

    @Bean
    public ManagementAuthenticationProvider userAuthenticationProvider() {
        ManagementAuthenticationProvider authenticationProvider = new ManagementAuthenticationProvider();
        authenticationProvider.setIdentityProviderManager(identityProviderManager);

        return authenticationProvider;
    }

    @Bean
    public LoginUrlAuthenticationEntryPoint loginUrlAuthenticationEntryPoint() {
        return new LoginUrlAuthenticationEntryPoint("/auth/login");
    }

    @Bean
    public JWTGenerator jwtCookieGenerator() {
        return new JWTGenerator();
    }

    @Bean
    public RedirectCookieGenerator redirectCookieGenerator() {
        return new RedirectCookieGenerator();
    }

    @Bean
    public Filter builtInAuthFilter() {
        return new BuiltInAuthenticationFilter(new AntPathRequestMatcher("/auth/authorize"));
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
        config.setAllowedHeaders(getPropertiesAsList("http.cors.allow-headers", "Cache-Control, Pragma, Origin, Authorization, Content-Type, X-Requested-With, If-Match, " + DEFAULT_CSRF_HEADER_NAME));
        config.setAllowedMethods(getPropertiesAsList("http.cors.allow-methods", "OPTIONS, GET, POST, PUT, PATCH, DELETE"));
        config.setExposedHeaders(getPropertiesAsList("http.cors.exposed-headers", "ETag, " + DEFAULT_CSRF_HEADER_NAME));
        config.setMaxAge(environment.getProperty("http.cors.max-age", Long.class, 1728000L));

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    @Bean
    public CookieCsrfSignedTokenRepository cookieCsrfSignedTokenRepository() {
        return new CookieCsrfSignedTokenRepository();
    }

    private List<String> getPropertiesAsList(final String propertyKey, final String defaultValue) {
        String property = environment.getProperty(propertyKey);
        if (property == null) {
            property = defaultValue;
        }
        return asList(property.replaceAll("\\s+","").split(","));
    }
}
