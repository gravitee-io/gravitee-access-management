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
package io.gravitee.am.gateway.handler.vertx.view.thymeleaf;

import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class ThymeleafConfiguration {

    @Autowired
    private Vertx vertx;

    @Value("${templates.path:#{systemProperties['gravitee.home']}/templates}")
    private String templatesDirectory;

    @Bean
    public ThymeleafTemplateEngine getTemplateEngine(GraviteeMessageResolver messageResolver,
                                                     DomainBasedThemeResolver themeResolver) {
        GraviteeThymeleafTemplateEngine thymeleafTemplateEngine = new GraviteeThymeleafTemplateEngine(vertx);
        thymeleafTemplateEngine.setThemeResolver(themeResolver);
        TemplateEngine templateEngine = thymeleafTemplateEngine.unwrap();

        // set template resolvers
        DomainBasedTemplateResolver overrideTemplateResolver = (DomainBasedTemplateResolver) overrideTemplateResolver();
        overrideTemplateResolver.setTemplateEngine(templateEngine);
        templateEngine.setTemplateResolver(overrideTemplateResolver);
        templateEngine.addTemplateResolver(defaultTemplateResolver());
        templateEngine.addMessageResolver(messageResolver);
        return thymeleafTemplateEngine;
    }

    @Bean
    public GraviteeMessageResolver messageResolver() {
        return new GraviteeMessageResolver(templatesDirectory.endsWith("/") ? templatesDirectory + "i18n/" : templatesDirectory + "/i18n/");
    }

    @Bean
    public ITemplateResolver overrideTemplateResolver() {
        return new DomainBasedTemplateResolver();

    }

    @Bean
    public DomainBasedThemeResolver themeResolver() {
        return new DomainBasedThemeResolver();
    }

    private ITemplateResolver defaultTemplateResolver() {
        ClassLoaderTemplateResolver templateResolver = new CustomClassLoaderTemplateResolver();
        templateResolver.setPrefix("/webroot/views/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode("HTML");
        return templateResolver;
    }

}
