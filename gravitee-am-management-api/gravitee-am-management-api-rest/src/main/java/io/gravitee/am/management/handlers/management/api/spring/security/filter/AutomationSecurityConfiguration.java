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

import io.gravitee.am.management.handlers.management.api.authentication.filter.BearerAuthenticationFilter;
import io.gravitee.am.management.handlers.management.api.authentication.web.Http401UnauthorizedEntryPoint;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Security configuration for the Automation API.
 * <p>
 * Active only when {@code http.api.automation.enabled=true}. Scoped to
 * {@code /automation/**} paths with stateless Bearer token (JWT) authentication
 * and CSRF disabled.
 * <p>
 * Creates a dedicated {@link BearerAuthenticationFilter} instance (not a Spring
 * bean) to avoid type-ambiguity with the management chain's injection and to
 * prevent shared-bean mutation between security chains.
 *
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@Configuration
@Conditional(AutomationSecurityConfiguration.AutomationEnabledCondition.class)
public class AutomationSecurityConfiguration {

    @Bean
    @Order(99)
    public SecurityFilterChain automationSecurityFilterChain(
            HttpSecurity http,
            Http401UnauthorizedEntryPoint entryPoint,
            AutowireCapableBeanFactory beanFactory
    ) throws Exception {
        BearerAuthenticationFilter bearerFilter =
                new BearerAuthenticationFilter(new AntPathRequestMatcher("/automation/**"));
        try {
            beanFactory.autowireBean(bearerFilter);
            beanFactory.initializeBean(bearerFilter, "automationBearerAuthFilter");
        } catch (BeansException e) {
            throw new BeanInitializationException(
                    "Failed to initialize BearerAuthenticationFilter for Automation API security chain", e);
        }

        http
                .securityMatchers(matcher -> matcher.requestMatchers(
                        AntPathRequestMatcher.antMatcher("/automation/**")))
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
                .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    static class AutomationEnabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return "true".equalsIgnoreCase(
                    context.getEnvironment().getProperty("http.api.automation.enabled", "false"));
        }
    }
}
