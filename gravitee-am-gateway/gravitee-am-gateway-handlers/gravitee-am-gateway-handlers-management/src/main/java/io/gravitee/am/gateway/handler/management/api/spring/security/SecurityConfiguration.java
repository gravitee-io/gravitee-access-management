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
package io.gravitee.am.gateway.handler.management.api.spring.security;

import io.gravitee.am.gateway.handler.management.api.filter.CORSFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.AccessTokenConverter;
import org.springframework.security.oauth2.provider.token.RemoteTokenServices;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import javax.servlet.Filter;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@EnableWebSecurity
@EnableResourceServer
public class SecurityConfiguration extends ResourceServerConfigurerAdapter {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void configure(ResourceServerSecurityConfigurer resources) throws Exception {
        resources
                .tokenServices(remoteTokenServices())
                .resourceId(null)
                .eventPublisher(new DefaultAuthenticationEventPublisher(applicationEventPublisher));
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
                .authorizeRequests()
                    .antMatchers(HttpMethod.OPTIONS, "**").permitAll()
                    .anyRequest().authenticated()
            .and()
                .httpBasic()
                    .disable()
                .csrf()
                .disable()
            .addFilterAfter(corsFilter(), AbstractPreAuthenticatedProcessingFilter.class);
    }

    @Bean
    public Filter corsFilter() {
        return new CORSFilter();
    }

    @Bean
    public AccessTokenConverter accessTokenConverter() {
        // TODO : use our custom jwt access token converter
        return new JwtAccessTokenConverter();
    }

    @Bean
    public RemoteTokenServices remoteTokenServices() {
        RemoteTokenServices s = new RemoteTokenServices();
        s.setAccessTokenConverter(accessTokenConverter());
        // TODO : check baseURL
        s.setCheckTokenEndpointUrl("http://localhost:8092/" + environment.getProperty("security.oauth.domain.id") + "/oauth/check_token");
        s.setClientId(environment.getProperty("security.oauth.client.id"));
        s.setClientSecret(environment.getProperty("security.oauth.client.secret"));
        return s;
    }
}
