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
package io.gravitee.am.management.handlers.automation.spring.security;

import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.management.handlers.management.api.authentication.web.Http401UnauthorizedEntryPoint;
import io.gravitee.am.management.service.OrganizationUserService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the Automation API.
 * <p>
 * Lives in its own Spring child context (separate from the management API context),
 * following the APIM pattern of isolated security per API surface.
 * <p>
 * Uses a lightweight {@link AutomationBearerTokenFilter} ({@code OncePerRequestFilter})
 * for stateless JWT Bearer authentication with CSRF disabled.
 *
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@Configuration
public class AutomationSecurityConfiguration {

    @Bean
    public AutomationBearerTokenFilter automationBearerTokenFilter(
            @Qualifier("managementJwtParser") JWTParser jwtParser,
            OrganizationUserService userService
    ) {
        return new AutomationBearerTokenFilter(jwtParser, userService);
    }

    @Bean
    public SecurityFilterChain automationSecurityFilterChain(
            HttpSecurity http,
            Http401UnauthorizedEntryPoint entryPoint,
            AutomationBearerTokenFilter automationBearerTokenFilter
    ) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/openapi.json", "/openapi.yaml").permitAll()
                        .requestMatchers("OPTIONS", "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> {})
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception.authenticationEntryPoint(entryPoint))
                .addFilterBefore(automationBearerTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
