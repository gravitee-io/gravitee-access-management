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
package io.gravitee.am.management.handlers.oauth2;

import io.gravitee.am.management.handlers.oauth2.filter.CORSFilter;
import io.gravitee.am.management.handlers.oauth2.provider.endpoint.UserInfoEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import javax.servlet.Filter;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@EnableResourceServer
public class OpenIDResourceServerConfiguration extends ResourceServerConfigurerAdapter {

    @Bean
    public UserInfoEndpoint userInfoEndpoint() {
        return new UserInfoEndpoint();
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http
                .requestMatchers()
                    .antMatchers("/userinfo")
                .and()
                .authorizeRequests()
                    .antMatchers(HttpMethod.OPTIONS, "**").permitAll()
                    .antMatchers(HttpMethod.GET, "/userinfo").access("#oauth2.hasScope('openid')")
                    .and()
                .addFilterAfter(corsFilter(), AbstractPreAuthenticatedProcessingFilter.class);
    }

    @Override
    public void configure(ResourceServerSecurityConfigurer resources) throws Exception {
        resources.resourceId("openid");
    }

    @Bean
    public Filter corsFilter() {
        return new CORSFilter();
    }
}
