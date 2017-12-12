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
package io.gravitee.am.gateway.handler.oauth2;

import io.gravitee.am.gateway.handler.oauth2.provider.endpoint.ScopeApprovalEndpoint;
import io.gravitee.am.gateway.handler.oauth2.service.DomainScopeService;
import io.gravitee.am.gateway.handler.oauth2.view.ThymeleafConfiguration;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.approval.ApprovalStore;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@ComponentScan("io.gravitee.am.gateway.handler.oauth2.controller")
@EnableWebMvc
@Import(ThymeleafConfiguration.class)
public class WebMvcConfiguration extends WebMvcConfigurerAdapter {

    @Autowired
    private Domain domain;

    @Bean
    public ScopeApprovalEndpoint accessConfirmationController(ClientDetailsService clientDetailsService,
                                                              ApprovalStore approvalStore,
                                                              DomainScopeService scopeService) {
        ScopeApprovalEndpoint accessConfirmationController = new ScopeApprovalEndpoint();
        accessConfirmationController.setClientDetailsService(clientDetailsService);
        accessConfirmationController.setApprovalStore(approvalStore);
        accessConfirmationController.setScopeService(scopeService);
        return accessConfirmationController;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        LoginForm loginForm = domain.getLoginForm();
        if (loginForm != null && loginForm.isEnabled() && loginForm.getAssets() != null) {
            registry
                    .addResourceHandler("/assets/**")
                    .addResourceLocations("file:" + loginForm.getAssets());
        } else {
            registry
                    .addResourceHandler("/assets/**")
                    .addResourceLocations("classpath:/assets/");
        }
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.defaultContentType(MediaType.APPLICATION_JSON);
    }
}