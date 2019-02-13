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

import io.gravitee.am.gateway.handler.form.FormManager;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.StringTemplateResource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainBasedTemplateResolver extends AbstractConfigurableTemplateResolver {

    private ConcurrentMap<String, StringTemplateResource> templates = new ConcurrentHashMap<>();

    private TemplateEngine templateEngine;

    @Override
    protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate, String template, String resourceName, String characterEncoding, Map<String, Object> templateResolutionAttributes) {
        boolean templateFound = templates.containsKey(resourceName);
        String[] templateParts = resourceName.split(Pattern.quote(FormManager.TEMPLATE_NAME_SEPARATOR));

        // template not found for the client, try at domain level
        if (!templateFound && templateParts.length == 2) {
            resourceName = templateParts[0];
            templateFound = templates.containsKey(resourceName);
        }

        if (templateFound) {
            return templates.get(resourceName);
        }

        return null;
    }

    public void addForm(String templateName, String templateContent) {
        templates.put(templateName, new StringTemplateResource(templateContent));
        templateEngine.getCacheManager().clearAllCaches();
    }

    public void removeForm(String templateName) {
        templates.remove(templateName);
        templateEngine.getCacheManager().clearAllCaches();
    }

    public void setTemplateEngine(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }
}
