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
package io.gravitee.am.management.handlers.admin.view;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Form;
import io.gravitee.am.model.Template;
import io.gravitee.am.service.FormService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.StringTemplateResource;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainBasedTemplateResolver extends AbstractConfigurableTemplateResolver implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(DomainBasedTemplateResolver.class);

    @Autowired
    private FormService formService;

    @Autowired
    private Domain domain;

    private Map<String, String> templates = new HashMap<>();

    @Override
    protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate, String template, String resourceName, String characterEncoding, Map<String, Object> templateResolutionAttributes) {
        if (templates.containsKey(resourceName)) {
            return new StringTemplateResource(templates.get(resourceName));
        }
        return null;
    }

    @Override
    public void afterPropertiesSet() {
        formService.findByDomainAndTemplate(domain.getId(), Template.LOGIN.template())
                .filter(Form::isEnabled)
                .subscribe(
                        page -> {
                            logger.info("Login form has been overridden for domain {}.", domain.getName());
                            templates.put(page.getTemplate(), page.getContent());
                        },
                        error -> logger.error("Unable to initialize login page for domain {}", domain.getName(), error),
                        () -> logger.info("View templating has not been overridden with custom view, returning default views.")
                );
    }
}
