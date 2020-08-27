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
package io.gravitee.am.management.handlers.management.api.authentication.view;

import io.gravitee.am.model.Form;
import io.gravitee.am.model.Organization;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.StringTemplateResource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TemplateResolver extends AbstractConfigurableTemplateResolver {

    private ConcurrentMap<String, StringTemplateResource> templates = new ConcurrentHashMap<>();
    private ITemplateResolver defaultTemplateResolver = defaultTemplateResolver();
    private TemplateEngine templateEngine;

    @Override
    protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate, String template, String resourceName, String characterEncoding, Map<String, Object> templateResolutionAttributes) {

        if (templates.containsKey(template)) {
            return templates.get(template);
        }

        String fallbackTemplate = template.replaceFirst(".*#", "");
        return defaultTemplateResolver.resolveTemplate(configuration, ownerTemplate, fallbackTemplate, templateResolutionAttributes).getTemplateResource();
    }

    public void addForm(Form form) {
        templates.put(getTemplateKey(form), new StringTemplateResource(form.getContent()));
        templateEngine.getCacheManager().clearAllCaches();
    }

    public void removeForm(Form form) {
        templates.remove(getTemplateKey(form));
        templateEngine.getCacheManager().clearAllCaches();
    }

    public void setTemplateEngine(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    private String getTemplateKey(Form form) {
        return getTemplateKey(form.getReferenceId(), form.getTemplate());
    }

    private String getTemplateKey(String organizationId, String template) {

        return organizationId + "#" + template;
    }

    private ITemplateResolver defaultTemplateResolver() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("/views/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode("HTML");
        return templateResolver;
    }
}
