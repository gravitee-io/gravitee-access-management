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
package io.gravitee.am.gateway.handler.vertx.view;

import io.gravitee.am.model.Domain;
import io.gravitee.common.spring.factory.AbstractAutowiringFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ThymeleafTemplateResolverFactory extends AbstractAutowiringFactoryBean<ITemplateResolver> {

    private final Logger logger = LoggerFactory.getLogger(ThymeleafTemplateResolverFactory.class);

    @Autowired
    private Domain domain;

    @Override
    protected ITemplateResolver doCreateInstance() throws Exception {
        if (domain.getLoginForm() == null || domain.getLoginForm().getContent() == null || !domain.getLoginForm().isEnabled()) {
            logger.debug("View templating has not been overridden with custom view, returning default views.");
            return defaultTemplateResolver();
        }

        ITemplateResolver resolver = overrideTemplateResolver();

        return (resolver != null) ? resolver : defaultTemplateResolver();
    }

    private ITemplateResolver overrideTemplateResolver() {
        return new DomainBasedTemplateResolver();

    }
    private ITemplateResolver defaultTemplateResolver() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("/webroot/views/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode("HTML");
        return templateResolver;
    }

    @Override
    public Class<?> getObjectType() {
        return ITemplateResolver.class;
    }
}