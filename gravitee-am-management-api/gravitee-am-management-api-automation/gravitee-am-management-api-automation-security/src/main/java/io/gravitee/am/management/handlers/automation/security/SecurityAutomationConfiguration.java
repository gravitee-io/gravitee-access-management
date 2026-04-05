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
package io.gravitee.am.management.handlers.automation.security;

import io.gravitee.am.management.handlers.management.api.authentication.filter.BearerAuthenticationFilter;
import io.gravitee.am.management.handlers.management.api.authentication.web.Http401UnauthorizedEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Security configuration for the Automation API.
 * <p>
 * Scoped to {@code /automation/**} paths with Bearer token (JWT) authentication.
 * Creates its own {@link BearerAuthenticationFilter} instance to avoid sharing
 * a mutable filter bean with the management security chain.
 *
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@Configuration
public class SecurityAutomationConfiguration {

    @Bean
    @Order(99)
    public SecurityFilterChain automationSecurityFilterChain(
            HttpSecurity http,
            Http401UnauthorizedEntryPoint entryPoint
    ) throws Exception {
        http
                .securityMatchers(matcher -> matcher.requestMatchers(AntPathRequestMatcher.antMatcher("/automation/**")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                new AntPathRequestMatcher("/automation/openapi.json", "GET"),
                                new AntPathRequestMatcher("/automation/openapi.yaml", "GET"),
                                new AntPathRequestMatcher("/automation/**", "OPTIONS")
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> {})
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception.authenticationEntryPoint(entryPoint))
                .addFilterBefore(new BearerAuthenticationFilter(new AntPathRequestMatcher("/automation/**")), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
