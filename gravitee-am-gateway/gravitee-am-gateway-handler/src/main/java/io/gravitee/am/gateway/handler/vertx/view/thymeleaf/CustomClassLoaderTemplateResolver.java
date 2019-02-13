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
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomClassLoaderTemplateResolver extends ClassLoaderTemplateResolver {

    @Override
    protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate, String template, String resourceName, String characterEncoding, Map<String, Object> templateResolutionAttributes) {
        if (resourceName.contains(FormManager.TEMPLATE_NAME_SEPARATOR)) {
            resourceName = resourceName.split(Pattern.quote(FormManager.TEMPLATE_NAME_SEPARATOR))[0] + ".html";
        }
        return super.computeTemplateResource(configuration, ownerTemplate, template, resourceName, characterEncoding, templateResolutionAttributes);
    }
}
