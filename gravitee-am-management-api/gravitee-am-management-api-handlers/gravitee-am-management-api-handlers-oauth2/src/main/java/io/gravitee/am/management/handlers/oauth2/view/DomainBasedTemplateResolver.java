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
package io.gravitee.am.management.handlers.oauth2.view;

import io.gravitee.am.model.Domain;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.StringTemplateResource;

import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainBasedTemplateResolver extends StringTemplateResolver {

    @Autowired
    private Domain domain;

    public DomainBasedTemplateResolver() {
        setTemplateMode("HTML");
    }

    @Override
    protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate, String template, Map<String, Object> templateResolutionAttributes) {
        return new StringTemplateResource(domain.getLoginForm().getContent());
    }
}
