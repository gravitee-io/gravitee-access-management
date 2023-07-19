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

import io.gravitee.am.management.handlers.management.api.authentication.csrf.CookieCsrfSignedTokenRepository;
import io.gravitee.am.management.handlers.management.api.authentication.filter.*;
import io.gravitee.am.management.handlers.management.api.authentication.handler.*;
import io.gravitee.am.management.handlers.management.api.authentication.manager.idp.IdentityProviderManager;
import io.gravitee.am.management.handlers.management.api.authentication.provider.generator.JWTGenerator;
import io.gravitee.am.management.handlers.management.api.authentication.provider.generator.RedirectCookieGenerator;
import io.gravitee.am.management.handlers.management.api.authentication.provider.security.ManagementAuthenticationProvider;
import io.gravitee.am.management.handlers.management.api.authentication.web.*;
import io.gravitee.am.management.handlers.management.api.spring.security.filter.AuthSecurityConfiguration;
import io.gravitee.am.management.handlers.management.api.spring.security.filter.ManagementSecurityConfiguration;
import io.gravitee.am.management.handlers.management.api.spring.security.filter.TokenSecurityConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.gravitee.am.management.handlers.management.api.authentication.csrf.CookieCsrfSignedTokenRepository.DEFAULT_CSRF_HEADER_NAME;
import static java.util.Arrays.asList;
import static java.util.Objects.nonNull;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@EnableWebSecurity
@ComponentScan("io.gravitee.am.management.handlers.management.api.authentication")
@Import({
        AuthSecurityConfiguration.class,
        TokenSecurityConfiguration.class,
        ManagementSecurityConfiguration.class
})
public class SecurityConfiguration {

    public static final String HTTP_CSP_ENABLED = "http.csp.enabled";
    public static final String DEFAULT_DEFAULT_SRC_CSP_DIRECTIVE = "default-src self;";
    public static final String DEFAULT_FRAME_ANCESTOR_CSP_DIRECTIVE = "frame-ancestors 'none';";
    public static final String HTTP_CSP_DIRECTIVES = "http.csp.directives[%d]";

    @Autowired
    private Environment environment;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(userAuthenticationProvider());
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
    public RedirectCookieGenerator redirectCookieGenerator() {
        return new RedirectCookieGenerator();
    }

    @Bean
    public AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> authenticationDetailsSource() {
        return new WebAuthenticationDetailsSource();
    }

    @Bean
    public Filter jwtAuthenticationFilter() {
        return new JWTAuthenticationFilter(new AntPathRequestMatcher("/**"));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(getPropertiesAsList("http.cors.allow-origin", "*"));
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

    @Bean
    public RequestRejectedHandler requestRejectedHandler() {
        return new CustomRequestRejectedHandler();
    }

    private List<String> getPropertiesAsList(final String propertyKey, final String defaultValue) {
        String property = environment.getProperty(propertyKey, defaultValue);
        return asList(property.replaceAll("\\s+","").split(","));
    }
}
