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

import io.gravitee.am.gateway.handler.oauth2.view.ThymeleafConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
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

    /*
    @Autowired
    private Domain domain;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (domain.getAssets() != null && ! domain.getAssets().isEmpty()) {
            domain.getAssets().forEach(asset -> registry
                    .addResourceHandler(asset.getMapping() + "/**")
                    .addResourceLocations("file:" + asset.getPath()));
        }
    }
    */

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.defaultContentType(MediaType.APPLICATION_JSON);
    }
}