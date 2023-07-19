package io.gravitee.am.management.handlers.management.api.spring.security.filter;

import io.gravitee.am.management.handlers.management.api.authentication.web.WebAuthenticationDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class TokenSecurityConfiguration {

    private static final String REALM_NAME = "Gravitee.io AM Management API";

    @Bean
    @Order(101)
    public SecurityFilterChain tokenSecurityFilterChain(
            HttpSecurity http,
            AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> authenticationDetailsSource
    ) throws Exception {
        http.authorizeHttpRequests(
                        authorizeHttpRequests ->
                                authorizeHttpRequests.requestMatchers("/auth/token").permitAll()
                                .anyRequest().authenticated()
                )
                .httpBasic(httpBasic -> httpBasic
                        .realmName(REALM_NAME)
                        .authenticationDetailsSource(authenticationDetailsSource))
                .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}